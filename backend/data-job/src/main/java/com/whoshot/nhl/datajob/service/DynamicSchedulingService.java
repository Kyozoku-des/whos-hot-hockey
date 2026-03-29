package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/**
 * Long-running daemon that manages runtime scheduling of synchronization jobs based on daily game windows.
 * Operates in two modes: hourly check (default) and frequent sync (during game windows).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")
public class DynamicSchedulingService {
    private final TaskScheduler taskScheduler;
    private final DataSyncService dataSyncService;

    private ScheduledFuture<?> frequentSyncTask;
    private boolean frequentSyncActive = false;

    /**
     * Initializes the daemon at application startup.
     * Runs an initial sync, then schedules an hourly check for game-day transitions.
     */
    @PostConstruct
    public void init() {
        log.info("Initializing dynamic scheduling daemon");

        // Run initial sync
        try {
            dataSyncService.syncTeams();
            dataSyncService.syncPlayers();
        } catch (PlayerStatisticsException e) {
            log.error("Error during initial sync: {}", e.getMessage(), e);
        } catch (Exception e) {
            log.error("Unexpected error during initial sync: {}", e.getMessage(), e);
        }

        // Check if games are on today and start frequent sync if needed
        hourlyCheck();

        // Schedule hourly checks for game-day transitions
        taskScheduler.scheduleAtFixedRate(this::hourlyCheck, Duration.ofHours(1));
        log.info("Hourly check scheduled");
    }

    /**
     * Periodic check that refreshes today's game schedule and transitions between sync modes.
     * If games are scheduled today and frequent sync is not active, starts frequent sync.
     * If no games today, runs a single sync and remains in hourly mode.
     */
    private void hourlyCheck() {
        try {
            log.info("Running hourly schedule check");
            dataSyncService.setFirstAndLastGameTimesForToday();

            LocalDateTime firstGameTime = dataSyncService.getFirstGameTimeForToday();

            if (firstGameTime != null) {
                if (!frequentSyncActive) {
                    log.info("Games detected for today. Scheduling frequent sync to start at {}",
                            firstGameTime.minusMinutes(5));
                    LocalDateTime startFrequentSyncTime = firstGameTime.minusMinutes(5);
                    taskScheduler.schedule(this::startFrequentSync, startFrequentSyncTime.toInstant(ZoneOffset.UTC));
                } else {
                    log.info("Games detected for today but frequent sync is already active");
                }
            } else {
                log.info("No games scheduled for today. Running one sync and remaining in hourly mode.");
                try {
                    dataSyncService.syncTeams();
                    dataSyncService.syncPlayers();
                } catch (PlayerStatisticsException e) {
                    log.error("Error during sync in hourly check: {}", e.getMessage(), e);
                }
            }
        } catch (Exception e) {
            log.error("Error during hourly check, will retry next hour: {}", e.getMessage(), e);
        }
    }

    /**
     * Starts fixed-rate synchronization at one-minute intervals.
     * When all games finish, cancels frequent sync and returns to hourly mode.
     */
    private void startFrequentSync() {
        log.info("Entering frequent sync mode");
        frequentSyncActive = true;

        LocalDateTime lastGameTime = dataSyncService.getLastGameTimeForToday();

        frequentSyncTask = taskScheduler.scheduleAtFixedRate(() -> {
                    try {
                        Set<String> activeTeams = dataSyncService.getActiveGameTeamCodes();

                        if (activeTeams.isEmpty()) {
                            log.info("No active games right now, skipping scoped sync");
                        } else {
                            log.info("Syncing {} teams with active games: {}", activeTeams.size(), activeTeams);
                            dataSyncService.syncTeamsForCodes(activeTeams);
                            dataSyncService.syncPlayersForTeams(activeTeams);
                        }

                        // Only check for game completion after last game should have started
                        if (lastGameTime != null && LocalDateTime.now(ZoneOffset.UTC).isAfter(lastGameTime)) {
                            boolean isAnyGameActive = dataSyncService.isAnyGameActive();
                            if (!isAnyGameActive) {
                                log.info("No active games detected. Stopping frequent sync, returning to hourly mode.");
                                if (frequentSyncTask != null) {
                                    frequentSyncTask.cancel(false);
                                }
                                frequentSyncActive = false;
                            }
                        }
                    } catch (PlayerStatisticsException e) {
                        log.error("Error during frequent player sync: {}", e.getMessage(), e);
                    } catch (Exception e) {
                        log.error("Unexpected error during frequent sync, will retry next interval: {}", e.getMessage(), e);
                    }
                }, Duration.ofMinutes(1)
        );
    }
}
