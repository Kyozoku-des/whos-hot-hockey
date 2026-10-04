package com.whoshot.nhl.datajob.service;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessResourceException;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.concurrent.CancellationException;

/**
 * Separates failures that end an ingestion run from record-level ones that only skip a record.
 */
final class IngestionFailures {

    private IngestionFailures() {
    }

    /**
     * A stop request, or a lost database: every remaining record would fail the same way, so the
     * run must stop rather than report each one as a skip.
     */
    static boolean isFatal(Throwable e) {
        return e instanceof CancellationException
                || e instanceof DataAccessResourceFailureException
                || e instanceof TransientDataAccessResourceException
                || e instanceof CannotCreateTransactionException
                || e instanceof Error;
    }

    /** Rethrows {@code e} if it is fatal; returns otherwise so the caller can skip the record. */
    static void rethrowIfFatal(Throwable e) {
        if (!isFatal(e)) {
            return;
        }
        if (e instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw (Error) e;
    }
}
