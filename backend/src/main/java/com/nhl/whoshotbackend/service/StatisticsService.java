package com.nhl.whoshotbackend.service;

import com.nhl.whoshotbackend.entity.GameLog;
import com.nhl.whoshotbackend.entity.Player;
import com.nhl.whoshotbackend.entity.Team;
import com.nhl.whoshotbackend.repository.GameLogRepository;
import com.nhl.whoshotbackend.repository.PlayerRepository;
import com.nhl.whoshotbackend.repository.TeamRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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

    public StatisticsService(
            PlayerRepository playerRepository,
            TeamRepository teamRepository,
            GameLogRepository gameLogRepository) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.gameLogRepository = gameLogRepository;
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
        return playerRepository.findHotPlayers(season);
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
     * Calculate and update hot ratings and streak flags for all players and teams in a season.
     * This should be called after data synchronization.
     */
    @Transactional
    public void calculateHotRatings(String season) {
        log.info("Calculating hot ratings and streak flags for season {}...", season);

        // Process players
        List<Player> allPlayers = playerRepository.findBySeasonOrderByPointsDesc(season);
        for (Player player : allPlayers) {
            calculatePlayerHotRating(player);
            calculatePlayerPointStreak(player);
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
     */
    private void calculatePlayerHotRating(Player player) {
        List<GameLog> recentGames = gameLogRepository.findLastNGamesByPlayer(player.getPlayerId(), HOT_RATING_GAMES);

        if (recentGames.isEmpty()) {
            player.setHotRating(player.getPointsPerGame());
            player.setHot(false);
            player.setCold(false);
            return;
        }

        int totalPoints = recentGames.stream()
                .mapToInt(GameLog::getPoints)
                .sum();

        double hotRating = (double) totalPoints / recentGames.size();
        player.setHotRating(hotRating);

        // Hot: PPG > 1.5 over at least 3 games
        boolean isHot = recentGames.size() >= 3 && hotRating > 1.5;
        player.setHot(isHot);

        // Cold: PPG < 0.2 over at least 4 games
        boolean isCold = recentGames.size() >= 4 && hotRating < 0.2;
        player.setCold(isCold);
    }

    /**
     * Calculate current point streak for a player.
     * A point streak is consecutive games with at least one point.
     * Sets the pointStreak boolean to true if streak >= 5 games.
     */
    private void calculatePlayerPointStreak(Player player) {
        List<GameLog> recentGames = gameLogRepository.findByPlayerIdOrderByGameDateDesc(
                player.getPlayerId());

        int streak = 0;
        for (GameLog game : recentGames) {
            if (game.getPoints() > 0) {
                streak++;
            } else {
                break;
            }
        }

        player.setCurrentPointStreak(streak);

        // Point streak flag: true if at least 5 consecutive games with a point
        player.setPointStreak(streak >= 5);
    }

    /**
     * Calculate hot/cold/streak flags for a team.
     * Based on win/loss streaks:
     * - Hot: Win streak >= 3
     * - Cold: Loss streak >= 3
     * - Point streak: Win streak >= 5
     */
    private void calculateTeamStreakFlags(Team team) {
        int winStreak = team.getCurrentWinStreak() != null ? team.getCurrentWinStreak() : 0;
        int lossStreak = team.getCurrentLossStreak() != null ? team.getCurrentLossStreak() : 0;

        // Hot: Win streak of 3 or more games
        team.setHot(winStreak >= 3);

        // Cold: Loss streak of 3 or more games
        team.setCold(lossStreak >= 3);

        // Point streak: Win streak of 5 or more games
        team.setPointStreak(winStreak >= 5);
    }
}
