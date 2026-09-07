package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ScheduledFuture;

/** Runs ingestion outside Spring's startup locks and cancels work during shutdown. */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")
public class DynamicSchedulingService {
    private final TaskScheduler taskScheduler;
    private final DataSyncService dataSyncService;
    private final ConfigurableApplicationContext applicationContext;

    private ScheduledFuture<?> syncTask;
    private volatile boolean stopping;
    private LocalDateTime lastGameTime;

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        schedule(this::initializeAndSchedule, Instant.now());
    }

    private synchronized void schedule(Runnable work, Instant when) {
        if (!stopping) {
            syncTask = taskScheduler.schedule(work, when);
        }
    }

    private void initializeAndSchedule() {
        try {
            dataSyncService.initialize();
            if (stopping) {
                return;
            }
            LocalDateTime firstGameTime = dataSyncService.getFirstGameTimeForToday();
            if (firstGameTime == null) {
                log.info("No games scheduled for today. Running one sync and exiting.");
                dataSyncService.syncPlayers();
                closeApplication();
                return;
            }
            lastGameTime = dataSyncService.getLastGameTimeForToday();
            schedule(this::syncAndReschedule, firstGameTime.minusMinutes(5).toInstant(ZoneOffset.UTC));
        } catch (Exception e) {
            if (!stopping) {
                log.error("Data job initialization or initial sync failed", e);
                closeApplication();
            }
        }
    }

    private void syncAndReschedule() {
        if (stopping) {
            return;
        }
        try {
            dataSyncService.syncPlayers();
            if (stopping) {
                return;
            }
            if (lastGameTime != null && LocalDateTime.now(ZoneOffset.UTC).isAfter(lastGameTime)
                    && !dataSyncService.isAnyGameActive()) {
                log.info("No active games detected. Shutting down application.");
                closeApplication();
                return;
            }
        } catch (PlayerStatisticsException | RuntimeException e) {
            if (!stopping) {
                log.error("Error during player sync", e);
            }
        }
        // Delay from completion so a slow sync cannot trigger a catch-up loop.
        schedule(this::syncAndReschedule, Instant.now().plusSeconds(60));
    }

    private void closeApplication() {
        if (!stopping) {
            // Close outside the scheduler so shutdown never waits for its own worker.
            Thread.ofPlatform().name("data-job-shutdown").start(applicationContext::close);
        }
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void stop() {
        stopping = true;
        if (syncTask != null) {
            syncTask.cancel(true);
        }
    }
}
