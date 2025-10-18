package com.nhl.whoshotbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhl.whoshotbackend.entity.GameLog;
import com.nhl.whoshotbackend.entity.Player;
import com.nhl.whoshotbackend.entity.Team;
import com.nhl.whoshotbackend.entity.TeamGame;
import com.nhl.whoshotbackend.repository.GameLogRepository;
import com.nhl.whoshotbackend.repository.PlayerRepository;
import com.nhl.whoshotbackend.repository.TeamGameRepository;
import com.nhl.whoshotbackend.repository.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Service responsible for fetching data from NHL API and persisting to database.
 */
@Service
@Slf4j
public class DataIntegrationService {

    private final NhlApiService nhlApiService;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final GameLogRepository gameLogRepository;
    private final TeamGameRepository teamGameRepository;

    public DataIntegrationService(
            NhlApiService nhlApiService,
            TeamRepository teamRepository,
            PlayerRepository playerRepository,
            GameLogRepository gameLogRepository,
            TeamGameRepository teamGameRepository) {
        this.nhlApiService = nhlApiService;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.gameLogRepository = gameLogRepository;
        this.teamGameRepository = teamGameRepository;
    }

    /**
     * Fetch and sync all NHL data.
     * This method is called on startup and scheduled to run periodically.
     */
    @Transactional
    public void syncAllData() {
        log.info("Starting full data synchronization...");

        try {
            syncStandings();
            syncPlayerStats();
            log.info("Data synchronization completed successfully");
        } catch (Exception e) {
            log.error("Error during data synchronization", e);
            throw e;
        }
    }

    /**
     * Sync team standings data.
     */
    @Transactional
    public void syncStandings() {
        syncStandings(null); // null means use current season
    }

