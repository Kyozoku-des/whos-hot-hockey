package com.whoshot.nhl.datajob.service;

import org.mockito.quality.Strictness;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

/** {@link BackfillLockService} stand-ins for tests that run without a database. */
final class TestSeasonLocks {

    private TestSeasonLocks() {
    }

    /** A lock that is always free: work runs as if no other writer existed. */
    static BackfillLockService available() {
        return lock(true);
    }

    /** A lock always held by another writer: guarded work never runs. */
    static BackfillLockService heldElsewhere() {
        return lock(false);
    }

    private static BackfillLockService lock(boolean available) {
        BackfillLockService lock = mock(BackfillLockService.class, withSettings().strictness(Strictness.LENIENT));
        when(lock.tryLock(any())).thenReturn(available);
        try {
            doCallRealMethod().when(lock).runExclusively(any(), any());
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        return lock;
    }
}
