package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.model.TeamGameIndex;
import com.whoshot.nhl.domain.entity.GameLog;
import com.whoshot.nhl.domain.entity.TeamGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GameLogWriter}'s player-game-log mapping (data-model.md {@code game_logs}).
 */
class GameLogMapperTest {

    private PlayerGameLogDto gameLog(long gameId, String date, String opponent, String homeRoadFlag,
                                      int goals, int assists, int points, String toi) {
        PlayerGameLogDto dto = new PlayerGameLogDto();
        dto.setGameId(gameId);
        dto.setGameDate(date);
        dto.setOpponentAbbrev(opponent);
        dto.setHomeRoadFlag(homeRoadFlag);
        dto.setGoals(goals);
        dto.setAssists(assists);
        dto.setPoints(points);
        dto.setPlusMinus(0);
        dto.setShots(3);
        dto.setToi(toi);
        return dto;
    }

    @Test
    void toiParsedToSeconds() {
        assertEquals(21 * 60 + 5, GameLogWriter.toiSeconds("21:05"));
        assertEquals(0, GameLogWriter.toiSeconds(null));
    }

    @Test
    void malformedToi_isRejectedAsInvalidRecord_notNumberFormatException() {
        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GameLogWriter.toiSeconds("21:xx"));
        assertTrue(e.getMessage().contains("21:xx"), e.getMessage());
    }

    @Test
    void validatePlayerGameLogs_namesTheBadGame() {
        List<PlayerGameLogDto> logs = List.of(
                gameLog(1L, "2025-01-01", "MTL", "H", 0, 0, 0, "18:00"),
                gameLog(2L, "2025-01-03", "TOR", "R", 0, 0, 0, "bad"));

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
                () -> GameLogWriter.validatePlayerGameLogs(logs));
        assertTrue(e.getMessage().contains("game 2"), e.getMessage());
    }

    @Test
    void homeGameDerivedFromHomeRoadFlag() {
        PlayerGameLogDto home = gameLog(1L, "2025-01-15", "MTL", "H", 1, 1, 2, "18:30");
        PlayerGameLogDto away = gameLog(2L, "2025-01-16", "MTL", "R", 0, 1, 1, "17:00");

        GameLog mappedHome = GameLogWriter.toGameLog(new GameLog(), 8478402L, "20242025", home, 1, null);
        GameLog mappedAway = GameLogWriter.toGameLog(new GameLog(), 8478402L, "20242025", away, 2, null);

        assertTrue(mappedHome.getHomeGame());
        assertFalse(mappedAway.getHomeGame());
    }

    @Test
    void chronological_reversesUpstreamMostRecentFirstOrder() {
        PlayerGameLogDto oldest = gameLog(1L, "2025-01-01", "MTL", "H", 0, 0, 0, "10:00");
        PlayerGameLogDto newest = gameLog(2L, "2025-02-01", "BOS", "R", 1, 0, 1, "12:00");

        // Upstream returns most-recent-first
        List<PlayerGameLogDto> chronological = GameLogWriter.chronological(List.of(newest, oldest));

        assertEquals(List.of(oldest, newest), chronological);
    }

    @Test
    void gameNumberIsDenseAndChronological() {
        PlayerGameLogDto first = gameLog(1L, "2025-01-01", "MTL", "H", 0, 0, 0, "10:00");
        PlayerGameLogDto second = gameLog(2L, "2025-02-01", "BOS", "R", 1, 0, 1, "12:00");
        List<PlayerGameLogDto> chronological = GameLogWriter.chronological(List.of(second, first));

        GameLog mappedFirst = GameLogWriter.toGameLog(new GameLog(), 1L, "20242025", chronological.get(0), 1, null);
        GameLog mappedSecond = GameLogWriter.toGameLog(new GameLog(), 1L, "20242025", chronological.get(1), 2, null);

        assertEquals(1, mappedFirst.getGameNumber());
        assertEquals(2, mappedSecond.getGameNumber());
    }

    @Test
    void gameWonResolvedFromMatchingTeamGameByOpponent() {
        TeamGame ownTeamRow = new TeamGame();
        ownTeamRow.setGameId(1L);
        ownTeamRow.setOpponentTeamCode("MTL");
        ownTeamRow.setWon(true);

        TeamGame otherTeamRow = new TeamGame();
        otherTeamRow.setGameId(1L);
        otherTeamRow.setOpponentTeamCode("COL");
        otherTeamRow.setWon(false);

        TeamGameIndex index = TeamGameIndex.of(List.of(ownTeamRow, otherTeamRow));

        assertTrue(index.wonAgainst(1L, "MTL"));
        assertFalse(index.wonAgainst(1L, "COL"));
    }

    @Test
    void gameWonIsNullWhenUnresolvable() {
        assertNull(TeamGameIndex.of(List.of()).wonAgainst(999L, "MTL"));
        assertNull(TeamGameIndex.empty().wonAgainst(1L, null));
    }

    @Test
    void mapsCoreStatFields() {
        PlayerGameLogDto dto = gameLog(42L, "2025-03-01", "BOS", "H", 2, 1, 3, "19:45");

        GameLog mapped = GameLogWriter.toGameLog(new GameLog(), 8478402L, "20242025", dto, 10, true);

        assertEquals(8478402L, mapped.getPlayerId());
        assertEquals(42L, mapped.getGameId());
        assertEquals("2025-03-01", mapped.getGameDate());
        assertEquals("BOS", mapped.getOpponentTeamCode());
        assertEquals(2, mapped.getGoals());
        assertEquals(1, mapped.getAssists());
        assertEquals(3, mapped.getPoints());
        assertEquals(19 * 60 + 45, mapped.getTimeOnIce());
        assertEquals(true, mapped.getGameWon());
        assertEquals("20242025", mapped.getSeasonId());
        assertEquals(10, mapped.getGameNumber());
    }
}
