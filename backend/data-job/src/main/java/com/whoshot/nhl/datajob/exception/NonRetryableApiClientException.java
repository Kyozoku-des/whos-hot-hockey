package com.whoshot.nhl.datajob.exception;

/**
 * {@link ApiClientException} for failures that repeating the request cannot fix: permanent 4xx
 * responses, invalid payloads, and an exhausted retry budget.
 */
public class NonRetryableApiClientException extends ApiClientException {

    /**
     * Creates an exception with a descriptive failure message.
     *
     * @param message human-readable error message
     */
    public NonRetryableApiClientException(String message) {
        this(message, null, null);
    }

    /**
     * Creates an exception with a descriptive failure message and root cause.
     *
     * @param message human-readable error message
     * @param cause underlying cause of the failure
     */
    public NonRetryableApiClientException(String message, Throwable cause) {
        this(message, cause, null);
    }

    NonRetryableApiClientException(String message, Throwable cause, Integer status) {
        super(message, cause, status, null, false);
    }
}
