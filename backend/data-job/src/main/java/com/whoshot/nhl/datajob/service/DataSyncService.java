package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.*;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates retrieval, transformation, and persistence of player season data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private final NhlApiService nhlApiService;
    private final PlayerRepository playerRepository;
    private final PlayerFactory playerFactory;
    private final TeamRepository teamRepository;
    private final GameLogRepository gameLogRepository;
    private SeasonDto season;
    @Getter
    private LocalDateTime firstGameTimeForToday;
    @Getter
    private LocalDateTime lastGameTimeForToday;
    private final int gameType = 2; // Regular season

    /**
     * Initializes the service by resolving the active season once at startup.
     */
    @PostConstruct
    private void init() {
        setSeason();
        setFirstAndLastGameTimesForToday();
    }

    /**
     * Resolves and stores the best season to synchronize based on current date boundaries.
     * Falls back to the latest available season when the current date does not fall within any regular season range.
     */
    private void setSeason() {
        List<SeasonDto> seasons = nhlApiService.getSeasons();

        for (SeasonDto season : seasons) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = season.getStartDate();
            LocalDateTime endDate = season.getRegularSeasonEndDate();

            String seasonId = season.getId();
            if (now.isAfter(startDate) && now.isBefore(endDate)) {
                log.info("Current season determined: {}", seasonId);
                this.season = season;
                return;
            }
        }

        log.info("No current season found, using latest season: {}", seasons.getLast().getId());
        this.season = seasons.getLast();
    }

    /**
     * Fetches today's game schedule and determines the earliest and latest game start times.
     */
    public void setFirstAndLastGameTimesForToday() {
        List<GameDto> games = nhlApiService.getLeagueSchedule();

        LocalDateTime todayStart = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        List<LocalDateTime> todayGameTimes = games.stream()
                .map(game -> LocalDateTime.parse(game.getStartTimeUTC(), DateTimeFormatter.ISO_DATE_TIME))
                .filter(time -> !time.isBefore(todayStart) && time.isBefore(todayEnd))
                .toList();

        firstGameTimeForToday = todayGameTimes.stream()
                .min(LocalDateTime::compareTo)
                .orElse(null);

        lastGameTimeForToday = todayGameTimes.stream()
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * Synchronizes active-player statistics for the resolved season.
     * Side effects: performs external API calls and writes player records to the database.
     *
     * @throws PlayerStatisticsException if player identity or point-total validation fails
     */
    @Transactional
    public void syncPlayers() throws PlayerStatisticsException {
        String seasonId = season.getId();
        log.info("Starting player sync for season {}", seasonId);

        var players = nhlApiService.getPlayerStandingsOrder(seasonId, gameType);
        int processedCount = 0;

        // Get order from standings API, calculate new statistics and verify points
        for (PlayerStandingDto playerStanding : players) {
            Long playerId = playerStanding.getId();

            PlayerInfoDto playerInfo = nhlApiService.getPlayerInfo(playerId);

            if (!playerInfo.isActive()) {
                log.warn("Skipping inactive player ID: {}. Name: {} {}", playerId, playerInfo.getFirstName(), playerInfo.getLastName());
                continue;
            }

            if (!Objects.equals(playerInfo.getPlayerId(), playerId)) {
                throw new PlayerStatisticsException("Player ID mismatch between standings and player info API");
            }

            // Fetch game logs for the season
            var gameLogs = nhlApiService.getPlayerGameLogs(playerId, seasonId, gameType);

            // Create player using factory (handles all construction and statistics calculation)
            Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, seasonId);

            try {
                playerRepository.save(player);
                playerRepository.flush();
                processedCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Unique constraint violation for player {} in season {}, attempting merge: {}",
                        playerId, seasonId, e.getMessage());
                playerRepository.saveAndFlush(player);
                processedCount++;
            }
        }

        log.info("Player sync completed: {} players processed", processedCount);
    }

    /**
     * Synchronizes team standings from the NHL API for the resolved season.
     * Fetches standings (which returns teams in official rank order) and persists them.
     */
    @Transactional
    public void syncTeams() {
        String seasonId = season.getId();
        log.info("Starting team sync for season {}", seasonId);

        var standings = nhlApiService.getTeamStandings();
        int processedCount = 0;

        for (TeamStandingsDto standing : standings) {
            String teamCode = standing.getTeamAbbrev().getDefaultValue();

            Team team = teamRepository.findByTeamCodeAndSeason(teamCode, seasonId)
                    .orElse(new Team());

            team.setTeamCode(teamCode);
            team.setSeason(seasonId);
            team.setTeamName(standing.getTeamName().getDefaultValue());
            team.setLogoUrl(standing.getTeamLogo());
            team.setGamesPlayed(standing.getGamesPlayed());
            team.setWins(standing.getWins());
            team.setLosses(standing.getLosses());
            team.setOvertimeLosses(standing.getOtLosses());
            team.setPoints(standing.getPoints());
            team.setLastUpdated(LocalDateTime.now().toString());

            try {
                teamRepository.save(team);
                processedCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Constraint violation for team {} in season {}: {}",
                        teamCode, seasonId, e.getMessage());
            }
        }

        teamRepository.flush();
        log.info("Team sync completed: {} teams processed", processedCount);
    }

    /**
     * Synchronizes only players belonging to the specified teams.
     * Used during game-time sync to limit API calls to active game participants.
     */
    @Transactional
    public void syncPlayersForTeams(Set<String> teamCodes) throws PlayerStatisticsException {
        String seasonId = season.getId();
        log.info("Starting scoped player sync for teams {} in season {}", teamCodes, seasonId);

        var players = nhlApiService.getPlayerStandingsOrder(seasonId, gameType);
        int processedCount = 0;

        for (PlayerStandingDto playerStanding : players) {
            Long playerId = playerStanding.getId();

            PlayerInfoDto playerInfo = nhlApiService.getPlayerInfo(playerId);

            if (!playerInfo.isActive()) {
                continue;
            }

            if (!teamCodes.contains(playerInfo.getCurrentTeamAbbrev())) {
                continue;
            }

            if (!Objects.equals(playerInfo.getPlayerId(), playerId)) {
                throw new PlayerStatisticsException("Player ID mismatch between standings and player info API");
            }

            var gameLogs = nhlApiService.getPlayerGameLogs(playerId, seasonId, gameType);
            Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, seasonId);

            try {
                playerRepository.save(player);
                playerRepository.flush();
                processedCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Unique constraint violation for player {} in season {}: {}",
                        playerId, seasonId, e.getMessage());
                playerRepository.saveAndFlush(player);
                processedCount++;
            }
        }

        log.info("Scoped player sync completed: {} players processed for teams {}", processedCount, teamCodes);
    }

    /**
     * Synchronizes only the specified teams from standings.
     * Used during game-time sync to limit updates to active game participants.
     */
    @Transactional
    public void syncTeamsForCodes(Set<String> teamCodes) {
        String seasonId = season.getId();
        log.info("Starting scoped team sync for teams {} in season {}", teamCodes, seasonId);

        var standings = nhlApiService.getTeamStandings();
        int processedCount = 0;

        for (TeamStandingsDto standing : standings) {
            String teamCode = standing.getTeamAbbrev().getDefaultValue();

            if (!teamCodes.contains(teamCode)) {
                continue;
            }

            Team team = teamRepository.findByTeamCodeAndSeason(teamCode, seasonId)
                    .orElse(new Team());

            team.setTeamCode(teamCode);
            team.setSeason(seasonId);
            team.setTeamName(standing.getTeamName().getDefaultValue());
            team.setLogoUrl(standing.getTeamLogo());
            team.setGamesPlayed(standing.getGamesPlayed());
            team.setWins(standing.getWins());
            team.setLosses(standing.getLosses());
            team.setOvertimeLosses(standing.getOtLosses());
            team.setPoints(standing.getPoints());
            team.setLastUpdated(LocalDateTime.now().toString());

            try {
                teamRepository.save(team);
                processedCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Constraint violation for team {} in season {}: {}",
                        teamCode, seasonId, e.getMessage());
            }
        }

        teamRepository.flush();
        log.info("Scoped team sync completed: {} teams processed", processedCount);
    }

    /**
     * Returns the team codes for all teams involved in currently active (LIVE) games.
     */
    public Set<String> getActiveGameTeamCodes() {
        List<GameDto> games = nhlApiService.getLeagueSchedule();

        return games.stream()
                .filter(game -> game.getGameState() == GameState.LIVE)
                .flatMap(game -> java.util.stream.Stream.of(
                        game.getHomeTeam().getAbbrev(),
                        game.getAwayTeam().getAbbrev()))
                .collect(Collectors.toSet());
    }

    /**
     * Checks if any game from today's schedule is currently in progress.
     *
     * @return true if at least one game has gameState "LIVE"
     */
    public boolean isAnyGameActive() {
        List<GameDto> games = nhlApiService.getLeagueSchedule();

        return games.stream()
                .anyMatch(game -> game.getGameState() == GameState.LIVE);
    }
}
