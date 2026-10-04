package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.ApiRequestProperties;
import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.exception.NonRetryableApiClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

/**
 * Reusable HTTP client wrapper that standardizes API calls, error handling, and retries.
 * <p>
 * This class is the single retry owner (Apache HttpClient's automatic retries are disabled in
 * {@link com.whoshot.nhl.datajob.config.RestClientConfig}). Each attempt first obtains a permit from
 * the shared {@link RequestThrottle}, so retries spend the same in-flight and rate budget as first
 * attempts. Only idempotent GETs are issued, and only these failures are retried:
 * <ul>
 *   <li>transport failures (connect/read timeouts, resets);</li>
 *   <li>{@code 408}, {@code 429}, {@code 500}, {@code 502}, {@code 503} and {@code 504}.</li>
 * </ul>
 * Other 4xx responses, invalid or empty payloads, and cancellation fail immediately. Retries use
 * exponential backoff with jitter, never earlier than a {@code Retry-After}, and stop at
 * {@code maxAttempts} or when the next attempt could not start before the operation deadline. A
 * {@code 429}/{@code 503} also cools the host down for every other worker.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClient {

    /** Upper bound on a parsed {@code Retry-After}; anything longer is beyond any run's budget. */
    static final Duration MAX_RETRY_AFTER = Duration.ofHours(1);

    private static final Set<Integer> RETRYABLE_STATUSES = Set.of(408, 429, 500, 502, 503, 504);
    private static final Set<Integer> COOLDOWN_STATUSES = Set.of(429, 503);

    private final RestClient restClient;
    private final RequestThrottle throttle;
    private final ApiRequestProperties properties;
    private final IngestionMetrics metrics;

    /**
     * Performs a GET request without custom headers.
     *
     * @param url target endpoint URL
     * @param typeRef expected response body type reference
     * @param <T> response type
     * @return deserialized response body
     * @throws ApiClientException    if the request fails permanently or exhausts its retry budget
     * @throws CancellationException if the calling thread is interrupted
     */
    public <T> T get(String url, ParameterizedTypeReference<T> typeRef) {
        return get(url, typeRef, null);
    }

    /**
     * Performs a GET request with custom headers to the specified URL and returns the response body.
     *
     * @param url          the URL to send the GET request to
     * @param typeRef      the class type of the expected response
     * @param headers      custom headers to include in the request
     * @param <T>          the type of the response
     * @return the response body
     * @throws ApiClientException    if the request fails permanently or exhausts its retry budget
     * @throws CancellationException if the calling thread is interrupted
     */
    public <T> T get(String url, ParameterizedTypeReference<T> typeRef, Map<String, String> headers) {
        String host = URI.create(url).getHost();
        long deadline = System.nanoTime() + properties.operationDeadline().toNanos();

        for (int attempt = 1; ; attempt++) {
            checkInterrupted();
            RequestThrottle.Permit permit = throttle.acquire(host, deadline);
            metrics.recordThrottleWait(permit.waited());
            long started = System.nanoTime();
            ApiClientException failure;
            try {
                T body = execute(url, typeRef, headers);
                throttle.release(permit, true);
                metrics.recordRequest(url, "success", null, Duration.ofNanos(System.nanoTime() - started));
                return body;
            } catch (ApiClientException e) {
                // Released before any backoff, so a waiting retry holds no request slot.
                throttle.release(permit, !e.isRetryable());
                metrics.recordRequest(url, e.isRetryable() ? "transient" : "permanent",
                        e.status().isPresent() ? e.status().getAsInt() : null,
                        Duration.ofNanos(System.nanoTime() - started));
                failure = e;
            } catch (RuntimeException | Error e) {
                throttle.abandon(permit);
                metrics.recordRequest(url, "cancelled", null, Duration.ofNanos(System.nanoTime() - started));
                throw e;
            }

            Duration delay = nextDelay(host, attempt, failure);
            if (System.nanoTime() + delay.toNanos() - deadline > 0) {
                throw new NonRetryableApiClientException("Deferred " + url + ": next attempt in "
                        + delay.toMillis() + " ms would pass the " + properties.operationDeadline()
                        + " operation deadline (" + failure.getMessage() + ")", failure);
            }
            metrics.recordRetry(url, failure.status().isPresent() ? failure.status().getAsInt() : null);
            log.warn("Attempt {}/{} for {} failed ({}); retrying in {} ms",
                    attempt, properties.maxAttempts(), url, failure.getMessage(), delay.toMillis());
            sleep(delay);
        }
    }

    /**
     * Decides whether another attempt follows and how long to wait for it.
     *
     * @return the delay before the next attempt
     * @throws ApiClientException the failure itself, when it is permanent or attempts are used up
     */
    private Duration nextDelay(String host, int attempt, ApiClientException failure) {
        if (!failure.isRetryable()) {
            throw failure;
        }
        Duration delay = backoff(attempt);
        Duration retryAfter = failure.retryAfter().orElse(Duration.ZERO);
        if (retryAfter.compareTo(delay) > 0) {
            delay = retryAfter;
        }
        // The host asked everyone to slow down, whether or not this caller tries again.
        if (failure.status().isPresent() && COOLDOWN_STATUSES.contains(failure.status().getAsInt())) {
            throttle.cooldown(host, delay);
        }
        if (attempt >= properties.maxAttempts()) {
            throw new NonRetryableApiClientException("Giving up after " + attempt + " attempts: "
                    + failure.getMessage(), failure);
        }
        return delay;
    }

    /**
     * Exponential backoff with equal jitter: half the capped exponential delay, plus a random part
     * of up to the other half, so workers that failed together do not retry together.
     */
    Duration backoff(int attempt) {
        long base = properties.initialBackoff().toMillis() << Math.min(attempt - 1, 20);
        long capped = Math.min(base, properties.maxBackoff().toMillis());
        long half = capped / 2;
        return Duration.ofMillis(half + ThreadLocalRandom.current().nextLong(capped - half + 1));
    }

    private <T> T execute(String url, ParameterizedTypeReference<T> typeRef, Map<String, String> headers) {
        try {
            log.debug("GET request to: {}", url);

            var requestSpec = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON);

            if (headers != null && !headers.isEmpty()) {
                requestSpec.headers(httpHeaders -> headers.forEach(httpHeaders::add));
            }

            T response = requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (httpRequest, httpResponse) -> {
                        throw toException(url, httpResponse);
                    })
                    .body(typeRef);

            if (response == null) {
                throw new NonRetryableApiClientException("Received null response from: " + url);
            }

            log.debug("GET request successful: {}", url);
            return response;

        } catch (ApiClientException e) {
            throw e;
        } catch (ResourceAccessException e) {
            checkInterrupted();
            throw new ApiClientException("Transport failure for URL: " + url + ": " + e.getMessage(), e);
        } catch (RestClientException e) {
            throw new NonRetryableApiClientException("Invalid response from URL: " + url + ": " + e.getMessage(), e);
        } catch (RuntimeException e) {
            throw new NonRetryableApiClientException("Unexpected error for URL: " + url + ": " + e.getMessage(), e);
        }
    }

    /**
     * Maps an error response to a typed exception that keeps its status and {@code Retry-After}.
     */
    private static ApiClientException toException(String url, ClientHttpResponse httpResponse) throws IOException {
        int status = httpResponse.getStatusCode().value();
        boolean retryable = RETRYABLE_STATUSES.contains(status);
        Duration retryAfter = parseRetryAfter(httpResponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
        if (retryable) {
            log.warn("Transient error during request to {}: {}", url, status);
        } else {
            log.error("Permanent error during request to {}: {}", url, status);
        }
        return ApiClientException.forStatus("HTTP " + status + " for URL: " + url, status, retryAfter, retryable);
    }

    /**
     * Parses a {@code Retry-After} value (delay-seconds or HTTP-date, RFC 9110 section 10.2.3),
     * clamped to {@code [0, MAX_RETRY_AFTER]}.
     *
     * @param value raw header value, may be null
     * @return the directed delay, or null when the header is missing or malformed
     */
    static Duration parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        Duration wait;
        try {
            wait = Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            try {
                ZonedDateTime at = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                wait = Duration.between(ZonedDateTime.now(at.getZone()), at);
            } catch (DateTimeParseException ex) {
                return null;
            }
        }
        if (wait.isNegative()) {
            return Duration.ZERO;
        }
        return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Request cancelled");
        }
    }

    private static void sleep(Duration wait) {
        try {
            TimeUnit.NANOSECONDS.sleep(wait.toNanos());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while backing off");
        }
    }
}
