package com.whoshot.nhl.datajob.exception;

/**
 * Exception thrown when an external API request fails or returns an invalid response.
 */
public class ApiClientException extends RuntimeException {

    /**
     * Creates an exception with a descriptive failure message.
     *
     * @param message human-readable error message
     */
    public ApiClientException(String message) {
        super(message);
    }

    /**
     * Creates an exception with a descriptive failure message and root cause.
     *
     * @param message human-readable error message
     * @param cause underlying cause of the failure
     */
    public ApiClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
