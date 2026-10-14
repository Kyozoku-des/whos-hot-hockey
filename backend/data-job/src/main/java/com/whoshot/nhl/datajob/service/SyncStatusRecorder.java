package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.domain.entity.SyncStatus;
import com.whoshot.nhl.domain.repository.SyncStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;

/**
 * Stores when a stats sync last finished, for the site's "Stats last updated" line.
 * <p>
 * Only called once a sync has run to the end: a run-level failure (standings request, lost
 * database, stop request) throws before it, and a sync skipped for the season lock never calls it.
 * Record-level skips do not hold it back, since a skipped record keeps its previous valid row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SyncStatusRecorder {

    private final SyncStatusRepository syncStatusRepository;

    /** Records that the current-season stats sync has just finished. */
    public void statsSynced() {
        try {
            syncStatusRepository.save(new SyncStatus(SyncStatus.STATS, Instant.now()));
        } catch (RuntimeException e) {
            IngestionFailures.rethrowIfFatal(e);
            // The data is written either way; only the shown timestamp lags until the next sync.
            log.warn("Could not record the stats sync time: {}", e.getMessage());
        }
    }
}
