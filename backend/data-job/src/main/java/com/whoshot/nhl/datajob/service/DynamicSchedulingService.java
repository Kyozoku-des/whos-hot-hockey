package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

/**
 * Manages runtime scheduling of synchronization jobs based on daily game windows.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")
public class DynamicSchedulingService {
    private final TaskScheduler taskScheduler;
    private final DataSyncService dataSyncService;
    private final ApplicationContext applicationContext;

    private ScheduledFuture<?> frequentSyncTask;
    private LocalDateTime lastGameTime;

    /**
     * Initializes scheduling at application startup.
     * If no games are scheduled, runs one synchronization and terminates the application.
     * Side effects: schedules recurring tasks and may exit the JVM.
     */
    @PostConstruct
    public void init() {
        // Check NHL API for today's game schedule
        LocalDateTime firstGameTime = dataSyncService.getFirstGameTimeForToday();

        if (firstGameTime == null) {
            log.info("No games scheduled for today. Running one sync and exiting.");
            try {
                dataSyncService.syncPlayers();
            } catch (PlayerStatisticsException e) {
                log.error(e.getMessage(), e);
            }

            exitApp();
            return;
        }

        this.lastGameTime = dataSyncService.getLastGameTimeForToday();
        LocalDateTime startFrequentSyncTime = firstGameTime.minusMinutes(5); // 5 minute buffer
        taskScheduler.schedule(this::startFrequentSync, startFrequentSyncTime.toInstant(ZoneOffset.UTC));
    }

    /**
     * Starts fixed-rate synchronization at one-minute intervals.
     * Side effect: registers a recurring task in the scheduler.
     */
    private void startFrequentSync() {
        log.info("Starting frequent sync");

        frequentSyncTask = taskScheduler.scheduleAtFixedRate(() -> {
                    try {
                        dataSyncService.syncPlayers();

                        // Only check for game completion after last game should have started
                        if (lastGameTime != null && LocalDateTime.now(ZoneOffset.UTC).isAfter(lastGameTime)) {
                            boolean isAnyGameActive = dataSyncService.isAnyGameActive();
                            if (!isAnyGameActive) {
                                log.info("No active games detected. Stopping frequent sync and scheduling app exit.");
                                if (frequentSyncTask != null) {
                                    frequentSyncTask.cancel(false);
                                }
                                exitApp();
                            }
                        }
                    } catch (PlayerStatisticsException e) {
                        log.error("Error during player sync: {}", e.getMessage(), e);
                    }
                }, Duration.ofMinutes(1)
        );
    }

    /**
     * Cancels scheduled tasks and exits the Spring application.
     * Side effect: terminates the JVM process.
     */
    private void exitApp() {
        if (frequentSyncTask != null) {
            frequentSyncTask.cancel(false);
        }

        log.info("Shutting down application.");
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
