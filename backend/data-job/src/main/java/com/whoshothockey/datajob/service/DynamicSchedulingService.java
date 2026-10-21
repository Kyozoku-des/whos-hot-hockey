package com.whoshothockey.datajob.service;

import com.whoshothockey.datajob.dto.nhlapi.GameDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Service;

import java.time.Clock;
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
@Profile("!test & !initial-load & !backfill")
public class DynamicSchedulingService {
    private final TaskScheduler taskScheduler;
    private final DataSyncService dataSyncService;
    private final BackfillLockService seasonLock;
    private final ScoreboardSyncService scoreboardSyncService;
    private final SyncStatusRecorder syncStatus;
    private final Clock clock;
    private ScheduledFuture<?> syncTask;
    private volatile boolean stopping;
    private Set<String> previousActiveTeams = Set.of();
    private final Set<String> pendingTeamGames = new HashSet<>();
    private int polls;
    private int failedPolls;

    @Autowired
    public DynamicSchedulingService(TaskScheduler taskScheduler, DataSyncService dataSyncService,
                                    BackfillLockService seasonLock, ScoreboardSyncService scoreboardSyncService,
                                    SyncStatusRecorder syncStatus) {
        this(taskScheduler, dataSyncService, seasonLock, scoreboardSyncService, syncStatus, Clock.systemUTC());
    }

    DynamicSchedulingService(TaskScheduler taskScheduler, DataSyncService dataSyncService,
                             BackfillLockService seasonLock, ScoreboardSyncService scoreboardSyncService,
                             SyncStatusRecorder syncStatus, Clock clock) {
        this.taskScheduler = taskScheduler;
        this.dataSyncService = dataSyncService;
        this.seasonLock = seasonLock;
        this.scoreboardSyncService = scoreboardSyncService;
        this.syncStatus = syncStatus;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        schedule(this::hourlyCheck, clock.instant());
    }

    private synchronized void schedule(Runnable work, Instant when) {
        if (!stopping) {
            syncTask = taskScheduler.schedule(work, when);
        }
    }

    private void hourlyCheck() {
        if (stopping) return;
        Instant started = clock.instant();
        try {
            // Refresh season as well as schedule so a daemon survives season rollover.
            dataSyncService.initialize();
            String seasonId = dataSyncService.getSeasonId();
            if (seasonLock.runExclusively(seasonId, () -> {
                dataSyncService.syncTeams();
                dataSyncService.syncPlayers();
            })) {
                syncStatus.statsSynced();
            } else {
                log.warn("[game-sync] Season {} is being written by another job; skipping this full sync", seasonId);
            }
            scoreboardSyncService.syncScoreboard(true);
            LocalDateTime nextGame = dataSyncService.getNextGameStart();
            String next = scheduleUntilWindow(nextGame);
            log.info("[game-sync] Hourly full sync done in {}s; {}, {}", secondsSince(started),
                    nextGame == null ? "no upcoming games" : "next game at " + nextGame + " UTC", next);
            return;
        } catch (Exception e) {
            if (!stopping) log.error("[game-sync] Hourly synchronization failed; retrying next hour", e);
        }
        schedule(this::hourlyCheck, clock.instant().plusSeconds(3600));
    }

