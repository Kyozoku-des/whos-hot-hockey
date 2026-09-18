package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import com.whoshot.nhl.domain.entity.TeamGame;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link GameLogWriter}'s team-game mapping (data-model.md {@code team_games}).
 */
class TeamGameMapperTest {

    private GameDto game(long id, String startTimeUTC, int gameType, String homeAbbrev, Integer homeScore,
                          String awayAbbrev, Integer awayScore, String lastPeriodType) {
        GameDto dto = new GameDto();
        dto.setId(id);
        dto.setStartTimeUTC(startTimeUTC);
        dto.setGameType(gameType);
        dto.setGameState(GameState.OFF);

        GameDto.TeamInfo home = new GameDto.TeamInfo();
        home.setAbbrev(homeAbbrev);
        home.setScore(homeScore);
        dto.setHomeTeam(home);

        GameDto.TeamInfo away = new GameDto.TeamInfo();
        away.setAbbrev(awayAbbrev);
        away.setScore(awayScore);
        dto.setAwayTeam(away);

        if (lastPeriodType != null) {
            GameDto.GameOutcome outcome = new GameDto.GameOutcome();
            outcome.setLastPeriodType(lastPeriodType);
            dto.setGameOutcome(outcome);
        }
        return dto;
    }

    @Test
    void mapsHomeTeamPerspective() {
        GameDto g = game(1L, "2025-01-15T00:00:00Z", 2, "COL", 3, "MTL", 2, "REG");

        TeamGame teamGame = GameLogWriter.toTeamGame(new TeamGame(), "COL", "20242025", g, 5);

        assertEquals(1L, teamGame.getGameId());
        assertEquals("COL", teamGame.getTeamCode());
        assertEquals("2025-01-15", teamGame.getGameDate());
        assertEquals("MTL", teamGame.getOpponentTeamCode());
        assertTrue(teamGame.getHomeGame());
        assertEquals(3, teamGame.getGoalsFor());
        assertEquals(2, teamGame.getGoalsAgainst());
        assertTrue(teamGame.getWon());
        assertFalse(teamGame.getOvertimeLoss());
        assertEquals("20242025", teamGame.getSeasonId());
        assertEquals(5, teamGame.getGameNumber());
    }

    @Test
    void mapsAwayTeamPerspective_andRegulationLoss() {
        GameDto g = game(2L, "2025-01-16T00:00:00Z", 2, "COL", 3, "MTL", 2, "REG");

        TeamGame teamGame = GameLogWriter.toTeamGame(new TeamGame(), "MTL", "20242025", g, 1);

        assertFalse(teamGame.getHomeGame());
        assertEquals(2, teamGame.getGoalsFor());
        assertEquals(3, teamGame.getGoalsAgainst());
        assertFalse(teamGame.getWon());
        assertFalse(teamGame.getOvertimeLoss(), "a regulation loss is not an overtime loss");
    }

    @Test
    void overtimeLossIsDistinguishedFromRegulationLoss() {
        GameDto ot = game(3L, "2025-01-17T00:00:00Z", 2, "COL", 3, "MTL", 2, "OT");
        GameDto so = game(4L, "2025-01-18T00:00:00Z", 2, "COL", 3, "MTL", 2, "SO");

        TeamGame otLoss = GameLogWriter.toTeamGame(new TeamGame(), "MTL", "20242025", ot, 1);
        TeamGame soLoss = GameLogWriter.toTeamGame(new TeamGame(), "MTL", "20242025", so, 1);

        assertTrue(otLoss.getOvertimeLoss());
        assertTrue(soLoss.getOvertimeLoss());
    }

    @Test
    void chronologicalCompletedRegularSeasonGames_sortsAscendingAndFiltersPlayoffsAndUnplayed() {
        GameDto regularLater = game(1L, "2025-02-01T00:00:00Z", 2, "COL", 3, "MTL", 2, "REG");
        GameDto regularEarlier = game(2L, "2025-01-01T00:00:00Z", 2, "COL", 4, "MTL", 1, "REG");
        GameDto playoff = game(3L, "2025-01-15T00:00:00Z", 3, "COL", 4, "MTL", 1, "REG");
        GameDto unplayed = game(4L, "2025-03-01T00:00:00Z", 2, "COL", null, "MTL", null, null);

        List<GameDto> result = GameLogWriter.chronologicalCompletedRegularSeasonGames(
                List.of(regularLater, regularEarlier, playoff, unplayed));

        assertEquals(List.of(regularEarlier, regularLater), result);
    }

    @Test
    void chronologicalCompletedRegularSeasonGames_assignsDenseGameNumbers() {
        GameDto earlier = game(1L, "2025-01-01T00:00:00Z", 2, "COL", 4, "MTL", 1, "REG");
        GameDto later = game(2L, "2025-02-01T00:00:00Z", 2, "COL", 3, "MTL", 2, "REG");
        List<GameDto> ordered = GameLogWriter.chronologicalCompletedRegularSeasonGames(List.of(later, earlier));

        TeamGame first = GameLogWriter.toTeamGame(new TeamGame(), "COL", "20242025", ordered.get(0), 1);
        TeamGame second = GameLogWriter.toTeamGame(new TeamGame(), "COL", "20242025", ordered.get(1), 2);

        assertEquals(1, first.getGameNumber());
        assertEquals(2, second.getGameNumber());
    }
}
