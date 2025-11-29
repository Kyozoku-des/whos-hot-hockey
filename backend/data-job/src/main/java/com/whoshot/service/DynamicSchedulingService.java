package com.whoshot.service;

import com.whoshot.exception.PlayerStatisticsException;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class DynamicSchedulingService {
    private final TaskScheduler taskScheduler;
    private final NhlApiService nhlApiService;
    private final DataSyncService dataSyncService;
    private final ApplicationContext applicationContext;

    private ScheduledFuture<?> frequentSyncTask;
    private ScheduledFuture<?> exitAppTask;

    @PostConstruct
    public void init() {
        // Check NHL API for today's game schedule
        LocalDateTime firstGameTime = null;
        LocalDateTime lastGameTime = null;

        if (firstGameTime == null) {
            log.info("No games scheduled for today. Running one sync and exiting.");
            try {
                dataSyncService.syncPlayer();
            } catch (PlayerStatisticsException e) {
                log.error(e.getMessage(), e);
            }
            exitApp();
            return;
        }

        LocalDateTime startFrequentSyncTime = firstGameTime.minusMinutes(5); // 5 minute buffer
        LocalDateTime appEndTime = lastGameTime.plusMinutes(10); // 10 minute buffer

        taskScheduler.schedule(this::startFrequentSync, startFrequentSyncTime.toInstant(ZoneOffset.UTC));
        taskScheduler.schedule(this::exitApp, appEndTime.toInstant(ZoneOffset.UTC));
    }

    private void startFrequentSync() {
        log.info("Starting frequent sync");
        frequentSyncTask = taskScheduler.scheduleAtFixedRate(() -> {
                    try {
                        dataSyncService.syncPlayer();
                    } catch (PlayerStatisticsException e) {
                        throw new RuntimeException(e);
                    }
                }, Duration.ofMinutes(1)
        );
    }

    private void exitApp() {
        if (frequentSyncTask != null) {
            frequentSyncTask.cancel(false);
        }
        if (exitAppTask != null) {
            exitAppTask.cancel(false);
        }
        log.info("Shutting down application.");
        System.exit(SpringApplication.exit(applicationContext, () -> 0));
    }
}
