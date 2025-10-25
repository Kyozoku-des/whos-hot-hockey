package com.nhl.whoshotbackend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.nhl.whoshotbackend.entity.CurrentSeason;
import com.nhl.whoshotbackend.entity.GameLog;
import com.nhl.whoshotbackend.entity.Player;
import com.nhl.whoshotbackend.entity.Team;
import com.nhl.whoshotbackend.entity.TeamGame;
import com.nhl.whoshotbackend.repository.CurrentSeasonRepository;
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
import java.util.Optional;

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
    private final CurrentSeasonRepository currentSeasonRepository;

    public DataIntegrationService(
            NhlApiService nhlApiService,
            TeamRepository teamRepository,
            PlayerRepository playerRepository,
            GameLogRepository gameLogRepository,
            TeamGameRepository teamGameRepository,
            CurrentSeasonRepository currentSeasonRepository) {
        this.nhlApiService = nhlApiService;
        this.teamRepository = teamRepository;
        this.playerRepository = playerRepository;
        this.gameLogRepository = gameLogRepository;
        this.teamGameRepository = teamGameRepository;
        this.currentSeasonRepository = currentSeasonRepository;
    }

    /**
     * Scheduled job to sync NHL data every 6 hours.
     * Runs at fixed delay of 6 hours after the last execution completed.
     */
    @Scheduled(fixedDelay = 21600000) // 6 hours = 21600000 milliseconds
    @Transactional
    public void scheduledDataSync() {
        log.info("=== Starting scheduled data synchronization ===");

        try {
            // Ensure current season is set
            ensureCurrentSeasonExists();

            // Sync all data
            syncAllData();

            log.info("=== Scheduled data synchronization completed successfully ===");
        } catch (Exception e) {
            log.error("Error during scheduled data synchronization", e);
        }
    }

    /**
     * Fetch and sync all NHL data.
     * This method syncs data but does not run automatically on startup.
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
     * Ensure that a current season exists in the database.
     * If not, create one using the NHL API.
     */
    @Transactional
    public void ensureCurrentSeasonExists() {
        Optional<CurrentSeason> existingSeason = currentSeasonRepository.findByIsActiveTrue();

        if (existingSeason.isEmpty()) {
            log.info("No active season found in database. Creating current season...");

            String seasonId = nhlApiService.getCurrentSeason();
            String displayName = formatSeasonDisplayName(seasonId);

            CurrentSeason currentSeason = new CurrentSeason();
            currentSeason.setSeasonId(seasonId);
            currentSeason.setSeasonDisplayName(displayName);
            currentSeason.setIsActive(true);
            currentSeason.setLastUpdated(LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

            currentSeasonRepository.save(currentSeason);
            log.info("Created current season: {} ({})", seasonId, displayName);
        } else {
            log.debug("Active season already exists: {}", existingSeason.get().getSeasonId());
        }
    }

    /**
     * Get the current active season ID.
     */
    public String getCurrentSeasonId() {
        Optional<CurrentSeason> currentSeason = currentSeasonRepository.findByIsActiveTrue();
        return currentSeason.map(CurrentSeason::getSeasonId)
                .orElse(nhlApiService.getCurrentSeason());
    }

    /**
     * Format season ID to display name.
     * Example: "20242025" -> "2024-2025"
     */
    private String formatSeasonDisplayName(String seasonId) {
        if (seasonId != null && seasonId.length() == 8) {
            return seasonId.substring(0, 4) + "-" + seasonId.substring(4);
        }
        return seasonId;
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

                // Sync team games from schedule to get game-by-game results
                syncTeamGames(teamCode, actualSeasonId);

                // Calculate streaks from team games
                calculateTeamStreaks(team);

                // Get schedule data to find next game
                JsonNode scheduleData = nhlApiService.getTeamSchedule(teamCode, actualSeasonId);
                setNextGame(team, scheduleData);

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

            // Fetch headshots and game logs for each player
            log.info("Starting headshot and game log synchronization for {} players...", playersToSave.size());
            int gameLogsFetched = 0;
            int headshotsFetched = 0;
            for (Player player : playersToSave) {
                try {
                    // Fetch headshot URL from player info
                    JsonNode playerInfo = nhlApiService.getPlayerInfo(player.getPlayerId());
                    if (playerInfo != null && playerInfo.has("headshot")) {
                        String headshotUrl = playerInfo.path("headshot").asText();
                        if (headshotUrl != null && !headshotUrl.isEmpty()) {
                            player.setHeadshotUrl(headshotUrl);
                            headshotsFetched++;
                        }
                    }

                    // Fetch game logs
                    syncPlayerGameLogs(player.getPlayerId(), actualSeasonId);
                    gameLogsFetched++;

                    if (gameLogsFetched % 50 == 0) {
                        log.info("Progress: {} / {} players (headshots: {}, game logs: {})",
                                gameLogsFetched, playersToSave.size(), headshotsFetched, gameLogsFetched);
                    }
                } catch (Exception e) {
                    log.warn("Could not fetch data for player {}: {}", player.getPlayerId(), e.getMessage());
                }
            }

            // Save players again with headshots
            playerRepository.saveAll(playersToSave);

            log.info("Player data synchronization completed. Headshots: {} / {}, Game logs: {} / {}",
                    headshotsFetched, playersToSave.size(), gameLogsFetched, playersToSave.size());

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
        String teamCode = teamNode.path("teamAbbrev").path("default").asText();
        team.setTeamCode(teamCode);
        team.setTeamName(teamNode.path("teamName").path("default").asText());
        team.setFranchiseName(teamNode.path("teamCommonName").path("default").asText());

        // Set team logo URL using NHL's standard logo URL pattern
        team.setLogoUrl(String.format("https://assets.nhle.com/logos/nhl/svg/%s_light.svg", teamCode));

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
     * Sync team games from schedule API for a specific team and season.
     * @param teamCode Team code (e.g., "COL")
     * @param seasonId Season ID (e.g., "20252026")
     */
    @Transactional
    public void syncTeamGames(String teamCode, String seasonId) {
        log.debug("Syncing team games for team: {} season: {}", teamCode, seasonId);

        try {
            JsonNode scheduleData = nhlApiService.getTeamSchedule(teamCode, seasonId);

            if (scheduleData == null || !scheduleData.has("games")) {
                log.debug("No schedule data for team: {} season: {}", teamCode, seasonId);
                return;
            }

            JsonNode games = scheduleData.get("games");
            List<TeamGame> teamGamesToSave = new ArrayList<>();

            for (JsonNode gameNode : games) {
                // Only process regular season games (gameType = 2)
                int gameType = gameNode.path("gameType").asInt();
                if (gameType != 2) {
                    continue;
                }

                // Only process completed games
                String gameState = gameNode.path("gameState").asText();
                if (!"FINAL".equals(gameState) && !"OFF".equals(gameState)) {
                    continue;
                }

                TeamGame teamGame = parseTeamGame(teamCode, gameNode);
                if (teamGame != null) {
                    teamGamesToSave.add(teamGame);
                }
            }

            // Clear existing team games for this team and season, then save new ones
            teamGameRepository.deleteAll(teamGameRepository.findLastNGamesByTeam(teamCode, 1000));
            teamGameRepository.saveAll(teamGamesToSave);

            log.debug("Team games synced for team: {} season: {}. Total games: {}",
                    teamCode, seasonId, teamGamesToSave.size());
        } catch (Exception e) {
            log.warn("Could not sync team games for team: {} season: {}: {}",
                    teamCode, seasonId, e.getMessage());
        }
    }

    /**
     * Parse a team game from schedule JSON.
     */
    private TeamGame parseTeamGame(String teamCode, JsonNode gameNode) {
        TeamGame teamGame = new TeamGame();

        teamGame.setGameId(gameNode.path("id").asLong());
        teamGame.setTeamCode(teamCode);
        teamGame.setGameDate(gameNode.path("gameDate").asText());
        teamGame.setGameType("REGULAR");

        // Determine if team is home or away
        JsonNode homeTeam = gameNode.path("homeTeam");
        JsonNode awayTeam = gameNode.path("awayTeam");

        boolean isHomeTeam = homeTeam.path("abbrev").asText().equals(teamCode);
        teamGame.setHomeGame(isHomeTeam);

        if (isHomeTeam) {
            teamGame.setOpponentTeamCode(awayTeam.path("abbrev").asText());
            teamGame.setGoalsFor(homeTeam.path("score").asInt());
            teamGame.setGoalsAgainst(awayTeam.path("score").asInt());
        } else {
            teamGame.setOpponentTeamCode(homeTeam.path("abbrev").asText());
            teamGame.setGoalsFor(awayTeam.path("score").asInt());
            teamGame.setGoalsAgainst(homeTeam.path("score").asInt());
        }

        // Determine if team won
        boolean won = teamGame.getGoalsFor() > teamGame.getGoalsAgainst();
        teamGame.setWon(won);

        // Check for overtime/shootout loss
        JsonNode periodDescriptor = gameNode.path("periodDescriptor");
        String periodType = periodDescriptor.path("periodType").asText();
        boolean isOvertimeOrShootout = "OT".equals(periodType) || "SO".equals(periodType);
        boolean overtimeLoss = !won && isOvertimeOrShootout;
        teamGame.setOvertimeLoss(overtimeLoss);

        return teamGame;
    }

    /**
     * Calculate win/loss streaks and win percentage for a team based on recent games.
     */
    private void calculateTeamStreaks(Team team) {
        List<TeamGame> recentGames = teamGameRepository.findLastNGamesByTeam(team.getTeamCode(), 10);

        int winStreak = 0;
        int lossStreak = 0;
        int winsInLast10 = 0;

        for (TeamGame game : recentGames) {
            // Count wins for last 10 games win percentage
            if (game.getWon()) {
                winsInLast10++;
            }

            // Calculate current streak (only for consecutive games from most recent)
            if (game == recentGames.get(0)) {
                // First game (most recent)
                if (game.getWon()) {
                    winStreak = 1;
                } else {
                    lossStreak = 1;
                }
            } else {
                // Subsequent games - only count if streak continues
                if (game.getWon() && winStreak > 0) {
                    winStreak++;
                } else if (!game.getWon() && lossStreak > 0) {
                    lossStreak++;
                } else {
                    // Streak broken, stop counting
                    break;
                }
            }
        }

        team.setCurrentWinStreak(winStreak);
        team.setCurrentLossStreak(lossStreak);

        // Calculate win percentage for last 10 games
        if (!recentGames.isEmpty()) {
            double winPercentage = (double) winsInLast10 / recentGames.size();
            team.setLast10GamesWinPercentage(winPercentage);
        } else {
            team.setLast10GamesWinPercentage(null);
        }
    }

    /**
     * Find and set the next game information for a team.
     */
    private void setNextGame(Team team, JsonNode scheduleData) {
        if (scheduleData == null || !scheduleData.has("games")) {
            return;
        }

        JsonNode games = scheduleData.get("games");
        for (JsonNode gameNode : games) {
            // Only check regular season games
            int gameType = gameNode.path("gameType").asInt();
            if (gameType != 2) {
                continue;
            }

            // Find first non-final game
            String gameState = gameNode.path("gameState").asText();
            if (!"FINAL".equals(gameState) && !"OFF".equals(gameState)) {
                // This is the next game
                JsonNode homeTeam = gameNode.path("homeTeam");
                JsonNode awayTeam = gameNode.path("awayTeam");

                boolean isHomeTeam = homeTeam.path("abbrev").asText().equals(team.getTeamCode());
                team.setNextGameIsHome(isHomeTeam);
                team.setNextGameDate(gameNode.path("gameDate").asText());

                if (isHomeTeam) {
                    team.setNextOpponentCode(awayTeam.path("abbrev").asText());
                } else {
                    team.setNextOpponentCode(homeTeam.path("abbrev").asText());
                }

                break; // Found the next game, stop searching
            }
        }
    }
}