    private void syncAndReschedule() {
        if (stopping) return;
        Instant started = clock.instant();
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
            int[] counts = new int[3]; // teams, team schedules, players
            if (!teamsToSync.isEmpty() && !seasonLock.runExclusively(dataSyncService.getSeasonId(), () -> {
                counts[0] = dataSyncService.syncTeamsForCodes(teamsToSync);
                dataSyncService.syncActiveNextGames(activeGames);
                // Before players, so their game logs resolve gameWon for the finished game.
                if (!pendingTeamGames.isEmpty()) {
                    Set<String> written = dataSyncService.syncTeamGamesForCodes(Set.copyOf(pendingTeamGames));
                    counts[1] = written.size();
                    pendingTeamGames.removeAll(written);
                }
                counts[2] = dataSyncService.syncPlayersForTeams(teamsToSync);
            })) {
                log.warn("[game-sync] Poll #{}: season is being written by another job; retrying next minute", polls);
                schedule(this::syncAndReschedule, clock.instant().plusSeconds(60));
                return;
            }
            if (!teamsToSync.isEmpty()) {
                // Teams not playing have no new games, so their stats are current as of this poll too.
                syncStatus.statsSynced();
            }
            scoreboardSyncService.syncScoreboard(false);
            // Retain participants through their first completed poll for final statistics.
            previousActiveTeams = Set.copyOf(activeTeams);
            log.info("[game-sync] Poll #{} in {}s: games [{}]; finished {}; updated {} teams, {} team schedules, {} players",
                    polls, secondsSince(started), describe(activeGames), finishedTeams, counts[0], counts[1], counts[2]);
            LocalDateTime nextGame = dataSyncService.getNextGameStart();
            if (activeTeams.isEmpty() && !windowOpensWithinAnHour(nextGame)) {
                // No game is live and the next starts later, typically on the next game night: close
                // the window so hourly full syncs resume. Also covers a game that completed entirely
                // between two polls.
                Instant finalStarted = clock.instant();
                if (seasonLock.runExclusively(dataSyncService.getSeasonId(), () -> {
                    dataSyncService.syncTeams();
                    dataSyncService.syncPlayers();
                })) {
                    // The full sync rewrites every team's completed games.
                    pendingTeamGames.clear();
                    syncStatus.statsSynced();
                } else {
                    log.warn("[game-sync] Season is being written by another job; the next hourly sync catches up");
                }
                scoreboardSyncService.syncScoreboard(true);
                int windowPolls = polls;
                int windowFailures = failedPolls;
                long finalSeconds = secondsSince(finalStarted);
                String next = scheduleUntilWindow(nextGame);
                log.info("[game-sync] Game window closed after {} polls ({} failed); final full sync done in {}s, {}",
                        windowPolls, windowFailures, finalSeconds, next);
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
        schedule(this::syncAndReschedule, clock.instant().plusSeconds(60));
    }

    /**
     * Schedules the next hourly check, or the first poll when the game window opens before that
     * check would. Decided after the preceding full sync has finished, so a slow sync cannot push the
     * first poll past the opening. A check is never scheduled within the hour before an opening: it
     * moves earlier, to an hour before, so its full sync finishes before the window opens.
     *
     * @return what was scheduled, for the log line
     */
    private String scheduleUntilWindow(LocalDateTime nextGame) {
        Instant now = clock.instant();
        if (!windowOpensWithinAnHour(nextGame)) {
            Instant nextCheck = now.plusSeconds(3600);
            if (nextGame != null && windowOpening(nextGame).minusSeconds(3600).isBefore(nextCheck)) {
                nextCheck = windowOpening(nextGame).minusSeconds(3600);
                schedule(this::hourlyCheck, nextCheck);
                return "next check at " + nextCheck + ", an hour before the game window opens";
            }
            schedule(this::hourlyCheck, nextCheck);
            return "next check in 1h";
        }
        Instant opening = windowOpening(nextGame);
        Instant windowStart = opening.isBefore(now) ? now : opening;
        polls = 0;
        failedPolls = 0;
        schedule(this::syncAndReschedule, windowStart);
        return "game window opens at " + windowStart;
    }

    /** The game window opens 5 minutes before a game starts. */
    private static Instant windowOpening(LocalDateTime gameStart) {
        return gameStart.minusMinutes(5).toInstant(ZoneOffset.UTC);
    }

    private boolean windowOpensWithinAnHour(LocalDateTime gameStart) {
        return gameStart != null && !windowOpening(gameStart).isAfter(clock.instant().plusSeconds(3600));
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

    private long secondsSince(Instant started) {
        return Duration.between(started, clock.instant()).toSeconds();
    }

    @EventListener(ContextClosedEvent.class)
    public synchronized void stop() {
        stopping = true;
        if (syncTask != null) syncTask.cancel(true);
    }
}
