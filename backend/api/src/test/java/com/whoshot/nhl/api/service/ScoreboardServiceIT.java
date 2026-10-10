package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.dto.ScoreboardDto;
import com.whoshot.nhl.domain.entity.GameLog;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.ScoreboardGame;
import com.whoshot.nhl.domain.entity.ScoreboardGoal;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.ScoreboardGameRepository;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScoreboardService} against PostgreSQL: which game day is shown, goal order, and the
 * streak and hot flags. The previous-game lookup is a native query, so it needs the real database.
 * Each test rolls back.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ScoreboardService.class)
class ScoreboardServiceIT {

    private static final String SEASON = "20262027";
    private static final long SCORER = 1L;
    private static final long PRIMARY = 2L;
    private static final long SECONDARY = 3L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Autowired
    private ScoreboardService service;
    @Autowired
    private ScoreboardGameRepository scoreboardGameRepository;
    @Autowired
    private GameLogRepository gameLogRepository;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TestEntityManager entityManager;

    @Test
    void noGames_returnsEmptyScoreboard() {
        assertThat(service.getLatestScoreboard()).isEqualTo(ScoreboardDto.empty());
    }

    @Test
    void withoutLiveGames_showsMostRecentFinishedDayWithGoalsInOrder() {
        scoreboardGameRepository.saveAll(List.of(
                game(10L, "2026-10-08", "OFF", goal(1, SCORER)),
                game(20L, "2026-10-09", "OFF", goal(2, PRIMARY), goal(1, SCORER, PRIMARY, SECONDARY)),
                game(21L, "2026-10-09", "OFF")));
        // Read back from the database, not the persistence context, so the goal ordering is the query's.
        entityManager.flush();
        entityManager.clear();

        ScoreboardDto scoreboard = service.getLatestScoreboard();

        assertThat(scoreboard.gameDate()).isEqualTo("2026-10-09");
        assertThat(scoreboard.live()).isFalse();
        assertThat(scoreboard.games()).extracting(ScoreboardDto.Game::gameId).containsExactly(20L, 21L);
        ScoreboardDto.Game game = scoreboard.games().getFirst();
        assertThat(game.goals()).extracting(ScoreboardDto.Goal::goalNumber).containsExactly(1, 2);
        assertThat(game.goals().getFirst().scorer().playerId()).isEqualTo(SCORER);
        assertThat(game.goals().getFirst().assists()).extracting(ScoreboardDto.Point::playerId)
                .containsExactly(PRIMARY, SECONDARY);
    }

    @Test
    void liveGames_takePrecedenceOverALaterFinishedDay() {
        scoreboardGameRepository.saveAll(List.of(
                game(10L, "2026-10-08", "OFF"),
                game(20L, "2026-10-09", "LIVE"),
                game(21L, "2026-10-09", "FINAL")));

        ScoreboardDto scoreboard = service.getLatestScoreboard();

        assertThat(scoreboard.gameDate()).isEqualTo("2026-10-09");
        assertThat(scoreboard.live()).isTrue();
        assertThat(scoreboard.games()).extracting(ScoreboardDto.Game::gameId).containsExactly(20L, 21L);
    }

    @Test
    void streakExtended_onlyWhenThePreviousGameHadAPoint() {
        scoreboardGameRepository.save(game(20L, "2026-10-09", "LIVE", goal(1, SCORER, PRIMARY, SECONDARY)));
        gameLogRepository.saveAll(List.of(
                // Scorer: point in the previous game; an older pointless game does not matter.
                gameLog(SCORER, 1L, "2026-10-05", 0),
                gameLog(SCORER, 2L, "2026-10-07", 1),
                // Primary assist: pointless previous game, earlier point does not count.
                gameLog(PRIMARY, 1L, "2026-10-05", 2),
                gameLog(PRIMARY, 2L, "2026-10-07", 0),
                // Secondary assist: only today's game is logged, so no previous game.
                gameLog(SECONDARY, 20L, "2026-10-09", 1),
                // A previous season's game never extends this season's streak.
                new GameLog(null, SECONDARY, 3L, "2026-04-10", "MTL", true, 1, 0, 1, 0, 1, 900, true, "20252026", 82)));

        ScoreboardDto.Goal goal = service.getLatestScoreboard().games().getFirst().goals().getFirst();

        assertThat(goal.scorer().streakExtended()).isTrue();
        assertThat(goal.assists()).extracting(ScoreboardDto.Point::streakExtended).containsExactly(false, false);
    }

    @Test
    void hot_atOrAboveThreshold() {
        scoreboardGameRepository.save(game(20L, "2026-10-09", "OFF", goal(1, SCORER, PRIMARY, SECONDARY)));
        playerRepository.saveAll(List.of(player(SCORER, 1.5), player(PRIMARY, 1.4), player(SECONDARY, null)));

        ScoreboardDto.Goal goal = service.getLatestScoreboard().games().getFirst().goals().getFirst();

        assertThat(goal.scorer().hot()).isTrue();
        assertThat(goal.assists()).extracting(ScoreboardDto.Point::hot).containsExactly(false, false);
    }

    private static ScoreboardGame game(Long gameId, String date, String state, ScoreboardGoal... goals) {
        var game = new ScoreboardGame();
        game.setGameId(gameId);
        game.setGameDate(date);
        game.setSeasonId(SEASON);
        game.setStartTimeUtc(date + "T23:00:" + (gameId % 60) + "Z");
        game.setGameState(state);
        game.setAwayTeamCode("SEA");
        game.setHomeTeamCode("DET");
        game.getGoals().addAll(List.of(goals));
        return game;
    }

    private static ScoreboardGoal goal(int goalNumber, Long scorerId, Long... assistIds) {
        var goal = new ScoreboardGoal();
        goal.setGoalNumber(goalNumber);
        goal.setScorerId(scorerId);
        goal.setScorerName("Scorer " + scorerId);
        if (assistIds.length > 0) {
            goal.setAssist1Id(assistIds[0]);
            goal.setAssist1Name("Assist " + assistIds[0]);
        }
        if (assistIds.length > 1) {
            goal.setAssist2Id(assistIds[1]);
            goal.setAssist2Name("Assist " + assistIds[1]);
        }
        return goal;
    }

    private static GameLog gameLog(long playerId, long gameId, String date, int points) {
        return new GameLog(null, playerId, gameId, date, "MTL", true,
                points, 0, points, 0, 1, 900, true, SEASON, (int) gameId);
    }

    private static Player player(long playerId, Double pointsPerLastTen) {
        return Player.builder()
                .id(new Player.PlayerId(playerId, SEASON))
                .firstName("First")
                .lastName("Last " + playerId)
                .pointsPerLastNGames(pointsPerLastTen)
                .build();
    }
}
