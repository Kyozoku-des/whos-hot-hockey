package com.whoshot.nhl.datajob.exception;

import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Exception thrown when an external API request fails or returns an invalid response.
 * <p>
 * Carries the structured inputs the retry policy needs: the HTTP status (absent for transport
 * failures and invalid payloads), the server-directed {@code Retry-After} delay, and whether
 * repeating the request can help.
 */
public class ApiClientException extends RuntimeException {

    private final Integer status;
    private final Duration retryAfter;
    private final boolean retryable;

    /**
     * Creates a retryable exception with a descriptive failure message.
     *
     * @param message human-readable error message
     */
    public ApiClientException(String message) {
        this(message, null, null, null, true);
    }

    /**
     * Creates a retryable exception with a descriptive failure message and root cause.
     *
     * @param message human-readable error message
     * @param cause underlying cause of the failure
     */
    public ApiClientException(String message, Throwable cause) {
        this(message, cause, null, null, true);
    }

    protected ApiClientException(String message, Throwable cause, Integer status, Duration retryAfter,
                                 boolean retryable) {
        super(message, cause);
        this.status = status;
        this.retryAfter = retryAfter;
        this.retryable = retryable;
    }

    /**
     * Creates an exception for an HTTP error response.
     *
     * @param message    human-readable error message
     * @param status     HTTP status code
     * @param retryAfter parsed {@code Retry-After}, or null when absent
     * @param retryable  whether repeating the request can succeed
     * @return the exception, a {@link NonRetryableApiClientException} when not retryable
     */
    public static ApiClientException forStatus(String message, int status, Duration retryAfter, boolean retryable) {
        return retryable
                ? new ApiClientException(message, null, status, retryAfter, true)
                : new NonRetryableApiClientException(message, null, status);
    }

    /** HTTP status of the failed response, empty for transport failures and invalid payloads. */
    public OptionalInt status() {
        return status == null ? OptionalInt.empty() : OptionalInt.of(status);
    }

    /** Server-directed delay before the next attempt, if the response carried one. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    /** Whether repeating the request can succeed. */
    public boolean isRetryable() {
        return retryable;
    }
}
