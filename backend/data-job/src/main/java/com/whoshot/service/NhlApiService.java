package com.whoshot.service;

import com.whoshot.dto.SeasonDto;
import com.whoshot.dto.SeasonsResponseDto;
import com.whoshot.dto.nhlapi.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service for interacting with the NHL API.
 * Fetches raw data from NHL endpoints.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NhlApiService {

    private final ApiClient apiClient;

    @Value("${nhl.api.base-url}")
    private String baseUrl;

    @Value("${nhl.api.stats-base-url}")
    private String statsBaseUrl;

    /**
     * Get the current season ID.
     * @return Season ID in format YYYYYYYY (e.g., "20252026")
     */
    public List<SeasonDto> getSeasons() {
        String url = statsBaseUrl + "/en/season";
        log.info("Fetching seasons from: {}", url);

        SeasonsResponseDto response = apiClient.get(url, new ParameterizedTypeReference<>() {});
        return response.getData();
    }

    /**
     * Get current standings for all teams.
     */
    public List<TeamStandingsDto> getTeamStandings() {
        String url = baseUrl + "/v1/standings/now";
        log.info("Fetching team standings from: {}", url);

        StandingsResponseDto response = apiClient.get(url, new ParameterizedTypeReference<>() {});
        return response.getStandings();
    }

    /**
     * Used only to sort our calculated player stats in the correct order when presenting player standings.
     * @return List of players in standings order
     */
    public List<PlayerStandingDto> getPlayerStandingsOrder(String seasonId, int gameType) {
        String url = String.format("%s/v1/skater-stats-leaders/%s/%d?categories=points&limit=-1",
                baseUrl, seasonId, gameType);
        log.info("Fetching player standings order from: {}", url);

        PlayerStandingsResponse response = apiClient.get(url, new ParameterizedTypeReference<>() {});
        return response.getPlayers();
    }

    /**
     * Get game-by-game logs for a specific player.
     * Returns detailed stats for each game the player has played.
     * @param playerId The player's NHL ID
     * @param seasonId The season ID (e.g., "20252026")
     * @param gameType Game type: 2 = Regular season, 3 = Playoffs
     * @return PlayerGameLogDto containing game log data
     */
    public List<PlayerGameLogDto> getPlayerGameLogs(Long playerId, String seasonId, int gameType) {
        String url = String.format("%s/v1/player/%d/game-log/%s/%d",
                baseUrl, playerId, seasonId, gameType);
        log.info("Fetching game log for player {} season {} gameType {} from: {}", playerId, seasonId, gameType, url);

        PlayerGameLogsResponse response = apiClient.get(url, new ParameterizedTypeReference<>() {});
        return response.getGameLogs();
    }

    /**
     * Get player landing page information.
     */
    public PlayerInfoDto getPlayerInfo(Long playerId) {
        String url = String.format("%s/v1/player/%d/landing", baseUrl, playerId);
        log.info("Fetching player info for player {} from: {}", playerId, url);

        return apiClient.get(url, new ParameterizedTypeReference<>() {});
    }

    /**
     * Get team schedule for a specific season.
     * @param teamCode Team code (e.g., "COL")
     * @param seasonId Season ID in format YYYYYYYY (e.g., "20252026")
     * @return List of games containing schedule data with game results
     */
    public List<GameDto> getTeamSchedule(String teamCode, String seasonId) {
        String url = String.format("%s/v1/club-schedule-season/%s/%s", baseUrl, teamCode, seasonId);
        log.info("Fetching team schedule for {} season {} from: {}", teamCode, seasonId, url);

        TeamScheduleResponseDto response = apiClient.get(url, new ParameterizedTypeReference<>() {});
        return response.getGames();
    }

    /**
     * Get player statistics for an ongoing game.
     * @param gameId The game ID
     * @return BoxScore data containing player statistics
     */
    public BoxScoreDto getGameBoxscore(Long gameId) {
        String url = String.format("%s/v1/gamecenter/%d/boxscore", baseUrl, gameId);
        log.info("Fetching game boxscore for game {} from: {}", gameId, url);

        return apiClient.get(url, new ParameterizedTypeReference<>() {});
    }
}
