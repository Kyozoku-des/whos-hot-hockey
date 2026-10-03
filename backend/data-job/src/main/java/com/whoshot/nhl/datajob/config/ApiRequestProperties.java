package com.whoshot.nhl.datajob.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

import java.time.Duration;

/**
 * Process-wide outbound request policy for the NHL API ({@code nhle.api.requests.*}).
 * <p>
 * Every attempt, retries included, passes through one shared budget: at most
 * {@code maxInFlight} requests at once, and request starts paced to {@code requestsPerSecond}
 * with bursts of up to {@code burst}. The budget is per JVM: when several data-job processes run
 * against the same upstream, configure budgets whose sum stays within the deployment budget.
 *
 * @param maxInFlight               concurrent requests allowed; also sizes the HTTP connection pool
 * @param requestsPerSecond         aggregate request-start rate across all hosts
 * @param burst                     request starts allowed back to back before pacing applies
 * @param maxAttempts               attempts per request, the first included
 * @param initialBackoff            base delay before the first retry; doubles per retry
 * @param maxBackoff                cap on a single computed backoff
 * @param operationDeadline         total time one request may spend, waits and retries included
 * @param circuitFailureThreshold   consecutive transient failures that open a host's circuit
 * @param circuitOpenDuration       how long an open circuit blocks requests before one probe
 */
@ConfigurationProperties("nhle.api.requests")
public record ApiRequestProperties(
        @DefaultValue("4") int maxInFlight,
        @DefaultValue("10") double requestsPerSecond,
        @DefaultValue("5") int burst,
        @DefaultValue("3") int maxAttempts,
        @DefaultValue("1s") Duration initialBackoff,
        @DefaultValue("30s") Duration maxBackoff,
        @DefaultValue("60s") Duration operationDeadline,
        @DefaultValue("5") int circuitFailureThreshold,
        @DefaultValue("30s") Duration circuitOpenDuration) {

    public ApiRequestProperties {
        if (maxInFlight < 1) {
            throw new IllegalArgumentException("nhle.api.requests.max-in-flight must be at least 1");
        }
        if (!(requestsPerSecond > 0)) {
            throw new IllegalArgumentException("nhle.api.requests.requests-per-second must be positive");
        }
        if (burst < 1) {
            throw new IllegalArgumentException("nhle.api.requests.burst must be at least 1");
        }
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("nhle.api.requests.max-attempts must be at least 1");
        }
        if (circuitFailureThreshold < 1) {
            throw new IllegalArgumentException("nhle.api.requests.circuit-failure-threshold must be at least 1");
        }
    }

    /** Defaults, for tests and callers outside Spring. */
    public static ApiRequestProperties defaults() {
        return new ApiRequestProperties(4, 10, 5, 3, Duration.ofSeconds(1), Duration.ofSeconds(30),
                Duration.ofSeconds(60), 5, Duration.ofSeconds(30));
    }
}
