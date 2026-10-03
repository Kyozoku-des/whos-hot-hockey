package com.whoshot.nhl.datajob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Guards against two concurrent writers of the same season corrupting each other's writes
 * (FR-014, research R-011), using a Postgres session-level advisory lock keyed on the season id.
 * Every writer of season data takes it: backfill, initial load, and the live daemon's full syncs
 * and game-time polls (issue #29). Because the lock lives in Postgres it also excludes writers in
 * other data-job processes; backfills of other seasons are unaffected.
 * <p>
 * An advisory lock is preferred over a status table because it releases automatically if the
 * holding connection dies, so a crashed run never leaves a permanent block on future runs.
 * <p>
 * Session-level advisory locks belong to the database connection that took them, so each lock
 * holds one dedicated connection from acquire to release. Going through a pooled
 * {@code JdbcTemplate} instead would let the lock and unlock land on different connections,
 * leaving the lock stranded on an idle pooled connection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillLockService {

    /**
     * Arbitrary namespace to keep this feature's advisory locks distinct from any others that
     * might ever be taken against this database. Public so the lock can be identified in
     * {@code pg_locks} (classid = namespace, objid = season key).
     */
    public static final int LOCK_NAMESPACE = 0x4E484C42; // "NHLB" in hex, arbitrary

    private final DataSource dataSource;
    private final Map<String, Connection> heldLocks = new ConcurrentHashMap<>();

    /** Season work that may throw a checked exception. */
    @FunctionalInterface
    public interface SeasonWork<E extends Exception> {
        void run() throws E;
    }

    /**
     * Runs {@code work} while holding the season's lock, releasing it however the work ends.
     *
     * @param seasonId season to lock
     * @param work     writes to perform
     * @return false, without running the work, if another writer holds the lock
     */
    public <E extends Exception> boolean runExclusively(String seasonId, SeasonWork<E> work) throws E {
        if (!tryLock(seasonId)) {
            return false;
        }
        try {
            work.run();
            return true;
        } finally {
            unlock(seasonId);
        }
    }

    /**
     * Attempts to acquire the advisory lock for a season on a dedicated database session.
     * Non-blocking: returns immediately whether or not the lock was obtained.
     *
     * @param seasonId season to lock
     * @return true if the lock was acquired
     */
    public boolean tryLock(String seasonId) {
        Connection connection = null;
        try {
            connection = dataSource.getConnection();
            boolean acquired;
            try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
                statement.setInt(1, LOCK_NAMESPACE);
                statement.setInt(2, lockKey(seasonId));
                try (ResultSet result = statement.executeQuery()) {
                    acquired = result.next() && result.getBoolean(1);
                }
            }
            if (acquired) {
                heldLocks.put(seasonId, connection);
                return true;
            }
            connection.close();
            log.warn("Could not acquire write lock for season {}: another writer is in progress", seasonId);
            return false;
        } catch (SQLException e) {
            discard(connection);
            throw new IllegalStateException("Could not query backfill lock for season " + seasonId, e);
        }
    }

    /**
     * Releases the advisory lock for a season and returns its dedicated connection. A no-op if
     * this instance does not hold the lock.
     *
     * @param seasonId season to unlock
     */
    public void unlock(String seasonId) {
        Connection connection = heldLocks.remove(seasonId);
        if (connection == null) {
            return;
        }
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_advisory_unlock(?, ?)")) {
            statement.setInt(1, LOCK_NAMESPACE);
            statement.setInt(2, lockKey(seasonId));
            statement.execute();
            connection.close();
        } catch (SQLException e) {
            // Never hand a connection that may still hold the lock back to the pool.
            log.error("Could not release backfill lock for season {}; discarding its connection", seasonId, e);
            discard(connection);
        }
    }

    /**
     * The advisory-lock key for a season within {@link #LOCK_NAMESPACE}.
     *
     * @param seasonId season id
     * @return lock key
     */
    public static int lockKey(String seasonId) {
        return seasonId.hashCode();
    }

    /** Closes the physical connection so the database drops any session-level lock it holds. */
    private static void discard(Connection connection) {
        if (connection == null) {
            return;
        }
        try {
            connection.abort(Runnable::run);
        } catch (SQLException e) {
            log.warn("Failed to abort backfill lock connection: {}", e.getMessage());
        }
    }
}
