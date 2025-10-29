package com.nhl.whoshotbackend.service;

import com.nhl.whoshotbackend.dto.GameLogDTO;
import com.nhl.whoshotbackend.dto.TeamGameLogDTO;
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

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Service for calculating and retrieving statistics.
 * Includes business logic for hot/cold ratings, streaks, etc.
 */
@Service
@Slf4j
public class StatisticsService {

    private static final int HOT_RATING_GAMES = 3; // Number of recent games to calculate hot rating

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final GameLogRepository gameLogRepository;
    private final TeamGameRepository teamGameRepository;

    public StatisticsService(
            PlayerRepository playerRepository,
            TeamRepository teamRepository,
            GameLogRepository gameLogRepository,
            TeamGameRepository teamGameRepository) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.gameLogRepository = gameLogRepository;
        this.teamGameRepository = teamGameRepository;
    }

    /**
     * Get team standings for a season ordered by points.
     */
    public List<Team> getStandings(String season) {
        return teamRepository.findBySeasonOrderByPointsDesc(season);
    }

    /**
     * Get player point standings for a season.
     */
    public List<Player> getPlayerStandings(String season) {
        return playerRepository.findBySeasonOrderByPointsDesc(season);
    }

    /**
     * Get players with active point streaks for a season.
     */
    public List<Player> getPlayerPointStreaks(String season) {
        return playerRepository.findPlayersWithPointStreaks(season);
    }

    /**
     * Get hot players based on recent performance for a season.
     */
    public List<Player> getHotPlayers(String season) {
        log.debug("Getting hot players (by last 10 games PPG) for season: {}", season);
        return playerRepository.findByLast10GamesPPG(season);
    }

    /**
     * Get teams with win streaks for a season.
     */
    public List<Team> getTeamWinStreaks(String season) {
        return teamRepository.findTeamsWithWinStreaks(season);
    }

    /**
     * Get teams with loss streaks for a season.
     */
    public List<Team> getTeamLossStreaks(String season) {
        return teamRepository.findTeamsWithLossStreaks(season);
    }

    /**
     * Get specific player by ID and season.
     */
    public Optional<Player> getPlayer(Long playerId, String season) {
        return playerRepository.findById(new Player.PlayerKey(playerId, season));
    }

    /**
     * Get specific team by code and season.
     */
    public Optional<Team> getTeam(String teamCode, String season) {
        return teamRepository.findByTeamCodeAndSeason(teamCode, season);
    }

    /**
     * Get players on a specific team for a season.
     */
    public List<Player> getTeamPlayers(String teamCode, String season) {
        return playerRepository.findByTeamCodeAndSeason(teamCode, season);
    }

    /**
     * Get game log (points per game) for a specific player and season.
     */
    public List<GameLogDTO> getPlayerGameLog(Long playerId, String season) {
        List<GameLog> gameLogs = gameLogRepository.findByPlayerIdAndSeasonIdOrderByGameNumberAsc(playerId, season);

        return gameLogs.stream()
                .map(gameLog -> new GameLogDTO(
                        gameLog.getGameNumber(),
                        gameLog.getGameDate(),
                        gameLog.getPoints(),
                        gameLog.getGoals(),
                        gameLog.getAssists(),
                        gameLog.getOpponentTeamCode(),
                        gameLog.getHomeGame()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Get game log (wins/losses) for a specific team and season.
     */
    public List<TeamGameLogDTO> getTeamGameLog(String teamCode, String season) {
        List<TeamGame> teamGames = teamGameRepository.findByTeamCodeAndSeasonIdOrderByGameNumberAsc(teamCode, season);

        return teamGames.stream()
                .map(teamGame -> new TeamGameLogDTO(
                        teamGame.getGameNumber(),
                        teamGame.getGameDate(),
                        teamGame.getWon(),
                        teamGame.getGoalsFor(),
                        teamGame.getGoalsAgainst(),
                        teamGame.getOpponentTeamCode(),
                        teamGame.getHomeGame()
                ))
                .collect(Collectors.toList());
    }

    /**
     * Calculate and update hot ratings and streak flags for all players and teams in a season.
     * This should be called after data synchronization.
     */
    @Transactional
    public void calculateHotRatings(String season) {
        log.info("Calculating hot ratings and streak flags for season {}...", season);

        // Process players
        List<Player> allPlayers = playerRepository.findBySeasonOrderByPointsDesc(season);
        for (Player player : allPlayers) {
            // Calculate point streak first so we can use it in hot rating
            calculatePlayerPointStreak(player);
            calculatePlayerHotRating(player);
        }
        playerRepository.saveAll(allPlayers);
        log.info("Hot ratings calculated for {} players", allPlayers.size());

        // Process teams
        List<Team> allTeams = teamRepository.findBySeasonOrderByPointsDesc(season);
        for (Team team : allTeams) {
            calculateTeamStreakFlags(team);
        }
        teamRepository.saveAll(allTeams);
        log.info("Streak flags calculated for {} teams", allTeams.size());
    }

    /**
     * Calculate hot rating for a specific player based on recent games.
     * Hot rating = points per game over last N games.
     * Also sets the hot, cold, and pointStreak boolean flags.
     * Also calculates last 10 games PPG.
     */
    private void calculatePlayerHotRating(Player player) {
        List<GameLog> recentGames = gameLogRepository.findLastNGamesByPlayer(player.getPlayerId(), HOT_RATING_GAMES);

        if (recentGames.isEmpty()) {
            player.setHotRating(player.getPointsPerGame());
            player.setHot(false);
            player.setCold(false);
        } else {
            int totalPoints = recentGames.stream()
                    .mapToInt(GameLog::getPoints)
                    .sum();

            double hotRating = (double) totalPoints / recentGames.size();
            player.setHotRating(hotRating);

            // Hot: PPG > 1.5 over at least 3 games OR point streak > 5 games
            int currentStreak = player.getCurrentPointStreak() != null ? player.getCurrentPointStreak() : 0;
            boolean isHot = (recentGames.size() >= 3 && hotRating > 1.5) || (currentStreak > 5);
            player.setHot(isHot);
        }

        // Calculate last 10 games PPG for cold detection
        List<GameLog> last10Games = gameLogRepository.findLastNGamesByPlayer(player.getPlayerId(), 10);
        double last10PPG;
        if (!last10Games.isEmpty()) {
            int totalPointsLast10 = last10Games.stream()
                    .mapToInt(GameLog::getPoints)
                    .sum();
            last10PPG = (double) totalPointsLast10 / last10Games.size();
            player.setLast10GamesPPG(last10PPG);
        } else {
            last10PPG = player.getPointsPerGame();
            player.setLast10GamesPPG(last10PPG);
        }

        // Cold: 7+ games without a point OR PPG < 0.1 in last 10+ games
        int pointlessStreak = player.getCurrentPointlessStreak() != null ? player.getCurrentPointlessStreak() : 0;
        boolean isCold = (pointlessStreak >= 7) || (last10Games.size() >= 10 && last10PPG < 0.1);
        player.setCold(isCold);
    }

    /**
     * Calculate current point streak and pointless streak for a player.
     * A point streak is consecutive games with at least one point (starting from most recent).
     * A pointless streak is consecutive games without a point (starting from most recent).
     * Sets the pointStreak boolean to true if streak >= 5 games.
     */
    private void calculatePlayerPointStreak(Player player) {
        List<GameLog> recentGames = gameLogRepository.findByPlayerIdOrderByGameDateDesc(
                player.getPlayerId());

        int pointStreak = 0;
        int pointlessStreak = 0;

        // Check if player is on a point streak or pointless streak
        boolean hasRecentPoint = !recentGames.isEmpty() && recentGames.get(0).getPoints() > 0;

        if (hasRecentPoint) {
            // Currently on a point streak - count consecutive games with points
            for (GameLog game : recentGames) {
                if (game.getPoints() > 0) {
                    pointStreak++;
                } else {
                    break;
                }
            }
        } else {
            // Currently on a pointless streak - count consecutive games without points
            for (GameLog game : recentGames) {
                if (game.getPoints() == 0) {
                    pointlessStreak++;
                } else {
                    break;
                }
            }
        }

        player.setCurrentPointStreak(pointStreak);
        player.setCurrentPointlessStreak(pointlessStreak);

        // Point streak flag: true if at least 5 consecutive games with a point
        player.setPointStreak(pointStreak >= 5);
    }

    /**
     * Calculate hot/cold/streak flags for a team.
     * Based on win/loss streaks and recent performance:
     * - Hot: Win streak >= 3
     * - Cold: Loss streak >= 5 OR PPG < 0.1 in last 10+ games
     * - Point streak: Win streak >= 5
     */
    private void calculateTeamStreakFlags(Team team) {
        int winStreak = team.getCurrentWinStreak() != null ? team.getCurrentWinStreak() : 0;
        int lossStreak = team.getCurrentLossStreak() != null ? team.getCurrentLossStreak() : 0;

        // Hot: Win streak of 3 or more games
        team.setHot(winStreak >= 3);

        // Calculate last 10 games PPG for cold detection
        List<TeamGame> last10Games = teamGameRepository.findLastNGamesByTeam(team.getTeamCode(), 10);
        double last10PPG;
        if (!last10Games.isEmpty()) {
            int totalPoints = last10Games.stream()
                    .mapToInt(game -> {
                        if (game.getWon()) {
                            return 2; // Win = 2 points
                        } else if (game.getOvertimeLoss()) {
                            return 1; // OT/SO loss = 1 point
                        } else {
                            return 0; // Regulation loss = 0 points
                        }
                    })
                    .sum();
            last10PPG = (double) totalPoints / last10Games.size();
            team.setLast10GamesPPG(last10PPG);
        } else {
            // Fallback to season average
            last10PPG = team.getGamesPlayed() > 0 ?
                (double) team.getPoints() / team.getGamesPlayed() : 0.0;
            team.setLast10GamesPPG(last10PPG);
        }

        // Cold: Loss streak of 5+ games OR PPG < 0.1 in last 10+ games
        boolean isCold = (lossStreak >= 5) || (last10Games.size() >= 10 && last10PPG < 0.1);
        team.setCold(isCold);

        // Point streak: Win streak of 5 or more games
        team.setPointStreak(winStreak >= 5);
    }
}
