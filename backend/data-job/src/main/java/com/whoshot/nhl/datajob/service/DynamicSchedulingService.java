package com.whoshot.nhl.datajob.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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
    private final Set<String> pendingTeamGames = new HashSet<>();
    private int polls;
    private int failedPolls;

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
        Instant started = Instant.now();
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
                    log.info("[game-sync] Hourly full sync done in {}s; first game at {} UTC, next check in 1h",
                            secondsSince(started), firstGameTime);
                    schedule(this::hourlyCheck, Instant.now().plusSeconds(3600));
                } else {
                    Instant windowStart = start.isBefore(Instant.now()) ? Instant.now() : start;
                    log.info("[game-sync] Hourly full sync done in {}s; game window opens at {} (games {} - {} UTC)",
                            secondsSince(started), windowStart, firstGameTime, lastGameTime);
                    polls = 0;
                    failedPolls = 0;
                    schedule(this::syncAndReschedule, windowStart);
                }
                return;
            }
            log.info("[game-sync] Hourly full sync done in {}s; no games today, next check in 1h",
                    secondsSince(started));
        } catch (Exception e) {
            if (!stopping) log.error("[game-sync] Hourly synchronization failed; retrying next hour", e);
        }
        schedule(this::hourlyCheck, Instant.now().plusSeconds(3600));
    }

    private void syncAndReschedule() {
        if (stopping) return;
        Instant started = Instant.now();
        polls++;
        try {
            List<GameDto> activeGames = dataSyncService.getActiveGames();
            Set<String> activeTeams = activeGames.stream()
                    .flatMap(game -> Stream.of(game.getHomeTeam().getAbbrev(), game.getAwayTeam().getAbbrev()))
                    .collect(Collectors.toSet());
            // Teams active last poll but not now have just finished: record their completed game.
            Set<String> finishedTeams = new HashSet<>(previousActiveTeams);
            finishedTeams.removeAll(activeTeams);
            // Teams whose completed game failed to write stay pending and are retried every poll.
            pendingTeamGames.addAll(finishedTeams);
            Set<String> teamsToSync = new HashSet<>(previousActiveTeams);
            teamsToSync.addAll(activeTeams);
            teamsToSync.addAll(pendingTeamGames);
            int teams = 0, teamGames = 0, players = 0;
            if (!teamsToSync.isEmpty()) {
                teams = dataSyncService.syncTeamsForCodes(teamsToSync);
                // Before players, so their game logs resolve gameWon for the finished game.
                if (!pendingTeamGames.isEmpty()) {
                    Set<String> written = dataSyncService.syncTeamGamesForCodes(Set.copyOf(pendingTeamGames));
                    teamGames = written.size();
                    pendingTeamGames.removeAll(written);
                }
                players = dataSyncService.syncPlayersForTeams(teamsToSync);
            }
            // Retain participants through their first completed poll for final statistics.
            previousActiveTeams = Set.copyOf(activeTeams);
            log.info("[game-sync] Poll #{} in {}s: games [{}]; finished {}; updated {} teams, {} team schedules, {} players",
                    polls, secondsSince(started), describe(activeGames), finishedTeams, teams, teamGames, players);
            if (lastGameTime != null && LocalDateTime.now(ZoneOffset.UTC).isAfter(lastGameTime)
                    && activeTeams.isEmpty()) {
                // Also covers a game that completed entirely between two polls.
                Instant finalStarted = Instant.now();
                dataSyncService.syncTeams();
                dataSyncService.syncPlayers();
                // The full sync rewrites every team's completed games.
                pendingTeamGames.clear();
                log.info("[game-sync] Game window closed after {} polls ({} failed); final full sync done in {}s, next check in 1h",
                        polls, failedPolls, secondsSince(finalStarted));
                schedule(this::hourlyCheck, Instant.now().plusSeconds(3600));
                return;
            }
            if (!pendingTeamGames.isEmpty()) {
                log.warn("[game-sync] Poll #{}: completed games not yet written for {}; retrying next poll",
                        polls, pendingTeamGames);
            }
        } catch (Exception e) {
            failedPolls++;
            if (!stopping) log.error("[game-sync] Poll #{} failed; retrying next minute", polls, e);
        }
        // Delay from completion, avoiding catch-up loops when ingestion takes over a minute.
        schedule(this::syncAndReschedule, Instant.now().plusSeconds(60));
    }

    /** Renders games as {@code "AWAY 1-2 HOME LIVE"} for the poll summary. */
    private static String describe(List<GameDto> games) {
        return games.stream()
                .map(game -> "%s %s-%s %s %s".formatted(
                        game.getAwayTeam().getAbbrev(), score(game.getAwayTeam()),
                        score(game.getHomeTeam()), game.getHomeTeam().getAbbrev(), game.getGameState()))
                .collect(Collectors.joining(", "));
    }

    private static Object score(GameDto.TeamInfo team) {
        return team.getScore() != null ? team.getScore() : "-";
    }

    private static long secondsSince(Instant started) {
        return Duration.between(started, Instant.now()).toSeconds();
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void stop() {
        stopping = true;
        if (syncTask != null) syncTask.cancel(true);
    }
}
