package com.whoshothockey.datajob.service;

import com.whoshothockey.datajob.dto.SeasonDto;
import com.whoshothockey.datajob.dto.nhlapi.GameDto;
import com.whoshothockey.datajob.dto.nhlapi.GameState;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataSyncServiceTest {
    private final SeasonDto previous = season("20242025", "2024-10-04T13:00:00", "2025-04-17T21:30:00");
    private final SeasonDto current = season("20252026", "2025-10-07T17:00:00", "2026-04-17T00:00:00");
    private final SeasonDto upcoming = season("20262027", "2026-09-29T00:00:00", "2027-04-10T00:00:00");

    @Test
    void offseasonKeepsMostRecentStartedSeasonRegardlessOfApiOrder() {
        var now = LocalDateTime.parse("2026-09-07T18:05:00");
        assertSame(current, DataSyncService.selectSeason(List.of(previous, current, upcoming), now));
        assertSame(current, DataSyncService.selectSeason(List.of(upcoming, current, previous), now));
    }

    @Test
    void selectsSeasonDuringRegularSeasonAndPlayoffs() {
        for (String date : List.of("2026-01-01T00:00:00", "2026-04-17T00:00:00", "2026-06-01T00:00:00")) {
            assertSame(current, DataSyncService.selectSeason(
                    List.of(upcoming, previous, current), LocalDateTime.parse(date)));
        }
    }

    @Test
    void switchesAtSeasonStartInclusively() {
        var seasons = List.of(current, upcoming);
        assertSame(current, DataSyncService.selectSeason(seasons, upcoming.getStartDate().minusSeconds(1)));
        assertSame(upcoming, DataSyncService.selectSeason(seasons, upcoming.getStartDate()));
    }

    @Test
    void failsClearlyWhenNoSeasonHasStarted() {
        var now = LocalDateTime.parse("2026-09-07T18:05:00");
        assertThrows(IllegalStateException.class, () -> DataSyncService.selectSeason(List.of(), now));
        assertThrows(IllegalStateException.class, () -> DataSyncService.selectSeason(List.of(upcoming), now));
    }

    @Test
    void ignoresEntriesWithoutStartDates() {
        assertSame(current, DataSyncService.selectSeason(
                List.of(new SeasonDto(), current), LocalDateTime.parse("2026-09-07T18:05:00")));
    }

    /** The 2026-10-16 and 2026-10-17 game nights; both have games on the UTC date 2026-10-17. */
    private static final List<GameDto> TWO_GAME_NIGHTS = List.of(
            game("2026-10-17T01:00:00Z", GameState.OFF),
            game("2026-10-17T02:00:00Z", GameState.OFF),
            game("2026-10-17T17:00:00Z", GameState.FUT),
            game("2026-10-17T23:00:00Z", GameState.FUT),
            game("2026-10-18T02:00:00Z", GameState.FUT));

    @Test
    void nextGameSkipsLastNightsFinishedGames() {
        assertEquals(LocalDateTime.parse("2026-10-17T17:00:00"),
                DataSyncService.nextGameStart(TWO_GAME_NIGHTS, LocalDateTime.parse("2026-10-17T04:43:00")));
    }

    @Test
    void nextGameIsNotLimitedToTheUtcDate() {
        var games = List.of(game("2026-10-17T02:00:00Z", GameState.OFF), game("2026-10-18T22:00:00Z", GameState.FUT));
        assertEquals(LocalDateTime.parse("2026-10-18T22:00:00"),
                DataSyncService.nextGameStart(games, LocalDateTime.parse("2026-10-17T04:43:00")));
    }

    @Test
    void nextGameKeepsGamesInProgressOrWithoutState() {
        var games = List.of(
                game("2026-10-15T00:30:00Z", GameState.LIVE),
                game("2026-10-15T01:00:00Z", GameState.OVER),
                game("2026-10-15T02:00:00Z", null));

        assertEquals(LocalDateTime.parse("2026-10-15T00:30:00"),
                DataSyncService.nextGameStart(games, LocalDateTime.parse("2026-10-15T03:30:00")));
        assertEquals(LocalDateTime.parse("2026-10-15T02:00:00"),
                DataSyncService.nextGameStart(games.subList(1, 3), LocalDateTime.parse("2026-10-15T02:30:00")));
    }

    @Test
    void nextGameIgnoresGamesThatNeverStarted() {
        var games = List.of(game("2026-10-15T00:30:00Z", GameState.FUT), game("2026-10-16T23:00:00Z", GameState.FUT));
        assertEquals(LocalDateTime.parse("2026-10-15T00:30:00"),
                DataSyncService.nextGameStart(games, LocalDateTime.parse("2026-10-15T01:00:00")));
        assertEquals(LocalDateTime.parse("2026-10-16T23:00:00"),
                DataSyncService.nextGameStart(games, LocalDateTime.parse("2026-10-15T01:31:00")));
    }

    @Test
    void noNextGameWhenAllHaveFinished() {
        assertNull(DataSyncService.nextGameStart(TWO_GAME_NIGHTS.subList(0, 2), LocalDateTime.parse("2026-10-17T04:43:00")));
    }

    private static GameDto game(String startTimeUTC, GameState state) {
        var game = new GameDto();
        game.setStartTimeUTC(startTimeUTC);
        game.setGameState(state);
        return game;
    }

    private static SeasonDto season(String id, String start, String end) {
        var season = new SeasonDto();
        season.setId(id);
        season.setStartDate(LocalDateTime.parse(start));
        season.setRegularSeasonEndDate(LocalDateTime.parse(end));
        return season;
    }
}
