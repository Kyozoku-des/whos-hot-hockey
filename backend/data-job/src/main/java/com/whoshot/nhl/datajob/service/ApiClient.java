package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.exception.NonRetryableApiClientException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Map;

/**
 * Reusable HTTP client wrapper that standardizes API calls, error handling, and retries.
 * <p>
 * Public methods retry on {@link ApiClientException} (3 attempts, 1s then 2s backoff), enabled by
 * {@link com.whoshot.nhl.datajob.config.ResilienceConfig}. 4xx responses raise
 * {@link NonRetryableApiClientException} and fail fast, except 429 Too Many Requests, which waits
 * for {@code Retry-After} (capped at {@link #MAX_RETRY_AFTER}) and is retried. Retries apply only
 * to calls through the
 * Spring proxy, so {@link #get(String, ParameterizedTypeReference)} delegating to the three-argument
 * overload retries once at the outer call, not twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ApiClient {

    /** Upper bound on how long a single {@code Retry-After} wait may block the caller. */
    static final Duration MAX_RETRY_AFTER = Duration.ofSeconds(30);

    private final RestClient restClient;

    /**
     * Common error handler for 4xx client errors. 429 is transient: it waits for
     * {@code Retry-After} and raises a retryable {@link ApiClientException}.
     *
     * @param url request URL that produced the error
     * @param httpResponse raw HTTP response used for status extraction
     */
    private void handleClientError(String url, ClientHttpResponse httpResponse) throws IOException {
        if (httpResponse.getStatusCode().isSameCodeAs(HttpStatus.TOO_MANY_REQUESTS)) {
            Duration wait = parseRetryAfter(httpResponse.getHeaders().getFirst(HttpHeaders.RETRY_AFTER));
            log.warn("Rate limited during request to {}; waiting {} before retry", url, wait);
            sleep(wait);
            throw new ApiClientException("Rate limited: 429 for URL: " + url);
        }
        log.error("Client error during request to {}: {}", url, httpResponse.getStatusCode());
        throw new NonRetryableApiClientException(
                "Client error: " + httpResponse.getStatusCode() + " for URL: " + url);
    }

    /**
     * Common error handler for 5xx server errors.
     *
     * @param url request URL that produced the error
     * @param httpResponse raw HTTP response used for status extraction
     */
    private void handleServerError(String url, ClientHttpResponse httpResponse) throws IOException {
        log.error("Server error during request to {}: {}", url, httpResponse.getStatusCode());
        throw new ApiClientException(
                "Server error: " + httpResponse.getStatusCode() + " for URL: " + url);
    }

    /**
     * Parses a {@code Retry-After} value (delay-seconds or HTTP-date, RFC 9110 section 10.2.3),
     * clamped to {@code [0, MAX_RETRY_AFTER]}. Missing or malformed values yield zero, leaving the
     * regular retry backoff as the only delay.
     *
     * @param value raw header value, may be null
     * @return wait duration before the next attempt
     */
    static Duration parseRetryAfter(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ZERO;
        }
        Duration wait;
        try {
            wait = Duration.ofSeconds(Long.parseLong(value.trim()));
        } catch (NumberFormatException e) {
            try {
                ZonedDateTime at = ZonedDateTime.parse(value.trim(), DateTimeFormatter.RFC_1123_DATE_TIME);
                wait = Duration.between(ZonedDateTime.now(at.getZone()), at);
            } catch (DateTimeParseException ex) {
                return Duration.ZERO;
            }
        }
        if (wait.isNegative()) {
            return Duration.ZERO;
        }
        return wait.compareTo(MAX_RETRY_AFTER) > 0 ? MAX_RETRY_AFTER : wait;
    }

    /**
     * Blocks for the given duration; an interrupt aborts the call without further retries.
     *
     * @param wait how long to sleep
     */
    private static void sleep(Duration wait) {
        if (wait.isZero()) {
            return;
        }
        try {
            Thread.sleep(wait);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NonRetryableApiClientException("Interrupted while waiting for Retry-After");
        }
    }

    /**
     * Performs a GET request without custom headers.
     *
     * @param url target endpoint URL
     * @param typeRef expected response body type reference
     * @param <T> response type
     * @return deserialized response body
     * @throws ApiClientException if the request fails after all retries or returns a null body
     */
    @Retryable(
            includes = {ApiClientException.class},
            excludes = {NonRetryableApiClientException.class},
            maxRetries = 2,
            delay = 1000,
            multiplier = 2
    )
    public <T> T get(String url, ParameterizedTypeReference<T> typeRef) {
        return get(url, typeRef, null);
    }

    /**
     * Performs a GET request with custom headers to the specified URL and returns the response body.
     * Makes up to 3 attempts on retryable ApiClientException with exponential backoff.
     *
     * @param url          the URL to send the GET request to
     * @param typeRef      the class type of the expected response
     * @param headers      custom headers to include in the request
     * @param <T>          the type of the response
     * @return the response body
     * @throws ApiClientException if the request fails after all retries
     */
    @Retryable(
            includes = {ApiClientException.class},
            excludes = {NonRetryableApiClientException.class},
            maxRetries = 2,
            delay = 1000,
            multiplier = 2
    )
    public <T> T get(String url, ParameterizedTypeReference<T> typeRef, Map<String, String> headers) {
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
                    .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, httpResponse) -> handleClientError(url, httpResponse))
                    .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, httpResponse) -> handleServerError(url, httpResponse))
                    .body(typeRef);

            if (response == null) {
                throw new ApiClientException("Received null response from: " + url);
            }

            log.debug("GET request successful: {}", url);
            return response;

        } catch (ApiClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during GET request to {}: {}", url, e.getMessage(), e);
            throw new ApiClientException("Unexpected error for URL: " + url, e);
        }
    }

    /**
     * Performs a POST request with multipart form data (for file uploads).
     * Makes up to 3 attempts on retryable ApiClientException with exponential backoff.
     *
     * @param url          the URL to send the POST request to
     * @param formData     the multipart form data (use MultiValueMap with Resource for files)
     * @param typeRef      the class type of the expected response
     * @param <T>          the type of the response
     * @return the response body
     * @throws ApiClientException if the request fails after all retries
     */
    @Retryable(
            includes = {ApiClientException.class},
            excludes = {NonRetryableApiClientException.class},
            maxRetries = 2,
            delay = 1000,
            multiplier = 2
    )
    public <T> T postMultipart(String url, MultiValueMap<String, Object> formData, ParameterizedTypeReference<T> typeRef) {
        try {
            log.debug("POST multipart request to: {}", url);

            T response = restClient.post()
                    .uri(url)
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(formData)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (httpRequest, httpResponse) -> handleClientError(url, httpResponse))
                    .onStatus(HttpStatusCode::is5xxServerError, (httpRequest, httpResponse) -> handleServerError(url, httpResponse))
                    .body(typeRef);

            if (response == null) {
                throw new ApiClientException("Received null response from: " + url);
            }

            log.debug("POST multipart request successful: {}", url);
            return response;

        } catch (ApiClientException e) {
            throw e;
        } catch (Exception e) {
            log.error("Unexpected error during POST multipart request to {}: {}", url, e.getMessage(), e);
            throw new ApiClientException("Unexpected error for URL: " + url, e);
        }
    }
}
