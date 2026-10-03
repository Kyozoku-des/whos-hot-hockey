package com.whoshot.nhl.datajob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

/**
 * Performs a full initial load of season data by delegating to {@link DataSyncService}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InitialDataLoadService {

    private final DataSyncService dataSyncService;
    private final BackfillLockService seasonLock;

    /**
     * Loads all player data for the current season.
     * Record-level failures are skipped and reported by the sync itself; a run-level failure (for
     * example the standings request) is logged without rethrowing. A stop request propagates.
     */
    public void loadFullSeason() {
        Instant start = Instant.now();
        log.info("Initial full-season data load started at {}", start);

        try {
            dataSyncService.initialize();
            String seasonId = dataSyncService.getSeasonId();
            boolean ran = seasonLock.runExclusively(seasonId, () -> {
                log.info("Syncing team standings...");
                dataSyncService.syncTeams();

                log.info("Syncing player data...");
                dataSyncService.syncPlayers();
            });
            if (!ran) {
                log.error("Initial data load skipped: season {} is being written by another job", seasonId);
                return;
            }
        } catch (RuntimeException e) {
            IngestionFailures.rethrowIfFatal(e);
            log.error("Initial data load failed: {}", e.getMessage(), e);
            return;
        }

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);
        log.info("Initial full-season data load completed at {}. Total duration: {} seconds",
                end, duration.toSeconds());
    }
}
