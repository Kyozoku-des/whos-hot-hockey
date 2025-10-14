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
     * Get current skater stats leaders.
     */
    public JsonNode getSkaterStatsLeaders() {
        String url = baseUrl + "/v1/skater-stats-leaders/current?limit=-1";
        log.info("Fetching skater stats from: {}", url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching skater stats", e);
            throw new RuntimeException("Failed to fetch skater stats from NHL API", e);
        }
    }

    /**
     * Get player game log.
     */
    public JsonNode getPlayerGameLog(Long playerId) {
        String url = String.format("%s/v1/player/%d/game-log/now", baseUrl, playerId);
        log.info("Fetching game log for player {} from: {}", playerId, url);
        try {
            return restTemplate.getForObject(url, JsonNode.class);
        } catch (Exception e) {
            log.error("Error fetching game log for player {}", playerId, e);
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
