package com.whoshot.nhl.datajob.exception;

/**
 * Checked exception thrown when calculated player statistics fail validation.
 */
public class PlayerStatisticsException extends Exception {
    /**
     * Creates an exception with a validation failure message.
     *
     * @param message details about the statistics validation failure
     */
    public PlayerStatisticsException(String message) {
        super(message);
    }
}
