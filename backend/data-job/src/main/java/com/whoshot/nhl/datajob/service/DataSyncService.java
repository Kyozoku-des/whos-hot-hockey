package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.*;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
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
import java.util.Comparator;
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
    private final CurrentSeasonRepository currentSeasonRepository;
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
     * During the off-season (no season's date range contains today, e.g. between the end of
     * the regular season and the start of the next), falls back to the most recently
     * completed season rather than the newest season record, since the newest record may
     * describe a season that has not started yet and has no game data available.
     */
    private void setSeason() {
        List<SeasonDto> seasons = nhlApiService.getSeasons();
        LocalDateTime now = LocalDateTime.now();

        for (SeasonDto season : seasons) {
            LocalDateTime startDate = season.getStartDate();
            LocalDateTime endDate = season.getRegularSeasonEndDate();

            String seasonId = season.getId();
            if (now.isAfter(startDate) && now.isBefore(endDate)) {
                log.info("Current season determined: {}", seasonId);
                this.season = season;
                persistCurrentSeason(season);
                return;
            }
        }

        SeasonDto mostRecentlyCompleted = seasons.stream()
                .filter(s -> now.isAfter(s.getRegularSeasonEndDate()))
                .max(Comparator.comparing(SeasonDto::getRegularSeasonEndDate))
                .orElse(seasons.getLast());

        log.info("No current season found (off-season), using most recently completed season: {}",
                mostRecentlyCompleted.getId());
        this.season = mostRecentlyCompleted;
        persistCurrentSeason(this.season);
    }

    /**
     * Persists the resolved season as the sole active season. Deactivates any other season
     * previously marked active so that {@code findByIsActiveTrue()} always returns at most
     * one row; leaving more than one active row throws IncorrectResultSizeDataAccessException
     * for every API request that resolves the default season (players, teams, search).
     */
    @Transactional
    private void persistCurrentSeason(SeasonDto season) {
        String seasonId = season.getId();

        currentSeasonRepository.findAllByIsActiveTrue().stream()
                .filter(active -> !active.getSeasonId().equals(seasonId))
                .forEach(active -> {
                    active.setIsActive(false);
                    currentSeasonRepository.save(active);
                });

        CurrentSeason currentSeason = currentSeasonRepository.findBySeasonId(seasonId)
                .orElse(new CurrentSeason());
        currentSeason.setSeasonId(seasonId);
        currentSeason.setSeasonDisplayName(
                seasonId.substring(0, 4) + "-" + seasonId.substring(4));
        currentSeason.setIsActive(true);
        currentSeason.setLastUpdated(LocalDateTime.now().toString());
        currentSeasonRepository.save(currentSeason);
        log.info("Persisted active season to current_season table: {}", seasonId);
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

        int processedCount = persistStandings(seasonId, null);

        log.info("Team sync completed: {} teams processed", processedCount);
    }

    /**
     * Fetches standings and persists them for the resolved season.
     *
     * @param seasonId      season the standings belong to
     * @param onlyTeamCodes when non-null, only these team codes are persisted
     * @return number of teams successfully saved
     */
    private int persistStandings(String seasonId, Set<String> onlyTeamCodes) {
        var standings = nhlApiService.getTeamStandings();
        int processedCount = 0;

        for (TeamStandingsDto standing : standings) {
            String teamCode = standing.getTeamAbbrev().getDefaultValue();

            if (onlyTeamCodes != null && !onlyTeamCodes.contains(teamCode)) {
                continue;
            }

            Team team = teamRepository.findByTeamCodeAndSeason(teamCode, seasonId)
                    .orElse(new Team());

            applyStandings(team, standing, seasonId);

            try {
                teamRepository.save(team);
                processedCount++;
            } catch (DataIntegrityViolationException e) {
                log.warn("Constraint violation for team {} in season {}: {}",
                        teamCode, seasonId, e.getMessage());
            }
        }

        teamRepository.flush();
        return processedCount;
    }

    /**
     * Copies one standings row onto a Team entity.
     */
    private void applyStandings(Team team, TeamStandingsDto standing, String seasonId) {
        team.setTeamCode(standing.getTeamAbbrev().getDefaultValue());
        team.setSeason(seasonId);
        team.setTeamName(standing.getTeamName().getDefaultValue());
        team.setLogoUrl(standing.getTeamLogo());
        team.setGamesPlayed(standing.getGamesPlayed());
        team.setWins(standing.getWins());
        team.setLosses(standing.getLosses());
        team.setOvertimeLosses(standing.getOtLosses());
        team.setPoints(standing.getPoints());
        team.setPointPercentage(standing.getPointPctg());
        team.setGoalsFor(standing.getGoalFor());
        team.setGoalsAgainst(standing.getGoalAgainst());
        team.setGoalDifferential(standing.getGoalDifferential());
        team.setConferenceName(standing.getConferenceName());
        team.setDivisionName(standing.getDivisionName());

        // Streaks
        if ("W".equals(standing.getStreakCode())) {
            team.setCurrentWinStreak(standing.getStreakCount());
            team.setCurrentLossStreak(0);
        } else if ("L".equals(standing.getStreakCode()) || "OT".equals(standing.getStreakCode())) {
            team.setCurrentWinStreak(0);
            team.setCurrentLossStreak(standing.getStreakCount());
        }

        // Last 10 games
        if (standing.getL10Wins() != null && standing.getL10Losses() != null && standing.getL10OtLosses() != null) {
            int l10Games = standing.getL10Wins() + standing.getL10Losses() + standing.getL10OtLosses();
            if (l10Games > 0) {
                int l10Points = standing.getL10Wins() * 2 + standing.getL10OtLosses();
                team.setLast10GamesPointPercentage((double) l10Points / (l10Games * 2));
                team.setLast10GamesPPG((double) l10Points / l10Games);
            }
        }

        team.setLastUpdated(LocalDateTime.now().toString());
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

        int processedCount = persistStandings(seasonId, teamCodes);

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
