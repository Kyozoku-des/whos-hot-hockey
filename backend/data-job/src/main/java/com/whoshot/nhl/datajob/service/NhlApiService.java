package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.SeasonsResponseDto;
import com.whoshot.nhl.datajob.dto.nhlapi.*;
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

    @Value("${nhle.api.base-url}")
    private String baseUrl;

    @Value("${nhle.api.alternate-base-url}")
    private String alternateBaseUrl;

    /**
     * Fetches all available seasons from the NHL statistics API.
     *
     * @return ordered list of season metadata
     */
    public List<SeasonDto> getSeasons() {
        String url = alternateBaseUrl + "/stats/rest/en/season";
        log.debug("Fetching seasons from: {}", url);

        SeasonsResponseDto response = apiClient.get(url, new ParameterizedTypeReference<>() {
        });
        return response.getData();
    }

    /**
     * Used only to sort our calculated player stats in the correct order when presenting player standings.
     *
     * @param seasonId season identifier in {@code YYYYYYYY} format
     * @param gameType NHL game type (2 for regular season, 3 for playoffs)
     * @return List of players in standings order
     */
    public List<PlayerStandingDto> getPlayerStandingsOrder(String seasonId, int gameType) {
        String url = String.format("%s/v1/skater-stats-leaders/%s/%d?categories=points&limit=-1",
                baseUrl, seasonId, gameType);
        log.debug("Fetching player standings order from: {}", url);

        PlayerStandingsResponse response = apiClient.get(url, new ParameterizedTypeReference<>() {
        });
        return response.getPlayers();
    }

    /**
     * Get player landing page information.
     *
     * @param playerId NHL player identifier
     * @return player profile details
     */
    public PlayerInfoDto getPlayerInfo(Long playerId) {
        String url = String.format("%s/v1/player/%d/landing", baseUrl, playerId);
        log.debug("Fetching player info for player {} from: {}", playerId, url);

        return apiClient.get(url, new ParameterizedTypeReference<>() {
        });
    }

    /**
     * TODO: Check if this data is updated during games.
     * Get game-by-game logs for a specific player.
     * Returns detailed stats for each game the player has played.
     *
     * @param playerId The player's NHL ID
     * @param seasonId The season ID (e.g., "20252026")
     * @param gameType Game type: 2 = Regular season, 3 = Playoffs
     * @return PlayerGameLogDto containing game log data
     */
    public List<PlayerGameLogDto> getPlayerGameLogs(Long playerId, String seasonId, int gameType) {
        String url = String.format("%s/v1/player/%d/game-log/%s/%d",
                baseUrl, playerId, seasonId, gameType);
        log.debug("Fetching game log for player {} season {} gameType {} from: {}", playerId, seasonId, gameType, url);

        PlayerGameLogsResponse response = apiClient.get(url, new ParameterizedTypeReference<>() {
        });
        return response.getGameLog();
    }

    /**
     * Get team schedule for a specific season.
     *
     * @param teamCode Team code (e.g., "COL")
     * @param seasonId Season ID in format YYYYYYYY (e.g., "20252026")
     * @return List of games containing schedule data with game results
     */
    public List<GameDto> getTeamSchedule(String teamCode, String seasonId) {
        String url = String.format("%s/v1/club-schedule-season/%s/%s", baseUrl, teamCode, seasonId);
        log.debug("Fetching team schedule for {} season {} from: {}", teamCode, seasonId, url);

        TeamScheduleResponseDto response = apiClient.get(url, new ParameterizedTypeReference<>() {
        });
        return response.getGames();
    }

    /**
     * Retrieves current team standings from the NHL public API.
     *
     * @return standings rows for all teams in the current context
     */
    public List<TeamStandingsDto> getTeamStandings() {
        return getTeamStandings("now");
    }

    /**
     * Retrieves team standings as of a specific date from the NHL public API.
     * Used to backfill a completed season's <em>final</em> standings — the season's
     * {@code regularSeasonEndDate} — as opposed to {@link #getTeamStandings()}, which always
     * describes today.
     *
     * @param date standings date in {@code YYYY-MM-DD} format, or the literal {@code "now"}
     * @return standings rows for all teams as of that date
     */
    public List<TeamStandingsDto> getTeamStandings(String date) {
        String url = baseUrl + "/v1/standings/" + date;
        log.debug("Fetching team standings from: {}", url);

        StandingsResponseDto response = apiClient.get(url, new ParameterizedTypeReference<>() {
        });
        return response.getStandings();
    }

    /**
     * Get player statistics for an ongoing game.
     *
     * @param gameId The game ID
     * @return BoxScore data containing player statistics
     */
    public BoxScoreDto getGameBoxScore(Long gameId) {
        String url = String.format("%s/v1/gamecenter/%d/boxscore", baseUrl, gameId);
        log.debug("Fetching game boxscore for game {} from: {}", gameId, url);

        return apiClient.get(url, new ParameterizedTypeReference<>() {
        });
    }

    /**
     * Get league schedule as of now.
     *
     * @return List of games containing schedule data with game results
     */
    public List<GameDto> getLeagueSchedule() {
        String url = baseUrl + "/v1/schedule/now";
        log.debug("Fetching league schedule from: {}", url);

        LeagueScheduleResponseDto response =
                apiClient.get(url, new ParameterizedTypeReference<>() {
                });

        if (response.getGameWeek() == null) {
            return List.of();
        }

        return response.getGameWeek().stream()
                .filter(week -> week.getGames() != null)
                .flatMap(week -> week.getGames().stream())
                .toList();
    }
}
