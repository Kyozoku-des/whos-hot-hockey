package com.whoshot.nhl.datajob.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * Ingestion and outbound-request metrics (issue #29), published through Micrometer and summarised
 * in one log line per run.
 * <p>
 * Labels stay low-cardinality: an endpoint category (never a player or team id), an outcome, an
 * HTTP status, and a run mode.
 * <ul>
 *   <li>{@code nhl.api.requests} — timer per attempt, p50/p95 published; tags endpoint, outcome, status</li>
 *   <li>{@code nhl.api.retries} — counter; tags endpoint, status</li>
 *   <li>{@code nhl.api.throttle.wait} — timer of time spent waiting for the request budget</li>
 *   <li>{@code nhl.api.in.flight}, {@code ingestion.fetch.outstanding} — gauges</li>
 *   <li>{@code ingestion.run} — timer per run; tag mode</li>
 *   <li>{@code ingestion.persist} — timer per record write (database time); tag mode</li>
 *   <li>{@code ingestion.records} — counter; tags mode, result (written, filtered, skipped, deferred)</li>
 *   <li>{@code ingestion.last.success} — gauge, epoch seconds of the last run per mode without
 *       skipped records; freshness age is now minus this value</li>
 * </ul>
 */
@Slf4j
@Component
public class IngestionMetrics {

    private final MeterRegistry registry;
    private final LongAdder requests = new LongAdder();
    private final LongAdder retries = new LongAdder();
    private final LongAdder throttleWaitNanos = new LongAdder();
    private final Map<String, AtomicLong> lastSuccess = new ConcurrentHashMap<>();

    @Autowired
    public IngestionMetrics(ObjectProvider<MeterRegistry> registry, RequestThrottle throttle, FetchPipeline pipeline) {
        this(registry.getIfAvailable(SimpleMeterRegistry::new));
        Gauge.builder("nhl.api.in.flight", throttle, RequestThrottle::inFlight)
                .description("Outbound NHL API requests currently running")
                .register(this.registry);
        Gauge.builder("ingestion.fetch.outstanding", pipeline, FetchPipeline::outstanding)
                .description("Fetches submitted but not yet consumed")
                .register(this.registry);
    }

    IngestionMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    /** Metrics recorded into a private registry, for tests and code built outside Spring. */
    public static IngestionMetrics standalone() {
        return new IngestionMetrics(new SimpleMeterRegistry());
    }

    MeterRegistry registry() {
        return registry;
    }

    /** Records one finished request attempt. */
    void recordRequest(String url, String outcome, Integer status, Duration latency) {
        requests.increment();
        Timer.builder("nhl.api.requests")
                .tag("endpoint", endpoint(url))
                .tag("outcome", outcome)
                .tag("status", status != null ? String.valueOf(status) : "success".equals(outcome) ? "2xx" : "none")
                .publishPercentiles(0.5, 0.95)
                .register(registry)
                .record(latency);
    }

    /** Records a retry about to happen. */
    void recordRetry(String url, Integer status) {
        retries.increment();
        Counter.builder("nhl.api.retries")
                .tag("endpoint", endpoint(url))
                .tag("status", status == null ? "none" : String.valueOf(status))
                .register(registry)
                .increment();
    }

    /** Records time a request spent waiting for admission. */
    void recordThrottleWait(Duration waited) {
        throttleWaitNanos.add(waited.toNanos());
        Timer.builder("nhl.api.throttle.wait").register(registry).record(waited);
    }

    /** Starts timing one ingestion run. */
    public Run startRun(String mode) {
        return new Run(mode);
    }

    /**
     * Maps a URL to a fixed endpoint category so no id ever becomes a metric label.
     */
    static String endpoint(String url) {
        String path;
        try {
            path = URI.create(url).getPath();
        } catch (IllegalArgumentException e) {
            return "other";
        }
        if (path == null) {
            return "other";
        }
        if (path.endsWith("/landing")) {
            return "player-landing";
        }
        if (path.contains("/game-log/")) {
            return "player-game-log";
        }
        if (path.contains("/club-schedule-season/")) {
            return "team-schedule";
        }
        if (path.contains("/skater-stats-leaders/")) {
            return "player-standings";
        }
        if (path.contains("/standings/")) {
            return "team-standings";
        }
        if (path.contains("/schedule/")) {
            return "league-schedule";
        }
        if (path.endsWith("/season")) {
            return "seasons";
        }
        return "other";
    }

    /** One timed run; counts persistence time and record results, then logs a summary. */
    public final class Run {
        private final String mode;
        private final long startedNanos = System.nanoTime();
        private final long requestsAtStart = requests.sum();
        private final long retriesAtStart = retries.sum();
        private final long throttleWaitAtStart = throttleWaitNanos.sum();
        private long persistNanos;

        private Run(String mode) {
            this.mode = mode;
        }

        /** Adds one record write's database time. */
        public void recordPersist(long startedNanos) {
            long elapsed = System.nanoTime() - startedNanos;
            persistNanos += elapsed;
            Timer.builder("ingestion.persist").tag("mode", mode).register(registry).record(Duration.ofNanos(elapsed));
        }

        /**
         * Ends the run, publishes its counts and logs one summary line.
         *
         * @return the run's duration
         */
        public Duration finish(int written, int filtered, int skipped, int deferred) {
            Duration duration = Duration.ofNanos(System.nanoTime() - startedNanos);
            Timer.builder("ingestion.run").tag("mode", mode).register(registry).record(duration);
            count("written", written);
            count("filtered", filtered);
            count("skipped", skipped);
            count("deferred", deferred);

            AtomicLong last = lastSuccess.computeIfAbsent(mode, m -> registry.gauge(
                    "ingestion.last.success", io.micrometer.core.instrument.Tags.of("mode", m), new AtomicLong()));
            Instant previousSuccess = last.get() == 0 ? null : Instant.ofEpochSecond(last.get());
            if (skipped == 0) {
                last.set(Instant.now().getEpochSecond());
            }

            long runRequests = requests.sum() - requestsAtStart;
            double seconds = Math.max(duration.toNanos(), 1) / 1e9;
            log.info("[ingestion] {} run in {} ms: {} requests ({} /s), {} retries, throttle wait {} ms, "
                            + "db {} ms; {} written, {} filtered, {} skipped ({} deferred); previous complete run {}",
                    mode, duration.toMillis(), runRequests, Math.round(runRequests / seconds * 10) / 10.0,
                    retries.sum() - retriesAtStart,
                    Duration.ofNanos(throttleWaitNanos.sum() - throttleWaitAtStart).toMillis(),
                    Duration.ofNanos(persistNanos).toMillis(), written, filtered, skipped, deferred,
                    previousSuccess == null ? "none" : Duration.between(previousSuccess, Instant.now()).toSeconds() + "s ago");
            return duration;
        }

        private void count(String result, int amount) {
            Counter.builder("ingestion.records").tag("mode", mode).tag("result", result)
                    .register(registry).increment(amount);
        }
    }
}
