package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
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

    /**
     * Loads all player data for the current season.
     * Catches and logs any {@link PlayerStatisticsException} without rethrowing,
     * so callers are not forced to handle API or validation failures.
     */
    public void loadFullSeason() {
        Instant start = Instant.now();
        log.info("Initial full-season data load started at {}", start);

        try {
            log.info("Syncing team standings...");
            dataSyncService.syncTeams();

            log.info("Syncing player data...");
            dataSyncService.syncPlayers();
        } catch (PlayerStatisticsException e) {
            log.error("Initial data load failed due to player statistics error: {}", e.getMessage(), e);
            return;
        }

        Instant end = Instant.now();
        Duration duration = Duration.between(start, end);
        log.info("Initial full-season data load completed at {}. Total duration: {} seconds",
                end, duration.toSeconds());
    }
}
