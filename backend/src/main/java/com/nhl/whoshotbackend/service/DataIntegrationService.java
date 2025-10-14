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
import org.springframework.scheduling.annotation.Scheduled;
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
        log.info("Syncing team standings...");

        try {
            JsonNode standingsData = nhlApiService.getStandings();

            if (standingsData == null || !standingsData.has("standings")) {
                log.warn("No standings data received");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            JsonNode standings = standingsData.get("standings");
            for (JsonNode teamNode : standings) {
                Team team = parseTeamFromStandings(teamNode);
                team.setLastUpdated(timestamp);

                // Calculate streaks from team games
                calculateTeamStreaks(team);

                teamRepository.save(team);
                log.debug("Saved team: {}", team.getTeamCode());
            }

            log.info("Team standings sync completed. Total teams: {}", standings.size());
        } catch (Exception e) {
            log.error("Error syncing standings", e);
            throw new RuntimeException("Failed to sync standings", e);
        }
    }

    /**
     * Sync player statistics.
     */
    @Transactional
    public void syncPlayerStats() {
        log.info("Syncing player statistics...");

        try {
            JsonNode statsData = nhlApiService.getSkaterStatsLeaders();

            if (statsData == null) {
                log.warn("No player stats data received");
                return;
            }

            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);
            List<Player> playersToSave = new ArrayList<>();

            // The API returns categories like "goals", "assists", "points"
            if (statsData.has("points")) {
                JsonNode pointsLeaders = statsData.get("points");
                for (JsonNode playerNode : pointsLeaders) {
                    Player player = parsePlayerFromStats(playerNode);
                    player.setLastUpdated(timestamp);
                    playersToSave.add(player);
                }
            }

            // Save all players
            playerRepository.saveAll(playersToSave);

            log.info("Player statistics sync completed. Total players: {}", playersToSave.size());
        } catch (Exception e) {
            log.error("Error syncing player stats", e);
            throw new RuntimeException("Failed to sync player stats", e);
        }
    }

    /**
     * Sync game logs for a specific player.
     */
    @Transactional
    public void syncPlayerGameLogs(Long playerId) {
        log.info("Syncing game logs for player: {}", playerId);

        try {
            JsonNode gameLogData = nhlApiService.getPlayerGameLog(playerId);

            if (gameLogData == null || !gameLogData.has("gameLog")) {
                log.warn("No game log data for player: {}", playerId);
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

            log.info("Game logs synced for player: {}. Total games: {}", playerId, gameLogsToSave.size());
        } catch (Exception e) {
            log.error("Error syncing game logs for player: {}", playerId, e);
        }
    }

    /**
     * Parse team data from standings JSON.
     */
    private Team parseTeamFromStandings(JsonNode teamNode) {
        Team team = new Team();

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

        return team;
    }

    /**
     * Parse player data from stats JSON.
     */
    private Player parsePlayerFromStats(JsonNode playerNode) {
        Player player = new Player();

        player.setPlayerId(playerNode.path("id").asLong());
        player.setFirstName(playerNode.path("firstName").path("default").asText());
        player.setLastName(playerNode.path("lastName").path("default").asText());
        player.setFullName(playerNode.path("firstName").path("default").asText() + " " +
                          playerNode.path("lastName").path("default").asText());
        player.setPositionCode(playerNode.path("positionCode").asText());
        player.setTeamCode(playerNode.path("teamAbbrev").asText());
        player.setGamesPlayed(playerNode.path("gamesPlayed").asInt());
        player.setGoals(playerNode.path("goals").asInt());
        player.setAssists(playerNode.path("assists").asInt());
        player.setPoints(playerNode.path("points").asInt());

        // Calculate points per game
        if (player.getGamesPlayed() > 0) {
            player.setPointsPerGame((double) player.getPoints() / player.getGamesPlayed());
        }

        player.setPlusMinus(playerNode.path("plusMinus").asInt());
        player.setPenaltyMinutes(playerNode.path("penaltyMinutes").asInt());
        player.setPowerPlayGoals(playerNode.path("powerPlayGoals").asInt());
        player.setShorthandedGoals(playerNode.path("shorthandedGoals").asInt());
        player.setGameWinningGoals(playerNode.path("gameWinningGoals").asInt());
        player.setOvertimeGoals(playerNode.path("otGoals").asInt());
        player.setShots(playerNode.path("shots").asInt());

        if (player.getShots() > 0) {
            player.setShootingPercentage((double) player.getGoals() / player.getShots() * 100);
        }

        return player;
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
