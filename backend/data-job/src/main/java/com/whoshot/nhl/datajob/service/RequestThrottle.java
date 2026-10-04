package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.ApiRequestProperties;
import com.whoshot.nhl.datajob.exception.NonRetryableApiClientException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Process-wide admission control for outbound NHL API requests. Every attempt, retries included,
 * must hold a {@link Permit} while it runs:
 * <ul>
 *   <li>an in-flight cap bounds concurrent requests across initial load, live sync and backfill;</li>
 *   <li>a request-start rate budget (GCRA, i.e. a token bucket without a refill thread) paces
 *       starts across all hosts together, so two hosts never multiply the configured rate;</li>
 *   <li>a per-host cooldown, set from {@code 429}/{@code 503} responses, holds back every worker
 *       targeting that host, not only the one that was throttled;</li>
 *   <li>a per-host circuit opens after consecutive transient failures, blocks the host for a while,
 *       then lets a single probe through and closes again only if the probe succeeds.</li>
 * </ul>
 * Every wait is interruptible and bounded by the caller's deadline. An interrupt raises
 * {@link CancellationException} with the interrupt flag kept; a wait that would pass the deadline
 * fails visibly instead of starting the request late.
 */
@Slf4j
@Component
public class RequestThrottle {

    /** How often a waiter re-checks a half-open circuit whose probe is still running. */
    private static final long PROBE_POLL_NANOS = TimeUnit.MILLISECONDS.toNanos(50);

    private final ApiRequestProperties properties;
    private final Semaphore inFlight;
    private final long intervalNanos;
    private final long burstToleranceNanos;
    private final Map<String, HostState> hosts = new ConcurrentHashMap<>();
    private final AtomicLong waitedNanos = new AtomicLong();
    private final AtomicLong peakInFlight = new AtomicLong();
    /** GCRA theoretical arrival time of the next request start, in {@link System#nanoTime()} units. */
    private long theoreticalArrival = System.nanoTime();

    public RequestThrottle(ApiRequestProperties properties) {
        this.properties = properties;
        this.inFlight = new Semaphore(properties.maxInFlight(), true);
        this.intervalNanos = (long) (TimeUnit.SECONDS.toNanos(1) / properties.requestsPerSecond());
        this.burstToleranceNanos = intervalNanos * (properties.burst() - 1);
    }

    /** A held slot for one request attempt; release it exactly once through {@link #release}. */
    public static final class Permit {
        private final String host;
        private final boolean probe;
        private final long waitedNanos;
        private boolean released;

        private Permit(String host, boolean probe, long waitedNanos) {
            this.host = host;
            this.probe = probe;
            this.waitedNanos = waitedNanos;
        }

        /** Time spent waiting for cooldowns, the in-flight cap and the rate budget. */
        public Duration waited() {
            return Duration.ofNanos(waitedNanos);
        }
    }

    /**
     * Waits until a request to {@code host} may start.
     *
     * @param host          target host, keying cooldowns and circuits
     * @param deadlineNanos {@link System#nanoTime()} after which the caller gives up
     * @return a permit to release when the attempt completes
     * @throws CancellationException          if the thread is interrupted while waiting
     * @throws NonRetryableApiClientException if admission would pass the deadline
     */
    public Permit acquire(String host, long deadlineNanos) {
        long started = System.nanoTime();
        HostState state = hosts.computeIfAbsent(host, h -> new HostState());
        boolean probe;
        while (true) {
            probe = awaitHost(host, state, deadlineNanos);
            try {
                acquireInFlight(host, deadlineNanos);
            } catch (RuntimeException e) {
                if (probe) {
                    state.endProbe();
                }
                throw e;
            }
            try {
                awaitRate(host, deadlineNanos);
            } catch (RuntimeException e) {
                inFlight.release();
                if (probe) {
                    state.endProbe();
                }
                throw e;
            }
            // The slot and rate waits can be long: a cooldown or open circuit imposed meanwhile
            // (another worker's 429/503) must hold this request back too, so check again right
            // before dispatch and go back to waiting if the host was blocked in between.
            if (state.stillAdmits(probe, properties)) {
                break;
            }
            inFlight.release();
            if (probe) {
                state.endProbe();
            }
        }
        peakInFlight.accumulateAndGet(properties.maxInFlight() - inFlight.availablePermits(), Math::max);
        long waited = System.nanoTime() - started;
        waitedNanos.addAndGet(waited);
        return new Permit(host, probe, waited);
    }

