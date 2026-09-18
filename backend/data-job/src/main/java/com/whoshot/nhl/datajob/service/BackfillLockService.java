package com.whoshot.nhl.datajob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Guards against two concurrent backfills of the same season corrupting each other's writes
 * (FR-014, research R-011), using a Postgres session-level advisory lock keyed on the season id.
 * <p>
 * An advisory lock is preferred over a status table because it releases automatically if the
 * holding connection dies, so a crashed run never leaves a permanent block on future runs.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackfillLockService {

    /**
     * Arbitrary namespace to keep this feature's advisory locks distinct from any others that
     * might ever be taken against this database.
     */
    private static final int LOCK_NAMESPACE = 0x4E484C42; // "NHLB" in hex, arbitrary

    private final JdbcTemplate jdbcTemplate;

    /**
     * Attempts to acquire the advisory lock for a season on the current database session.
     * Non-blocking: returns immediately whether or not the lock was obtained.
     *
     * @param seasonId season to lock
     * @return true if the lock was acquired
     */
    public boolean tryLock(String seasonId) {
        Boolean acquired = jdbcTemplate.queryForObject(
                "SELECT pg_try_advisory_lock(?, ?)", Boolean.class, LOCK_NAMESPACE, lockKey(seasonId));
        boolean result = Boolean.TRUE.equals(acquired);
        if (!result) {
            log.warn("Could not acquire backfill lock for season {}: another run is in progress", seasonId);
        }
        return result;
    }

    /**
     * Releases the advisory lock for a season on the current database session.
     *
     * @param seasonId season to unlock
     */
    public void unlock(String seasonId) {
        jdbcTemplate.queryForObject(
                "SELECT pg_advisory_unlock(?, ?)", Boolean.class, LOCK_NAMESPACE, lockKey(seasonId));
    }

    private int lockKey(String seasonId) {
        return seasonId.hashCode();
    }
}
