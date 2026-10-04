package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.dto.PlayerGameLogDto;
import com.whoshot.nhl.api.dto.TeamGameLogDto;
import com.whoshot.nhl.domain.entity.GameLog;
import com.whoshot.nhl.domain.entity.TeamGame;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration coverage for FR-011 / FR-012: the game-log endpoints' services return a backfilled
 * past season when asked for it by id, in the order the graphs expect, and return an empty list —
 * not an error or zeroed rows — for a player or team with nothing in that season.
 * <p>
 * Runs against a disposable PostgreSQL 16 container with the real Flyway schema, since the
 * ordering under test comes from derived repository queries. Each test rolls back.
 */
@Tag("integration")
@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PlayerService.class, TeamService.class, SeasonResolver.class})
class SeasonScopedGameLogIT {

    private static final String PAST_SEASON = "20242025";
    private static final String CURRENT_SEASON = "20252026";
    private static final long VETERAN_ID = 8480001L;
    private static final long ROOKIE_ID = 8490001L;

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    @Autowired
    private PlayerService playerService;
    @Autowired
    private TeamService teamService;
    @Autowired
    private GameLogRepository gameLogRepository;
    @Autowired
    private TeamGameRepository teamGameRepository;

    @BeforeEach
    void seed() {
        // Inserted out of order so the assertions prove the query sorts, not the insert order.
        gameLogRepository.saveAll(List.of(
                gameLog(VETERAN_ID, 2024020003L, "2024-10-12", PAST_SEASON, 3),
                gameLog(VETERAN_ID, 2024020001L, "2024-10-08", PAST_SEASON, 1),
                gameLog(VETERAN_ID, 2024020002L, "2024-10-10", PAST_SEASON, 2),
                gameLog(VETERAN_ID, 2025020001L, "2025-10-07", CURRENT_SEASON, 1),
                gameLog(ROOKIE_ID, 2025020002L, "2025-10-09", CURRENT_SEASON, 1)));

        teamGameRepository.saveAll(List.of(
                teamGame("COL", 2024020002L, "2024-10-10", PAST_SEASON, 2),
                teamGame("COL", 2024020001L, "2024-10-08", PAST_SEASON, 1),
                teamGame("COL", 2024020003L, "2024-10-12", PAST_SEASON, 3),
                teamGame("COL", 2025020001L, "2025-10-07", CURRENT_SEASON, 1)));
    }

    @Test
    void playerGameLog_forBackfilledSeason_isAscendingByGameNumber_andScopedToThatSeason() {
        List<PlayerGameLogDto> log = playerService.getPlayerGameLog(VETERAN_ID, PAST_SEASON);

        assertEquals(List.of(1, 2, 3), log.stream().map(PlayerGameLogDto::gameNumber).toList());
        assertEquals(List.of(2024020001L, 2024020002L, 2024020003L),
                log.stream().map(PlayerGameLogDto::gameId).toList());
    }

    @Test
    void teamGameLog_forBackfilledSeason_isDescendingByGameDate_andScopedToThatSeason() {
        List<TeamGameLogDto> log = teamService.getTeamGameLog("COL", PAST_SEASON);

        assertEquals(List.of("2024-10-12", "2024-10-10", "2024-10-08"),
                log.stream().map(TeamGameLogDto::gameDate).toList());
    }

    @Test
    void playerAbsentFromBackfilledSeason_returnsEmptyList() {
        assertTrue(playerService.getPlayerGameLog(ROOKIE_ID, PAST_SEASON).isEmpty());
    }

    @Test
    void seasonThatWasNeverLoaded_returnsEmptyList() {
        assertTrue(playerService.getPlayerGameLog(VETERAN_ID, "20102011").isEmpty());
        assertTrue(teamService.getTeamGameLog("COL", "20102011").isEmpty());
    }

    private static GameLog gameLog(long playerId, long gameId, String date, String season, int number) {
        return new GameLog(null, playerId, gameId, date, "MTL", true,
                1, 0, 1, 1, 3, 1080, true, season, number);
    }

    private static TeamGame teamGame(String team, long gameId, String date, String season, int number) {
        return new TeamGame(null, gameId, team, date, "MTL", true,
                3, 2, true, false, "2", season, number);
    }
}
