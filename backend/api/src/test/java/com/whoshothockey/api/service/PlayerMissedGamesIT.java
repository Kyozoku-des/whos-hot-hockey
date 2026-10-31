package com.whoshothockey.api.service;

import com.whoshothockey.api.dto.MissedGameDto;
import com.whoshothockey.api.dto.PlayerStandingsDto;
import com.whoshothockey.domain.entity.GameLog;
import com.whoshothockey.domain.entity.Player;
import com.whoshothockey.domain.entity.TeamGame;
import com.whoshothockey.domain.repository.GameLogRepository;
import com.whoshothockey.domain.repository.PlayerRepository;
import com.whoshothockey.domain.repository.TeamGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Integration coverage for the games a player missed and the last-10 figures of the player lists,
 * which come from a native latest-game query against the real Flyway schema. Each test rolls back.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlayerService.class, SeasonResolver.class})
class PlayerMissedGamesIT {

    private static final String SEASON = "20252026";
    private static final long INJURED_ID = 8480001L;
    private static final long TRADED_ID = 8480002L;
    private static final long HEALTHY_ID = 8480003L;
    private static final long UNPLAYED_ID = 8480004L;
    private static final long FORMER_OPPONENT_ID = 8480005L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Autowired
    private PlayerService playerService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private GameLogRepository gameLogRepository;
    @Autowired
    private TeamGameRepository teamGameRepository;

    @BeforeEach
    void seed() {
        // COL's five games, inserted out of order; game 2 was against Vancouver
        teamGameRepository.saveAll(List.of(
                teamGame("COL", 5, "2025-10-16", "MTL"),
                teamGame("COL", 1, "2025-10-08", "MTL"),
                teamGame("COL", 3, "2025-10-12", "MTL"),
                teamGame("COL", 2, "2025-10-10", "VAN"),
                teamGame("VAN", 2, "2025-10-10", "COL"),
                teamGame("COL", 4, "2025-10-14", "MTL"),
                teamGame("SEA", 100, "2025-10-09", "MTL"),
                teamGame("SEA", 101, "2025-10-11", "MTL")));

        playerRepository.saveAll(List.of(
                player(INJURED_ID, 3, 4, 4.0 / 3),
                player(TRADED_ID, 3, 3, 1.0),
                player(HEALTHY_ID, 5, 10, 2.0),
                player(UNPLAYED_ID, 0, 0, null),
                player(FORMER_OPPONENT_ID, 2, 2, 1.0)));

        gameLogRepository.saveAll(List.of(
                // Played the first three, out since
                gameLog(INJURED_ID, 1, "2025-10-08"),
                gameLog(INJURED_ID, 2, "2025-10-10", "VAN"),
                gameLog(INJURED_ID, 3, "2025-10-12"),
                // Two games for Seattle, then traded and played COL's game 4
                gameLog(TRADED_ID, 100, "2025-10-09"),
                gameLog(TRADED_ID, 101, "2025-10-11"),
                gameLog(TRADED_ID, 4, "2025-10-14"),
                gameLog(HEALTHY_ID, 1, "2025-10-08"),
                gameLog(HEALTHY_ID, 2, "2025-10-10", "VAN"),
                gameLog(HEALTHY_ID, 3, "2025-10-12"),
                gameLog(HEALTHY_ID, 4, "2025-10-14"),
                gameLog(HEALTHY_ID, 5, "2025-10-16"),
                // Played against COL for Vancouver in game 2, traded, then COL's game 5
                gameLog(FORMER_OPPONENT_ID, 2, "2025-10-10", "COL"),
                gameLog(FORMER_OPPONENT_ID, 5, "2025-10-16")));
    }

    @Test
    void missedGames_areTheTeamsGamesWithoutThePlayerSinceTheirFirstGameForIt() {
        assertEquals(List.of(4L, 5L), gameIds(playerService.getPlayerMissedGames(INJURED_ID, SEASON)));
        assertEquals(List.of(5L), gameIds(playerService.getPlayerMissedGames(TRADED_ID, SEASON)));
        assertEquals(List.of(), gameIds(playerService.getPlayerMissedGames(HEALTHY_ID, SEASON)));
        assertEquals(List.of(), gameIds(playerService.getPlayerMissedGames(UNPLAYED_ID, SEASON)));
        assertEquals(List.of(), gameIds(playerService.getPlayerMissedGames(FORMER_OPPONENT_ID, SEASON)));
        assertEquals(List.of(), gameIds(playerService.getPlayerMissedGames(9999999L, SEASON)));
    }

    @Test
    void playerDetail_countsTheGamesMissedInARow() {
        assertEquals(2, playerService.getPlayerDetail(INJURED_ID, SEASON).gamesMissedInARow());
        assertEquals(1, playerService.getPlayerDetail(TRADED_ID, SEASON).gamesMissedInARow());
        assertEquals(0, playerService.getPlayerDetail(HEALTHY_ID, SEASON).gamesMissedInARow());
        assertEquals(0, playerService.getPlayerDetail(UNPLAYED_ID, SEASON).gamesMissedInARow());
    }

    @Test
    void playerStandings_carryMissedGamesAndTheLast10Pace() {
        Map<Long, PlayerStandingsDto> standings = playerService.getPlayerStandings(SEASON).stream()
                .collect(Collectors.toMap(PlayerStandingsDto::playerId, Function.identity()));

        PlayerStandingsDto injured = standings.get(INJURED_ID);
        assertEquals(2, injured.gamesMissedInARow());
        assertEquals(3, injured.lastNGamesPlayed());
        assertEquals(4, injured.pointsInLastNGames());
        // 4 points + 4/3 a game over the 77 games COL has left, not the 79 the player has
        assertEquals(107, injured.projectedPointsLastN());

        assertEquals(1, standings.get(TRADED_ID).gamesMissedInARow());
        assertEquals(0, standings.get(HEALTHY_ID).gamesMissedInARow());
        assertEquals(164, standings.get(HEALTHY_ID).projectedPointsLastN());

        PlayerStandingsDto unplayed = standings.get(UNPLAYED_ID);
        assertEquals(0, unplayed.gamesMissedInARow());
        assertNull(unplayed.projectedPointsLastN());
    }

    private static List<Long> gameIds(List<MissedGameDto> games) {
        return games.stream().map(MissedGameDto::gameId).toList();
    }

    private static Player player(long playerId, int gamesPlayed, int points, Double pointsPerLastNGames) {
        return Player.builder()
                .id(new Player.PlayerId(playerId, SEASON))
                .firstName("First")
                .lastName("Last" + playerId)
                .positionCode("C")
                .teamCode("COL")
                .gamesPlayed(gamesPlayed)
                .points(points)
                .pointsPerLastNGames(pointsPerLastNGames)
                .build();
    }

    private static GameLog gameLog(long playerId, long gameId, String date) {
        return gameLog(playerId, gameId, date, "MTL");
    }

    private static GameLog gameLog(long playerId, long gameId, String date, String opponent) {
        return new GameLog(null, playerId, gameId, LocalDate.parse(date), opponent, true,
                1, 0, 1, 1, 3, 1080, true, SEASON, 1);
    }

    private static TeamGame teamGame(String team, long gameId, String date, String opponent) {
        return new TeamGame(null, gameId, team, LocalDate.parse(date), opponent, true,
                3, 2, true, false, "2", SEASON, 1);
    }
}
