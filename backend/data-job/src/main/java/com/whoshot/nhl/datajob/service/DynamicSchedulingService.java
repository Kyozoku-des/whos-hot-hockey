package com.whoshot.nhl.datajob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

/** A single cancellable task chain prevents overlapping ingestion and pending game timers. */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test & !initial-load & !backfill")
public class DynamicSchedulingService {
    private final TaskScheduler taskScheduler;
    private final DataSyncService dataSyncService;
    private ScheduledFuture<?> syncTask;
    private volatile boolean stopping;
    private LocalDateTime lastGameTime;
    private Set<String> previousActiveTeams = Set.of();

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        schedule(this::hourlyCheck, Instant.now());
    }

    private synchronized void schedule(Runnable work, Instant when) {
        if (!stopping) {
            syncTask = taskScheduler.schedule(work, when);
        }
    }

    private void hourlyCheck() {
        if (stopping) return;
        try {
            // Refresh season as well as schedule so a daemon survives season rollover.
            dataSyncService.initialize();
            dataSyncService.syncTeams();
            dataSyncService.syncPlayers();
            LocalDateTime firstGameTime = dataSyncService.getFirstGameTimeForToday();
            lastGameTime = dataSyncService.getLastGameTimeForToday();
            if (firstGameTime != null) {
                Instant start = firstGameTime.minusMinutes(5).toInstant(ZoneOffset.UTC);
                if (start.isAfter(Instant.now().plusSeconds(3600))) {
                    schedule(this::hourlyCheck, Instant.now().plusSeconds(3600));
                } else {
                    schedule(this::syncAndReschedule, start.isBefore(Instant.now()) ? Instant.now() : start);
                }
                return;
            }
        } catch (Exception e) {
            if (!stopping) log.error("Hourly synchronization failed; retrying next hour", e);
        }
        schedule(this::hourlyCheck, Instant.now().plusSeconds(3600));
    }

    private void syncAndReschedule() {
        if (stopping) return;
        try {
            Set<String> activeTeams = dataSyncService.getActiveGameTeamCodes();
            Set<String> teamsToSync = new HashSet<>(previousActiveTeams);
            teamsToSync.addAll(activeTeams);
            if (!teamsToSync.isEmpty()) {
                dataSyncService.syncTeamsForCodes(teamsToSync);
                dataSyncService.syncPlayersForTeams(teamsToSync);
            }
            // Retain participants through their first completed poll for final statistics.
            previousActiveTeams = Set.copyOf(activeTeams);
            if (lastGameTime != null && LocalDateTime.now(ZoneOffset.UTC).isAfter(lastGameTime)
                    && activeTeams.isEmpty()) {
                // Also covers a game that completed entirely between two polls.
                dataSyncService.syncTeams();
                dataSyncService.syncPlayers();
                schedule(this::hourlyCheck, Instant.now().plusSeconds(3600));
                return;
            }
        } catch (Exception e) {
            if (!stopping) log.error("Game-time synchronization failed; retrying next minute", e);
        }
        // Delay from completion, avoiding catch-up loops when ingestion takes over a minute.
        schedule(this::syncAndReschedule, Instant.now().plusSeconds(60));
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void stop() {
        stopping = true;
        if (syncTask != null) syncTask.cancel(true);
    }
}
