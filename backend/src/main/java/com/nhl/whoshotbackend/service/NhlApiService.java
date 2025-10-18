package com.nhl.whoshotbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Service for interacting with the NHL API.
 * Fetches raw data from NHL endpoints.
 */
@Service
@Slf4j
public class NhlApiService {

    private final RestTemplate restTemplate;

    @Value("${nhl.api.base-url}")
    private String baseUrl;

    @Value("${nhl.api.stats-base-url}")
    private String statsBaseUrl;

    @Value("${nhl.api.current-season}")
    private String currentSeason;

    public NhlApiService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    /**
     * Get the current season ID from configuration.
     * @return Season ID in format YYYYYYYY (e.g., "20252026")
     */
    public String getCurrentSeason() {
        return currentSeason;
    }

    /**
     * Get current standings.
     */
    public JsonNode getStandings() {
        String url = baseUrl + "/v1/standings/now";
        log.info("Fetching standings from: {}", url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching standings", e);
            throw new RuntimeException("Failed to fetch standings from NHL API", e);
        }
    }

    /**
     * Get all skater stats for the current season.
     * Uses the Stats API which provides complete player statistics, not just leaders.
     */
    public JsonNode getAllSkaterStats() {
        return getAllSkaterStats(currentSeason);
    }

    /**
     * Get all skater stats for a specific season.
     * @param seasonId Season ID in format YYYYYYYY (e.g., 20252026 for 2025-2026 season)
     * @return JSON response with player statistics
     */
    public JsonNode getAllSkaterStats(String seasonId) {
        // Use the Stats API endpoint to get ALL skater stats
        // cayenneExp parameter filters by season and game type (2 = regular season)
        String url = String.format("%s/en/skater/summary?limit=-1&cayenneExp=seasonId=%s and gameTypeId=2",
                                   statsBaseUrl, seasonId);
        log.info("Fetching all skater stats for season {} from: {}", seasonId, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching skater stats for season {}", seasonId, e);
            throw new RuntimeException("Failed to fetch skater stats from NHL API for season " + seasonId, e);
        }
    }

    /**
     * Get player game log for current season.
     */
    public JsonNode getPlayerGameLog(Long playerId) {
        return getPlayerGameLog(playerId, currentSeason, 2);
    }

    /**
     * Get game-by-game logs for a specific player.
     * Returns detailed stats for each game the player has played.
     * @param playerId The player's NHL ID
     * @param seasonId The season ID (e.g., "20252026")
     * @param gameType Game type: 2 = Regular season, 3 = Playoffs
     * @return JsonNode containing game log data
     */
    public JsonNode getPlayerGameLog(Long playerId, String seasonId, int gameType) {
        String url = String.format("%s/v1/player/%d/game-log/%s/%d",
                baseUrl, playerId, seasonId, gameType);
        log.info("Fetching game log for player {} season {} gameType {} from: {}", playerId, seasonId, gameType, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.warn("Could not fetch game log for player {} season {}: {}", playerId, seasonId, e.getMessage());
            // Don't throw exception - some players might not have game logs yet
            return null;
        }
    }

    /**
     * Get player landing page information.
     */
    public JsonNode getPlayerInfo(Long playerId) {
        String url = String.format("%s/v1/player/%d/landing", baseUrl, playerId);
        log.info("Fetching player info for {} from: {}", playerId, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching player info for {}", playerId, e);
            return null;
        }
    }

    /**
     * Get team roster.
     */
    public JsonNode getTeamRoster(String teamCode) {
        String url = String.format("%s/v1/roster/%s/current", baseUrl, teamCode);
        log.info("Fetching roster for team {} from: {}", teamCode, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching roster for team {}", teamCode, e);
            return null;
        }
    }

    /**
     * Get club stats for a team.
     */
    public JsonNode getClubStats(String teamCode) {
        String url = String.format("%s/v1/club-stats/%s/now", baseUrl, teamCode);
        log.info("Fetching club stats for {} from: {}", teamCode, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching club stats for {}", teamCode, e);
            return null;
        }
    }

    /**
     * Get team schedule.
     */
    public JsonNode getTeamSchedule(String teamCode) {
        String url = String.format("%s/v1/club-schedule-season/%s/now", baseUrl, teamCode);
        log.info("Fetching schedule for team {} from: {}", teamCode, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching schedule for team {}", teamCode, e);
            return null;
        }
    }

    /**
     * Get game boxscore.
     */
    public JsonNode getGameBoxscore(Long gameId) {
        String url = String.format("%s/v1/gamecenter/%d/boxscore", baseUrl, gameId);
        log.info("Fetching boxscore for game {} from: {}", gameId, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching boxscore for game {}", gameId, e);
            return null;
        }
    }
}
