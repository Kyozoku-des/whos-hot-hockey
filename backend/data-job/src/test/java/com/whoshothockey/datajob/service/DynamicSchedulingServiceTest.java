package com.whoshothockey.datajob.service;

import com.whoshothockey.datajob.dto.nhlapi.GameDto;
import com.whoshothockey.datajob.dto.nhlapi.GameState;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DynamicSchedulingServiceTest {
    @Test
    void preGameChecksKeepOnlyOnePendingTaskAndShutdownCancelsIt() throws Exception {
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var pending = mock(ScheduledFuture.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return pending;
        });
        when(sync.getNextGameStart()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusHours(5));
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class), mock(SyncStatusRecorder.class));
        daemon.init();
        for (int i = 0; i < 3; i++) {
            assertEquals(1, tasks.size());
            tasks.remove().run();
        }
        assertEquals(1, tasks.size());
        daemon.stop();
        verify(pending).cancel(true);
        tasks.remove().run();
        assertTrue(tasks.isEmpty());
        verify(sync, times(3)).syncPlayers();
    }

    @Test
    void completedTeamsReceiveFinalRefreshBeforeReturningToHourlyChecks() throws Exception {
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        // A live game opens the window; once it has ended no other game is scheduled.
        when(sync.getNextGameStart()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusHours(3), (LocalDateTime) null);
        Set<String> teams = Set.of("EDM", "VAN");
        when(sync.getActiveGames()).thenReturn(List.of(game("VAN", "EDM")), List.of());
        var syncStatus = mock(SyncStatusRecorder.class);
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class), syncStatus);
        daemon.init();
        tasks.remove().run();
        tasks.remove().run();
        tasks.remove().run();
        // Hourly full sync, both polls and the final full sync each finished a stats sync.
        verify(syncStatus, times(4)).statsSynced();
        verify(sync, times(2)).syncTeamsForCodes(teams);
        verify(sync, times(2)).syncPlayersForTeams(teams);
        // Every poll keeps the next-game state current while the game is live.
        verify(sync).syncActiveNextGames(List.of(game("VAN", "EDM")));
        verify(sync).syncActiveNextGames(List.of());
        // Only once the game has ended, and before its players so their gameWon resolves.
        var order = inOrder(sync);
        order.verify(sync).syncTeamGamesForCodes(teams);
        order.verify(sync).syncPlayersForTeams(teams);
        verify(sync, times(1)).syncTeamGamesForCodes(any());
        verify(sync, times(2)).syncPlayers();
        assertEquals(1, tasks.size());
        daemon.stop();
    }

    @Test
    void windowClosesAfterLastNightsGamesWhenTonightsStartOnTheSameUtcDate() throws Exception {
        // 2026-10-17: last night's late games run 01:00-04:42 UTC, tonight's start at 17:00 UTC.
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        when(sync.getNextGameStart()).thenReturn(
                LocalDateTime.now(ZoneOffset.UTC).minusHours(2), LocalDateTime.now(ZoneOffset.UTC).plusHours(12));
        when(sync.getActiveGames()).thenReturn(List.of(game("BOS", "ANA")), List.of());
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class), mock(SyncStatusRecorder.class));
        daemon.init();
        tasks.remove().run(); // hourly check opens the window
        tasks.remove().run(); // game live
        tasks.remove().run(); // game ended: final full sync, window closes
        verify(sync, times(2)).syncPlayers();
        tasks.remove().run(); // an hourly full sync, not another poll
        verify(sync, times(3)).syncPlayers();
        verify(sync, times(2)).getActiveGames();
        assertEquals(1, tasks.size());
        daemon.stop();
    }

    @Test
    void fullSyncsNeverDelayTheNextWindowOpening() {
        // A game ends at 20:50 and the next starts 66 minutes to 3 hours later, so the window closes in
        // between. Neither the final full sync nor a following hourly one may push the first poll of
        // the next window past its opening, 5 minutes before the game.
        for (int gap = 66; gap <= 180; gap++) {
            for (int syncMinutes : new int[] {3, 7, 20}) {
                LocalDateTime nextGame = LocalDateTime.parse("2026-10-17T20:50:00").plusMinutes(gap);
                assertEquals(nextGame.minusMinutes(5).toInstant(ZoneOffset.UTC),
                        firstPollOfNextWindow(nextGame, Duration.ofMinutes(syncMinutes)),
                        "next game " + gap + " min after the last ended, full sync " + syncMinutes + " min");
            }
        }
    }

    /**
     * Runs the daemon, with every full sync taking {@code fullSync}, through a game that ends at
     * 20:50 and on to the first poll of the next window.
     *
     * @return when that poll started
     */
    private static Instant firstPollOfNextWindow(LocalDateTime nextGame, Duration fullSync) {
        var clock = new MutableClock(Instant.parse("2026-10-17T19:00:00Z"));
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        var times = new ArrayDeque<Instant>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            times.add(invocation.getArgument(1));
            return mock(ScheduledFuture.class);
        });
        doAnswer(invocation -> {
            clock.advance(fullSync);
            return null;
        }).when(sync).syncPlayers();
        when(sync.getNextGameStart()).thenReturn(LocalDateTime.parse("2026-10-17T18:00:00"), nextGame);
        var polls = new AtomicInteger();
        when(sync.getActiveGames()).thenAnswer(invocation ->
                polls.incrementAndGet() == 1 ? List.of(game("NJD", "WSH")) : List.of());
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(),
                mock(ScoreboardSyncService.class), mock(SyncStatusRecorder.class), clock);
        daemon.init();
        runNext(tasks, times, clock); // hourly check opens the window for the live game
        runNext(tasks, times, clock); // game live
        clock.set(Instant.parse("2026-10-17T20:50:00Z"));
        runNext(tasks, times, clock); // game ended: final full sync, window closes
        for (int i = 0; i < 10; i++) {
            int pollsBefore = polls.get();
            Instant started = runNext(tasks, times, clock);
            if (polls.get() > pollsBefore) {
                daemon.stop();
                return started;
            }
        }
        throw new AssertionError("no poll after the window closed");
    }

    /** Runs the next scheduled task once its time has come; returns when it started. */
    private static Instant runNext(ArrayDeque<Runnable> tasks, ArrayDeque<Instant> times, MutableClock clock) {
        Instant due = times.remove();
        if (due.isAfter(clock.instant())) clock.set(due);
        Instant started = clock.instant();
        tasks.remove().run();
        return started;
    }

    @Test
    void failedCompletedGameWriteIsRetriedOnTheNextPoll() throws Exception {
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        // A game starting soon keeps the window open after this one ends.
        when(sync.getNextGameStart()).thenReturn(
                LocalDateTime.now(ZoneOffset.UTC).minusHours(3), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(30));
        Set<String> teams = Set.of("EDM", "VAN");
        when(sync.getActiveGames()).thenReturn(List.of(game("VAN", "EDM")), List.of());
        when(sync.syncTeamGamesForCodes(teams)).thenReturn(Set.of("EDM"));
        when(sync.syncTeamGamesForCodes(Set.of("VAN"))).thenReturn(Set.of("VAN"));
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class), mock(SyncStatusRecorder.class));
        daemon.init();
        tasks.remove().run(); // hourly check
        tasks.remove().run(); // game live
        tasks.remove().run(); // game ended: VAN write fails
        tasks.remove().run(); // VAN retried, its players refreshed again
        tasks.remove().run(); // nothing pending
        var order = inOrder(sync);
        order.verify(sync).syncTeamGamesForCodes(teams);
        order.verify(sync).syncTeamGamesForCodes(Set.of("VAN"));
        order.verify(sync).syncPlayersForTeams(Set.of("VAN"));
        verify(sync, times(2)).syncTeamGamesForCodes(any());
        daemon.stop();
    }

    @Test
    void anotherWriterOfTheSeason_blocksLiveWritesWithoutStoppingTheDaemon() throws Exception {
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        when(sync.getNextGameStart()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(sync.getActiveGames()).thenReturn(List.of(game("VAN", "EDM")));
        var syncStatus = mock(SyncStatusRecorder.class);
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.heldElsewhere(), mock(ScoreboardSyncService.class), syncStatus);
        daemon.init();
        tasks.remove().run(); // hourly check
        tasks.remove().run(); // game-time poll
        verify(syncStatus, never()).statsSynced();

        verify(sync, never()).syncTeams();
        verify(sync, never()).syncPlayers();
        verify(sync, never()).syncTeamsForCodes(any());
        verify(sync, never()).syncPlayersForTeams(any());
        assertEquals(1, tasks.size(), "the daemon keeps polling");
        daemon.stop();
    }

    @Test
    void failedFullSync_doesNotRecordStatsAsUpdated() throws Exception {
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        doThrow(new IllegalStateException("standings unavailable")).when(sync).syncPlayers();
        var syncStatus = mock(SyncStatusRecorder.class);
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class), syncStatus);
        daemon.init();
        tasks.remove().run();

        verify(syncStatus, never()).statsSynced();
        assertEquals(1, tasks.size(), "retried next hour");
        daemon.stop();
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        MutableClock(Instant now) {
            this.now = now;
        }

        void set(Instant instant) {
            now = instant;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static GameDto game(String away, String home) {
        var game = new GameDto();
        game.setGameState(GameState.LIVE);
        game.setAwayTeam(team(away, 1));
        game.setHomeTeam(team(home, 2));
        return game;
    }

    private static GameDto.TeamInfo team(String abbrev, int score) {
        var team = new GameDto.TeamInfo();
        team.setAbbrev(abbrev);
        team.setScore(score);
        return team;
    }
}
