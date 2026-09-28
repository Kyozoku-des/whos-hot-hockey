package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.model.PlayerStatistics;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link StatisticsCalculationService}. Game logs are passed most-recent-first,
 * matching the NHL API order the service relies on for streaks and last-N averages.
 */
class StatisticsCalculationServiceTest {

    private static final double DELTA = 1e-9;

    private final StatisticsCalculationService service = new StatisticsCalculationService();

    private PlayerStandingDto standing(long playerId, int points) {
        PlayerStandingDto dto = new PlayerStandingDto();
        dto.setId(playerId);
        dto.setPoints(points);
        return dto;
    }

    private PlayerGameLogDto game(Integer goals, Integer assists, Integer plusMinus) {
        PlayerGameLogDto dto = new PlayerGameLogDto();
        dto.setGoals(goals);
        dto.setAssists(assists);
        dto.setPoints(goals == null && assists == null ? null
                : (goals == null ? 0 : goals) + (assists == null ? 0 : assists));
        dto.setPlusMinus(plusMinus);
        return dto;
    }

    /** Builds most-recent-first logs where each entry is that game's point total (all assists). */
    private List<PlayerGameLogDto> gamesWithPoints(int... pointsMostRecentFirst) {
        List<PlayerGameLogDto> logs = new ArrayList<>();
        for (int p : pointsMostRecentFirst) {
            logs.add(game(0, p, 0));
        }
        return logs;
    }

    private PlayerStatistics calculate(int... pointsMostRecentFirst) throws PlayerStatisticsException {
        int total = 0;
        for (int p : pointsMostRecentFirst) {
            total += p;
        }
        return service.calculatePlayerStatistics(standing(1L, total), gamesWithPoints(pointsMostRecentFirst));
    }

    @Test
    void sumsSeasonTotalsAcrossAllGames() throws PlayerStatisticsException {
        List<PlayerGameLogDto> logs = List.of(
                game(1, 2, 2),
                game(0, 0, -1),
                game(2, 0, 1));

        PlayerStatistics stats = service.calculatePlayerStatistics(standing(8478402L, 5), logs);

        assertEquals(3, stats.gamesPlayed());
        assertEquals(3, stats.goals());
        assertEquals(2, stats.assists());
        assertEquals(5, stats.points());
        assertEquals(2, stats.plusMinus());
        assertEquals(5.0 / 3, stats.pointsPerGame(), DELTA);
        assertNotNull(stats.lastUpdated());
    }

    @Test
    void nullStatFieldsCountAsZero() throws PlayerStatisticsException {
        List<PlayerGameLogDto> logs = List.of(
                game(null, null, null),
                game(1, null, 1));

        PlayerStatistics stats = service.calculatePlayerStatistics(standing(1L, 1), logs);

        assertEquals(2, stats.gamesPlayed());
        assertEquals(1, stats.goals());
        assertEquals(0, stats.assists());
        assertEquals(1, stats.points());
        assertEquals(1, stats.plusMinus());
        // Null points in the most recent game count as a pointless game.
        assertEquals(0, stats.currentPointStreak());
        assertEquals(1, stats.currentPointlessStreak());
    }

    @Test
    void noGames_yieldsZerosWithoutDividingByZero() throws PlayerStatisticsException {
        PlayerStatistics stats = service.calculatePlayerStatistics(standing(1L, 0), List.of());

        assertEquals(0, stats.gamesPlayed());
        assertEquals(0, stats.points());
        assertEquals(0.0, stats.pointsPerGame(), DELTA);
        assertEquals(0.0, stats.pointsPerLastNGames(), DELTA);
        assertEquals(0, stats.currentPointStreak());
        assertEquals(0, stats.currentPointlessStreak());
    }

    @Test
    void pointStreak_countsConsecutiveScoringGamesFromMostRecent() throws PlayerStatisticsException {
        PlayerStatistics stats = calculate(1, 2, 1, 0, 3, 3);

        assertEquals(3, stats.currentPointStreak());
        assertEquals(0, stats.currentPointlessStreak());
    }

    @Test
    void pointlessStreak_countsConsecutiveScorelessGamesFromMostRecent() throws PlayerStatisticsException {
        PlayerStatistics stats = calculate(0, 0, 0, 0, 2, 0);

        assertEquals(0, stats.currentPointStreak());
        assertEquals(4, stats.currentPointlessStreak());
    }

    @Test
    void streaks_spanWholeSeasonWhenNeverBroken() throws PlayerStatisticsException {
        assertEquals(4, calculate(1, 1, 2, 1).currentPointStreak());
        assertEquals(3, calculate(0, 0, 0).currentPointlessStreak());
    }

    @Test
    void streaks_areMutuallyExclusive() throws PlayerStatisticsException {
        PlayerStatistics scoring = calculate(1, 0, 0, 0);
        PlayerStatistics scoreless = calculate(0, 1, 1, 1);

        assertEquals(1, scoring.currentPointStreak());
        assertEquals(0, scoring.currentPointlessStreak());
        assertEquals(0, scoreless.currentPointStreak());
        assertEquals(1, scoreless.currentPointlessStreak());
    }

    @Test
    void pointsPerLastNGames_usesTenMostRecentGamesOnly() throws PlayerStatisticsException {
        // 10 most recent games: 15 points. Older games (ignored): 20 points.
        PlayerStatistics stats = calculate(3, 2, 0, 1, 1, 2, 0, 3, 1, 2, 10, 10);

        assertEquals(12, stats.gamesPlayed());
        assertEquals(1.5, stats.pointsPerLastNGames(), DELTA);
        assertEquals(35.0 / 12, stats.pointsPerGame(), DELTA);
    }

    @Test
    void pointsPerLastNGames_usesAllGamesWhenFewerThanTen() throws PlayerStatisticsException {
        PlayerStatistics stats = calculate(2, 0, 1, 1);

        assertEquals(1.0, stats.pointsPerLastNGames(), DELTA);
        assertEquals(stats.pointsPerGame(), stats.pointsPerLastNGames(), DELTA);
    }

    @Test
    void pointsMismatchWithStandings_throwsWithPlayerAndBothTotals() {
        List<PlayerGameLogDto> logs = gamesWithPoints(2, 1);

        PlayerStatisticsException e = assertThrows(PlayerStatisticsException.class,
                () -> service.calculatePlayerStatistics(standing(8478402L, 4), logs));

        assertTrue(e.getMessage().contains("8478402"), e.getMessage());
        assertTrue(e.getMessage().contains("calculated 3"), e.getMessage());
        assertTrue(e.getMessage().contains("expected 4"), e.getMessage());
    }
}