    @Transactional
    public void syncStandings(String seasonId) {
        // Determine actual season
        String actualSeasonId = seasonId != null ? seasonId : nhlApiService.getCurrentSeason();

        log.info("Syncing team standings for season: {}...", actualSeasonId);

        try {
            JsonNode standingsData = nhlApiService.getStandings();

            if (standingsData == null || !standingsData.has("standings")) {
                log.warn("No standings data received");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            JsonNode standings = standingsData.get("standings");
            for (JsonNode teamNode : standings) {
                String teamCode = teamNode.path("teamAbbrev").path("default").asText();

                // Create composite key to check if team+season exists
                Team.TeamKey teamKey = new Team.TeamKey(teamCode, actualSeasonId);
                Team team = teamRepository.findById(teamKey)
                        .orElse(new Team());

                // Update team data from API
                updateTeamFromStandings(team, teamNode);
                team.setSeason(actualSeasonId); // Set season
                team.setLastUpdated(timestamp);

                // Calculate streaks from team games
                calculateTeamStreaks(team);

                teamRepository.save(team);
                log.debug("Saved team: {} for season: {}", team.getTeamCode(), actualSeasonId);
            }

            log.info("Team standings sync completed. Total teams: {}", standings.size());
        } catch (Exception e) {
            log.error("Error syncing standings", e);
            throw new RuntimeException("Failed to sync standings", e);
        }
    }

    /**
     * Sync player statistics for the current season.
     */
    @Transactional
    public void syncPlayerStats() {
        syncPlayerStats(null); // null means use current season
    }

    /**
     * Sync player statistics for a specific season.
     * @param seasonId Season ID in format YYYYYYYY (e.g., 20252026), or null for current season
     */
    @Transactional
    public void syncPlayerStats(String seasonId) {
        // Get actual season ID (use current if null)
        String actualSeasonId = seasonId;
        if (actualSeasonId == null) {
            // Read from config
            actualSeasonId = nhlApiService.getAllSkaterStats().path("seasonId").asText();
            if (actualSeasonId == null || actualSeasonId.isEmpty()) {
                actualSeasonId = com.nhl.whoshotbackend.util.SeasonValidator.getCurrentSeasonId();
            }
        }

        log.info("Syncing player statistics for season: {}...", actualSeasonId);

        try {
            JsonNode statsData = seasonId != null
                ? nhlApiService.getAllSkaterStats(seasonId)
                : nhlApiService.getAllSkaterStats();

            if (statsData == null) {
                log.warn("No player stats data received");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            List<Player> playersToSave = new ArrayList<>();

            // The Stats API returns data in a "data" array
            if (statsData.has("data")) {
                JsonNode playersData = statsData.get("data");
                for (JsonNode playerNode : playersData) {
                    Long playerId = playerNode.path("playerId").asLong();

                    // Create composite key to check if player+season exists
                    Player.PlayerKey playerKey = new Player.PlayerKey(playerId, actualSeasonId);
                    Player player = playerRepository.findById(playerKey)
                            .orElse(new Player());

                    // Update player data from API
                    updatePlayerFromStats(player, playerNode);
                    player.setSeason(actualSeasonId); // Set season
                    player.setLastUpdated(timestamp);
                    playersToSave.add(player);
                }
            }

            // Save all players
            playerRepository.saveAll(playersToSave);

            log.info("Player statistics sync completed. Total players: {}", playersToSave.size());

            // Now sync game logs for each player to enable streak calculations
            log.info("Starting game log synchronization for {} players...", playersToSave.size());
            int gameLogsFetched = 0;
            for (Player player : playersToSave) {
                try {
                    syncPlayerGameLogs(player.getPlayerId(), actualSeasonId);
                    gameLogsFetched++;
                    if (gameLogsFetched % 50 == 0) {
                        log.info("Game logs fetched for {} / {} players", gameLogsFetched, playersToSave.size());
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch game logs for player {}: {}", player.getPlayerId(), e.getMessage());
                }
            }
            log.info("Game log synchronization completed. Fetched logs for {} players", gameLogsFetched);

        } catch (Exception e) {
            log.error("Error syncing player stats", e);
            throw new RuntimeException("Failed to sync player stats", e);
        }
    }

    /**
     * Sync game logs for a specific player (current season).
     */
    @Transactional
    public void syncPlayerGameLogs(Long playerId) {
        syncPlayerGameLogs(playerId, nhlApiService.getCurrentSeason());
    }

    /**
     * Sync game logs for a specific player and season.
     * @param playerId The player's NHL ID
     * @param seasonId The season ID (e.g., "20252026")
     */
    @Transactional
    public void syncPlayerGameLogs(Long playerId, String seasonId) {
        log.debug("Syncing game logs for player: {} season: {}", playerId, seasonId);

        try {
            JsonNode gameLogData = nhlApiService.getPlayerGameLog(playerId, seasonId, 2); // 2 = regular season

            if (gameLogData == null || !gameLogData.has("gameLog")) {
                log.debug("No game log data for player: {} season: {}", playerId, seasonId);
                return;
            }

            JsonNode gameLogs = gameLogData.get("gameLog");
            List<GameLog> gameLogsToSave = new ArrayList<>();

            for (JsonNode gameNode : gameLogs) {
                GameLog gameLog = parseGameLog(playerId, gameNode);
                gameLogsToSave.add(gameLog);
            }

            // Clear existing game logs for this player and save new ones
            gameLogRepository.deleteAll(gameLogRepository.findByPlayerIdOrderByGameDateDesc(playerId));
            gameLogRepository.saveAll(gameLogsToSave);

            log.debug("Game logs synced for player: {} season: {}. Total games: {}", playerId, seasonId, gameLogsToSave.size());
        } catch (Exception e) {
            log.warn("Could not sync game logs for player: {} season: {}: {}", playerId, seasonId, e.getMessage());
        }
    }

    /**
     * Update team data from standings JSON.
     */
    private void updateTeamFromStandings(Team team, JsonNode teamNode) {
        team.setTeamCode(teamNode.path("teamAbbrev").path("default").asText());
        team.setTeamName(teamNode.path("teamName").path("default").asText());
        team.setFranchiseName(teamNode.path("teamCommonName").path("default").asText());
        team.setGamesPlayed(teamNode.path("gamesPlayed").asInt());
        team.setWins(teamNode.path("wins").asInt());
        team.setLosses(teamNode.path("losses").asInt());
        team.setOvertimeLosses(teamNode.path("otLosses").asInt());
        team.setPoints(teamNode.path("points").asInt());
        team.setPointPercentage(teamNode.path("pointPctg").asDouble());
        team.setGoalsFor(teamNode.path("goalFor").asInt());
        team.setGoalsAgainst(teamNode.path("goalAgainst").asInt());
        team.setGoalDifferential(teamNode.path("goalDifferential").asInt());
        team.setConferenceName(teamNode.path("conferenceName").asText());
        team.setDivisionName(teamNode.path("divisionName").asText());
    }

    /**
     * Update player data from stats JSON.
     * Updated to work with the Stats API response format.
     */
    private void updatePlayerFromStats(Player player, JsonNode playerNode) {
        // Stats API uses different field names than the Web API
        player.setPlayerId(playerNode.path("playerId").asLong());

        // Parse full name (Stats API returns "skaterFullName" as "FirstName LastName")
        String fullName = playerNode.path("skaterFullName").asText();
        String[] nameParts = fullName.split(" ", 2);
        if (nameParts.length >= 2) {
            player.setFirstName(nameParts[0]);
            player.setLastName(nameParts[1]);
        } else {
            player.setFirstName(fullName);
            player.setLastName(playerNode.path("lastName").asText());
        }
        player.setFullName(fullName);

        player.setPositionCode(playerNode.path("positionCode").asText());
        // Stats API uses "teamAbbrevs" which can be multiple teams (handle first one)
        String teamAbbrevs = playerNode.path("teamAbbrevs").asText();
        player.setTeamCode(teamAbbrevs.split(",")[0].trim());

        player.setGamesPlayed(playerNode.path("gamesPlayed").asInt());
        player.setGoals(playerNode.path("goals").asInt());
        player.setAssists(playerNode.path("assists").asInt());
        player.setPoints(playerNode.path("points").asInt());

        // Stats API provides pointsPerGame directly
        double ppg = playerNode.path("pointsPerGame").asDouble(0.0);
        player.setPointsPerGame(ppg > 0 ? ppg : null);

        player.setPlusMinus(playerNode.path("plusMinus").asInt());
        player.setPenaltyMinutes(playerNode.path("penaltyMinutes").asInt());
        player.setPowerPlayGoals(playerNode.path("ppGoals").asInt());
        player.setShorthandedGoals(playerNode.path("shGoals").asInt());
        player.setGameWinningGoals(playerNode.path("gameWinningGoals").asInt());
        player.setOvertimeGoals(playerNode.path("otGoals").asInt());
        player.setShots(playerNode.path("shots").asInt());

        // Stats API provides shootingPct directly as a decimal (e.g., 0.11522 for 11.522%)
        double shootPct = playerNode.path("shootingPct").asDouble(0.0);
        player.setShootingPercentage(shootPct > 0 ? shootPct * 100 : null);
    }

    /**
     * Parse game log from JSON.
     */
    private GameLog parseGameLog(Long playerId, JsonNode gameNode) {
        GameLog gameLog = new GameLog();

        gameLog.setPlayerId(playerId);
        gameLog.setGameId(gameNode.path("gameId").asLong());
        gameLog.setGameDate(gameNode.path("gameDate").asText());
        gameLog.setOpponentTeamCode(gameNode.path("opponentAbbrev").asText());
        gameLog.setHomeGame(gameNode.path("homeRoadFlag").asText().equals("H"));
        gameLog.setGoals(gameNode.path("goals").asInt());
        gameLog.setAssists(gameNode.path("assists").asInt());
        gameLog.setPoints(gameNode.path("points").asInt());
        gameLog.setPlusMinus(gameNode.path("plusMinus").asInt());
        gameLog.setShots(gameNode.path("shots").asInt());

        // Parse time on ice (format: "MM:SS")
        String toi = gameNode.path("toi").asText();
        if (toi != null && !toi.isEmpty()) {
            String[] parts = toi.split(":");
            if (parts.length == 2) {
                gameLog.setTimeOnIce(Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]));
            }
        }

        return gameLog;
    }

    /**
     * Calculate win/loss streaks for a team based on recent games.
     */
    private void calculateTeamStreaks(Team team) {
        List<TeamGame> recentGames = teamGameRepository.findLastNGamesByTeam(team.getTeamCode(), 10);

        int winStreak = 0;
        int lossStreak = 0;

        for (TeamGame game : recentGames) {
            if (game.getWon()) {
                winStreak++;
                lossStreak = 0;
            } else {
                lossStreak++;
                winStreak = 0;
            }
        }

        team.setCurrentWinStreak(winStreak);
        team.setCurrentLossStreak(lossStreak);
    }
}
