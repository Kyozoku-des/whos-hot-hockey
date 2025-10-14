package com.nhl.whoshotbackend.repository;

import com.nhl.whoshotbackend.entity.Player;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Player entities.
 */
@Repository
public interface PlayerRepository extends JpaRepository<Player, Long> {

    /**
     * Get all players ordered by points descending.
     */
    List<Player> findAllByOrderByPointsDesc();

    /**
     * Get players with current point streaks, ordered by streak length.
     */
    @Query("SELECT p FROM Player p WHERE p.currentPointStreak > 0 ORDER BY p.currentPointStreak DESC")
    List<Player> findPlayersWithPointStreaks();

    /**
     * Get "hot" players ordered by hot rating descending.
     */
    @Query("SELECT p FROM Player p WHERE p.hotRating IS NOT NULL ORDER BY p.hotRating DESC")
    List<Player> findHotPlayers();

    /**
     * Find players by team code.
     */
    List<Player> findByTeamCode(String teamCode);
}
