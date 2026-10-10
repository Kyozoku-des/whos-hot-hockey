package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.TaskScheduler;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ScheduledFuture;

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
        when(sync.getFirstGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusHours(5));
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class));
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
        when(sync.getFirstGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusHours(3));
        when(sync.getLastGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        Set<String> teams = Set.of("EDM", "VAN");
        when(sync.getActiveGames()).thenReturn(List.of(game("VAN", "EDM")), List.of());
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class));
        daemon.init();
        tasks.remove().run();
        tasks.remove().run();
        tasks.remove().run();
        verify(sync, times(2)).syncTeamsForCodes(teams);
        verify(sync, times(2)).syncPlayersForTeams(teams);
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
    void failedCompletedGameWriteIsRetriedOnTheNextPoll() throws Exception {
        var sync = mock(DataSyncService.class);
        var scheduler = mock(TaskScheduler.class);
        var tasks = new ArrayDeque<Runnable>();
        when(scheduler.schedule(any(Runnable.class), any(Instant.class))).thenAnswer(invocation -> {
            tasks.add(invocation.getArgument(0));
            return mock(ScheduledFuture.class);
        });
        when(sync.getFirstGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusHours(3));
        // A later game keeps the window open after this one ends.
        when(sync.getLastGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusHours(1));
        Set<String> teams = Set.of("EDM", "VAN");
        when(sync.getActiveGames()).thenReturn(List.of(game("VAN", "EDM")), List.of());
        when(sync.syncTeamGamesForCodes(teams)).thenReturn(Set.of("EDM"));
        when(sync.syncTeamGamesForCodes(Set.of("VAN"))).thenReturn(Set.of("VAN"));
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.available(), mock(ScoreboardSyncService.class));
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
        when(sync.getFirstGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).minusHours(1));
        when(sync.getLastGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusHours(2));
        when(sync.getActiveGames()).thenReturn(List.of(game("VAN", "EDM")));
        var daemon = new DynamicSchedulingService(scheduler, sync, TestSeasonLocks.heldElsewhere(), mock(ScoreboardSyncService.class));
        daemon.init();
        tasks.remove().run(); // hourly check
        tasks.remove().run(); // game-time poll

        verify(sync, never()).syncTeams();
        verify(sync, never()).syncPlayers();
        verify(sync, never()).syncTeamsForCodes(any());
        verify(sync, never()).syncPlayersForTeams(any());
        assertEquals(1, tasks.size(), "the daemon keeps polling");
        daemon.stop();
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
