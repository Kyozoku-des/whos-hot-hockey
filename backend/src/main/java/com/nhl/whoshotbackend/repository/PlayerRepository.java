package com.nhl.whoshotbackend.repository;

import com.nhl.whoshotbackend.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Player entities.
 * Note: Uses composite key (playerId + season).
 */
@Repository
public interface PlayerRepository extends JpaRepository<Player, Player.PlayerKey> {

    /**
     * Get all players for a season ordered by points descending.
     */
    List<Player> findBySeasonOrderByPointsDesc(String season);

    /**
     * Get players with current point streaks for a season, ordered by streak length.
     */
    @Query("SELECT p FROM Player p WHERE p.season = ?1 AND p.currentPointStreak > 0 ORDER BY p.currentPointStreak DESC")
    List<Player> findPlayersWithPointStreaks(String season);

    /**
     * Get "hot" players for a season ordered by hot rating descending.
     */
    @Query("SELECT p FROM Player p WHERE p.season = ?1 AND p.hotRating IS NOT NULL ORDER BY p.hotRating DESC")
    List<Player> findHotPlayers(String season);

    /**
     * Find players by team code for a specific season.
     */
    List<Player> findByTeamCodeAndSeason(String teamCode, String season);
}
