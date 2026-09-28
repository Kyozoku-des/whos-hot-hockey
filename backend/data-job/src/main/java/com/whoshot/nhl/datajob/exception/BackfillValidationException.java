package com.whoshot.nhl.datajob.exception;

/**
 * Thrown when a requested backfill season fails validation (FR-002). Guaranteed to be thrown
 * before any database write occurs.
 */
public class BackfillValidationException extends RuntimeException {
    public BackfillValidationException(String message) {
        super(message);
    }
}
