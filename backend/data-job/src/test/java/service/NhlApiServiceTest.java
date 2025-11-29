package service;

import com.whoshot.dto.SeasonDto;
import com.whoshot.dto.nhlapi.*;
import com.whoshot.service.NhlApiService;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.springframework.test.util.AssertionErrors.assertNotNull;

/**
 * Integration test for NhlApiService.getPlayerGameLog()
 * This test makes real API calls to the NHL API (no mocking).
 */
@Slf4j
@SpringBootTest
class NhlApiServiceTest {

    @Autowired
    private NhlApiService nhlApiService;

    /**
     * Test getPlayerGameLog with Connor McDavid (8478402)
     * Season: 20242025 (current season)
     * GameType: 2 (regular season)
     */
    @Test
    void testGetPlayerGameLogs_ConnorMcDavid() {
        // Arrange
        Long playerId = 8478402L; // Connor McDavid
        String seasonId = "20252026";
        int gameType = 2; // Regular season

        // Act
        PlayerGameLogsResponse result = nhlApiService.getPlayerGameLoge(playerId, seasonId, gameType);

        // Assert
        Assertions.assertNotNull(result, "PlayerGameLogDto should not be null");
        log.info("Season ID: {}", result.getSeasonId());
        log.info("Number of games: {}", result.getGameLog() != null ? result.getGameLog().size() : 0);

        if (result.getGameLog() != null && !result.getGameLog().isEmpty()) {
            PlayerGameLogsResponse.PlayerGameLogDto firstGame = result.getGameLog().get(0);
            log.info("First game - Date: {}, Opponent: {}, Goals: {}, Assists: {}, Points: {}",
                    firstGame.getGameDate(),
                    firstGame.getOpponentAbbrev(),
                    firstGame.getGoals(),
                    firstGame.getAssists(),
                    firstGame.getPoints());

            // Verify game log entry has required fields
            Assertions.assertNotNull(firstGame.getGameId(), "Game ID should not be null");
            Assertions.assertNotNull(firstGame.getGameDate(), "Game date should not be null");
            Assertions.assertNotNull(firstGame.getOpponentAbbrev(), "Opponent abbreviation should not be null");
        }
    }

    /**
     * Test getPlayerInfo with Connor McDavid (8478402)
     */
    @Test
    void testGetPlayerInfo_ConnorMcDavid() {
        // Arrange
        Long playerId = 8478402L; // Connor McDavid

        // Act
        PlayerInfoDto result = nhlApiService.getPlayerInfo(playerId);

        // Assert
        Assertions.assertNotNull(result, "PlayerInfoDto should not be null");
        log.info("Player ID: {}", result.getPlayerId());
        log.info("Is Active: {}", result.isActive());
        log.info("Headshot URL: {}", result.getHeadshotUrl());
        log.info("Hero Image URL: {}", result.getHeroImage());

        // Verify required fields
        Assertions.assertNotNull(result.getPlayerId(), "Player ID should not be null");
        Assertions.assertEquals(playerId, result.getPlayerId(), "Player ID should match requested ID");
        Assertions.assertTrue(result.isActive(), "Connor McDavid should be an active player");
        Assertions.assertNotNull(result.getHeadshotUrl(), "Headshot URL should not be null");
    }

    /**
     * Test getSeasons
     */
    @Test
    void testGetSeasons() {
        // Act
        List<SeasonDto> result = nhlApiService.getSeasons();

        // Assert
        Assertions.assertNotNull(result, "Seasons list should not be null");
        Assertions.assertFalse(result.isEmpty(), "Seasons list should not be empty");
        log.info("Number of seasons: {}", result.size());

        if (!result.isEmpty()) {
            SeasonDto firstSeason = result.get(0);
            log.info("First season ID: {}", firstSeason.getId());
            assertNotNull(firstSeason.getId(), "Season ID should not be null");
        }
    }

    /**
     * Test getTeamStandings
     */
    @Test
    void testGetTeamStandings() {
        // Act
        List<TeamStandingsDto> result = nhlApiService.getTeamStandings();

        // Assert
        Assertions.assertNotNull(result, "Team standings list should not be null");
        Assertions.assertFalse(result.isEmpty(), "Team standings list should not be empty");
        log.info("Number of teams: {}", result.size());

        if (!result.isEmpty()) {
            TeamStandingsDto firstTeam = result.get(0);
            log.info("First team - Name: {}, Wins: {}, Losses: {}, Points: {}",
                    firstTeam.getTeamName(),
                    firstTeam.getWins(),
                    firstTeam.getLosses(),
                    firstTeam.getPoints());
            Assertions.assertNotNull(firstTeam.getTeamAbbrev(), "Team abbreviation should not be null");
        }
    }

    /**
     * Test getPlayerStandingsOrder
     */
    @Test
    void testGetPlayerStandingsOrder() {
        // Arrange
        String seasonId = "20252026";
        int gameType = 2; // Regular season

        // Act
        List<PlayerStandingDto> result = nhlApiService.getPlayerStandingsOrder(seasonId, gameType);

        // Assert
        Assertions.assertNotNull(result, "Player standings list should not be null");
        Assertions.assertFalse(result.isEmpty(), "Player standings list should not be empty");
        log.info("Number of players in standings: {}", result.size());
    }

    /**
     * Test getTeamSchedule for Edmonton Oilers
     */
    @Test
    void testGetTeamSchedule_EdmontonOilers() {
        // Arrange
        String teamCode = "EDM";
        String seasonId = "20252026";

        // Act
        List<GameDto> result = nhlApiService.getTeamSchedule(teamCode, seasonId);

        // Assert
        Assertions.assertNotNull(result, "Team schedule list should not be null");
        Assertions.assertFalse(result.isEmpty(), "Team schedule should not be empty");
    }

    /**
     * Test getGameBoxscore
     * Uses a recent game ID that should have completed
     */
    @Test
    void testGetGameBoxscore() {
        // Arrange
        // First get a game ID from team schedule
        String teamCode = "EDM";
        String seasonId = "20252026";
        List<GameDto> schedule = nhlApiService.getTeamSchedule(teamCode, seasonId);

        Assertions.assertNotNull(schedule, "Schedule should not be null");
        Assertions.assertFalse(schedule.isEmpty(), "Schedule should not be empty");

        Long gameId = schedule.get(0).getId();
        log.info("Testing boxscore for game ID: {}", gameId);

        // Act
        BoxScoreDto result = nhlApiService.getGameBoxscore(gameId);

        // Assert
        Assertions.assertNotNull(result, "BoxScoreDto should not be null");
    }
}
