package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.ApiRequestProperties;
import com.whoshot.nhl.datajob.exception.NonRetryableApiClientException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Admission control shared by every outbound request (issue #29): in-flight cap, start-rate budget,
 * host cooldowns, circuit breaking, deadlines and cancellation.
 */
class RequestThrottleTest {

    private static final String HOST = "api-web.nhle.com";
    private static final String OTHER_HOST = "api.nhle.com";

    private static RequestThrottle throttle(int maxInFlight, double rps, int burst, int circuitThreshold,
                                            Duration circuitOpen) {
        return new RequestThrottle(new ApiRequestProperties(maxInFlight, rps, burst, 3, Duration.ofMillis(10),
                Duration.ofMillis(100), Duration.ofSeconds(10), circuitThreshold, circuitOpen));
    }

    private static RequestThrottle unpaced(int maxInFlight) {
        return throttle(maxInFlight, 10_000, 10_000, 100, Duration.ofSeconds(30));
    }

    private static long deadlineIn(Duration duration) {
        return System.nanoTime() + duration.toNanos();
    }

    private static Duration timed(Runnable action) {
        long started = System.nanoTime();
        action.run();
        return Duration.ofNanos(System.nanoTime() - started);
    }

    @Test
    void requestStarts_arePacedAfterTheBurst() {
        RequestThrottle throttle = throttle(10, 20, 2, 100, Duration.ofSeconds(30));

        Duration elapsed = timed(() -> {
            for (int i = 0; i < 6; i++) {
                throttle.release(throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(5))), true);
            }
        });

        // Two burst starts, then four more 50 ms apart.
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(180));
    }

    @Test
    void rateBudget_isSharedAcrossHosts() {
        RequestThrottle throttle = throttle(10, 20, 1, 100, Duration.ofSeconds(30));

        Duration elapsed = timed(() -> {
            for (int i = 0; i < 4; i++) {
                throttle.release(throttle.acquire(i % 2 == 0 ? HOST : OTHER_HOST,
                        deadlineIn(Duration.ofSeconds(5))), true);
            }
        });

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(140));
    }

    @Test
    void inFlightCap_blocksUntilReleaseAndDefersAtDeadline() {
        RequestThrottle throttle = unpaced(2);
        var first = throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1)));
        throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1)));

        assertThatThrownBy(() -> throttle.acquire(HOST, deadlineIn(Duration.ofMillis(100))))
                .isInstanceOf(NonRetryableApiClientException.class)
                .hasMessageContaining("Deferred");

        throttle.release(first, true);
        throttle.acquire(HOST, deadlineIn(Duration.ofMillis(100)));
    }

    @Test
    void peakInFlight_neverExceedsTheCap() throws Exception {
        RequestThrottle throttle = unpaced(3);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger observedPeak = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        try {
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 40; i++) {
                futures.add(pool.submit(() -> {
                    var permit = throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(10)));
                    try {
                        observedPeak.accumulateAndGet(running.incrementAndGet(), Math::max);
                        TimeUnit.MILLISECONDS.sleep(5);
                    } finally {
                        running.decrementAndGet();
                        throttle.release(permit, true);
                    }
                    return null;
                }));
            }
            for (Future<?> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(observedPeak.get()).isLessThanOrEqualTo(3);
        assertThat(throttle.peakInFlight()).isEqualTo(3);
    }

    @Test
    void cooldown_holdsBackOnlyThatHost() {
        RequestThrottle throttle = unpaced(4);
        throttle.cooldown(HOST, Duration.ofMillis(300));

        Duration other = timed(() -> throttle.release(throttle.acquire(OTHER_HOST,
                deadlineIn(Duration.ofSeconds(1))), true));
        Duration cooled = timed(() -> throttle.release(throttle.acquire(HOST,
                deadlineIn(Duration.ofSeconds(1))), true));

        assertThat(other).isLessThan(Duration.ofMillis(100));
        assertThat(cooled).isGreaterThanOrEqualTo(Duration.ofMillis(250));
    }

    @Test
    void cooldownBeyondDeadline_isDeferredImmediately() {
        RequestThrottle throttle = unpaced(4);
        throttle.cooldown(HOST, Duration.ofSeconds(30));

        Duration elapsed = timed(() -> assertThatThrownBy(() -> throttle.acquire(HOST,
                deadlineIn(Duration.ofSeconds(1)))).isInstanceOf(NonRetryableApiClientException.class));

        assertThat(elapsed).isLessThan(Duration.ofMillis(200));
    }

    @Test
    void circuit_opensAfterConsecutiveFailures_thenAdmitsOneProbe() throws Exception {
        RequestThrottle throttle = throttle(4, 10_000, 10_000, 2, Duration.ofMillis(200));
        throttle.release(throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1))), false);
        throttle.release(throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1))), false);

        long started = System.nanoTime();
        var probe = throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(2)));
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(150));

        // While the probe runs, everyone else waits for its result.
        var second = CompletableFuture.supplyAsync(() -> throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(2))));
        TimeUnit.MILLISECONDS.sleep(150);
        assertThat(second).isNotDone();

        throttle.release(probe, true);
        throttle.release(second.get(1, TimeUnit.SECONDS), true);
    }

    @Test
    void failedProbe_reopensTheCircuit() {
        RequestThrottle throttle = throttle(4, 10_000, 10_000, 1, Duration.ofMillis(200));
        throttle.release(throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1))), false);
        var probe = throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1)));
        throttle.release(probe, false);

        Duration elapsed = timed(() -> throttle.release(throttle.acquire(HOST,
                deadlineIn(Duration.ofSeconds(1))), true));

        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(150));
    }

    @Test
    void abandonedProbe_leavesTheCircuitOpenForTheNextProbe() {
        RequestThrottle throttle = throttle(4, 10_000, 10_000, 1, Duration.ofMillis(100));
        throttle.release(throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1))), false);
        var probe = throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1)));

        throttle.abandon(probe);

        // Still half-open: the next caller becomes the probe instead of the circuit closing.
        var nextProbe = throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1)));
        throttle.release(nextProbe, false);
        Duration elapsed = timed(() -> throttle.release(throttle.acquire(HOST,
                deadlineIn(Duration.ofSeconds(1))), true));
        assertThat(elapsed).isGreaterThanOrEqualTo(Duration.ofMillis(70));
    }

    @Test
    void interruptWhileWaiting_cancelsAndKeepsTheFlag() throws Exception {
        RequestThrottle throttle = unpaced(1);
        throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(1)));
        var cancelled = new AtomicBoolean();
        var interruptKept = new AtomicBoolean();
        Thread waiter = new Thread(() -> {
            try {
                throttle.acquire(HOST, deadlineIn(Duration.ofSeconds(30)));
            } catch (CancellationException e) {
                cancelled.set(true);
                interruptKept.set(Thread.currentThread().isInterrupted());
            }
        });
        waiter.start();
        TimeUnit.MILLISECONDS.sleep(100);
        waiter.interrupt();
        waiter.join(1000);

        assertThat(waiter.isAlive()).isFalse();
        assertThat(cancelled).isTrue();
        assertThat(interruptKept).isTrue();
    }
}
