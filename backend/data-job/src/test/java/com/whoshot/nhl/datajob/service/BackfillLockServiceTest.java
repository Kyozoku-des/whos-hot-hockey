package com.whoshot.nhl.datajob.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit coverage for {@link BackfillLockService} (FR-014, research R-011): it must ask Postgres for
 * a non-blocking advisory lock keyed consistently on the season id, and report the result rather
 * than blocking. True cross-connection concurrency is Postgres's own well-established guarantee for
 * {@code pg_try_advisory_lock}; this test verifies the SQL contract this class relies on.
 */
@ExtendWith(MockitoExtension.class)
class BackfillLockServiceTest {

    @Mock
    private JdbcTemplate jdbcTemplate;

    private BackfillLockService lockService;

    @BeforeEach
    void setUp() {
        lockService = new BackfillLockService(jdbcTemplate);
    }

    @Test
    void tryLock_returnsTrue_whenPostgresGrantsTheLock() {
        when(jdbcTemplate.queryForObject(eq("SELECT pg_try_advisory_lock(?, ?)"), eq(Boolean.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        assertTrue(lockService.tryLock("20242025"));
    }

    @Test
    void tryLock_returnsFalse_whenAnotherRunHoldsTheLock() {
        when(jdbcTemplate.queryForObject(eq("SELECT pg_try_advisory_lock(?, ?)"), eq(Boolean.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(false);

        assertFalse(lockService.tryLock("20242025"));
    }

    @Test
    void sameSeasonId_alwaysProducesTheSameLockKey() {
        when(jdbcTemplate.queryForObject(eq("SELECT pg_try_advisory_lock(?, ?)"), eq(Boolean.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        lockService.tryLock("20242025");
        lockService.tryLock("20242025");

        ArgumentCaptor<Object> keyCaptor = ArgumentCaptor.forClass(Object.class);
        verify(jdbcTemplate, org.mockito.Mockito.times(2)).queryForObject(
                eq("SELECT pg_try_advisory_lock(?, ?)"), eq(Boolean.class),
                org.mockito.ArgumentMatchers.any(), keyCaptor.capture());

        assertTrue(keyCaptor.getAllValues().get(0).equals(keyCaptor.getAllValues().get(1)),
                "the same season must always hash to the same lock key");
    }

    @Test
    void unlock_releasesTheAdvisoryLock() {
        when(jdbcTemplate.queryForObject(eq("SELECT pg_advisory_unlock(?, ?)"), eq(Boolean.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(true);

        lockService.unlock("20242025");

        verify(jdbcTemplate).queryForObject(eq("SELECT pg_advisory_unlock(?, ?)"), eq(Boolean.class),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
