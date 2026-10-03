package com.whoshot.nhl.datajob.exception;

/**
 * {@link ApiClientException} for failures that repeating the request cannot fix, such as 4xx responses.
 */
public class NonRetryableApiClientException extends ApiClientException {

    /**
     * Creates an exception with a descriptive failure message.
     *
     * @param message human-readable error message
     */
    public NonRetryableApiClientException(String message) {
        super(message);
    }
}