    /**
     * Ends an attempt and updates the host's circuit.
     *
     * @param permit      the permit from {@link #acquire}
     * @param hostHealthy false for a transient failure (transport error, 408, 429, 5xx); true when
     *                    the host answered, even with a permanent error such as 404
     */
    public void release(Permit permit, boolean hostHealthy) {
        if (permit.released) {
            return;
        }
        permit.released = true;
        inFlight.release();
        HostState state = hosts.get(permit.host);
        if (hostHealthy) {
            state.recordSuccess();
        } else if (state.recordFailure(permit.probe, properties)) {
            log.warn("Circuit open for {} after {} consecutive transient failures; next probe in {}",
                    permit.host, properties.circuitFailureThreshold(), properties.circuitOpenDuration());
        }
    }

    /**
     * Ends an attempt that produced no verdict on the host (cancelled or failed locally): frees its
     * slot without touching the circuit, so an interrupted probe neither closes nor re-opens it.
     */
    public void abandon(Permit permit) {
        if (permit.released) {
            return;
        }
        permit.released = true;
        inFlight.release();
        if (permit.probe) {
            hosts.get(permit.host).endProbe();
        }
    }

    /**
     * Holds back every request to {@code host} for at least {@code delay}, as directed by a
     * {@code Retry-After} or a throttling response.
     */
    public void cooldown(String host, Duration delay) {
        hosts.computeIfAbsent(host, h -> new HostState()).blockFor(delay.toNanos());
    }

    /** Total time callers have spent waiting for admission since startup. */
    public Duration totalWait() {
        return Duration.ofNanos(waitedNanos.get());
    }

    /** Requests currently holding a permit. */
    public int inFlight() {
        return properties.maxInFlight() - inFlight.availablePermits();
    }

    /** Highest number of requests observed in flight at once since startup. */
    public int peakInFlight() {
        return (int) peakInFlight.get();
    }

    /** @return whether this caller is the half-open circuit's probe */
    private boolean awaitHost(String host, HostState state, long deadlineNanos) {
        while (true) {
            long now = System.nanoTime();
            long wait;
            synchronized (state) {
                if (now - state.blockedUntil < 0) {
                    wait = state.blockedUntil - now;
                } else if (state.consecutiveFailures < properties.circuitFailureThreshold()) {
                    return false;
                } else if (!state.probing) {
                    state.probing = true;
                    log.info("Circuit for {} half-open; sending one probe request", host);
                    return true;
                } else {
                    wait = PROBE_POLL_NANOS;
                }
            }
            if (now + wait - deadlineNanos > 0) {
                throw new NonRetryableApiClientException("Deferred: " + host
                        + " is cooling down beyond this request's deadline");
            }
            sleepNanos(wait);
        }
    }

    private void acquireInFlight(String host, long deadlineNanos) {
        try {
            if (!inFlight.tryAcquire(Math.max(0, deadlineNanos - System.nanoTime()), TimeUnit.NANOSECONDS)) {
                throw new NonRetryableApiClientException("Deferred: no request slot for " + host
                        + " before this request's deadline");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while waiting for a request slot");
        }
    }

    private void awaitRate(String host, long deadlineNanos) {
        long startAt;
        synchronized (this) {
            long now = System.nanoTime();
            startAt = Math.max(now, theoreticalArrival - burstToleranceNanos);
            if (startAt - deadlineNanos > 0) {
                throw new NonRetryableApiClientException("Deferred: request budget for " + host
                        + " is exhausted beyond this request's deadline");
            }
            theoreticalArrival = Math.max(startAt, theoreticalArrival) + intervalNanos;
        }
        sleepNanos(startAt - System.nanoTime());
    }

    private static void sleepNanos(long nanos) {
        if (nanos <= 0) {
            return;
        }
        try {
            TimeUnit.NANOSECONDS.sleep(nanos);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while waiting for the request budget");
        }
    }

    private static final class HostState {
        private long blockedUntil = System.nanoTime();
        private int consecutiveFailures;
        private boolean probing;

        synchronized void blockFor(long nanos) {
            long until = System.nanoTime() + nanos;
            if (until - blockedUntil > 0) {
                blockedUntil = until;
            }
        }

        synchronized void recordSuccess() {
            consecutiveFailures = 0;
            probing = false;
        }

        /** @return whether this failure opened (or re-opened) the circuit */
        synchronized boolean recordFailure(boolean probe, ApiRequestProperties properties) {
            consecutiveFailures++;
            if (probe) {
                probing = false;
            }
            if (probe || consecutiveFailures == properties.circuitFailureThreshold()) {
                blockFor(properties.circuitOpenDuration().toNanos());
                return true;
            }
            return false;
        }

        /**
         * Whether a request that already passed {@link #awaitHost} may still start: the host must
         * not have been cooled down since, and (unless this request is the probe) its circuit must
         * not have opened.
         */
        synchronized boolean stillAdmits(boolean probe, ApiRequestProperties properties) {
            if (System.nanoTime() - blockedUntil < 0) {
                return false;
            }
            return probe || consecutiveFailures < properties.circuitFailureThreshold();
        }

        synchronized void endProbe() {
            probing = false;
        }
    }
}
