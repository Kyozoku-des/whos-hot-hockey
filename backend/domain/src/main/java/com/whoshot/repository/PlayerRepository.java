package com.whoshot.repository;

import com.whoshot.entity.SearchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Player entities.
 * Note: Uses composite key (playerId + season).
 */
@Repository
public interface PlayerRepository extends JpaRepository<com.whoshot.entity.Player, com.whoshot.entity.Player.PlayerId> {

    /**
     * Get all players for a season ordered by points descending.
     */
    List<com.whoshot.entity.Player> findByIdSeasonOrderByPointsDesc(String season);

    /**
     * Get players with current point streaks for a season, ordered by streak length.
     */
    @Query("SELECT p FROM Player p WHERE p.id.season = ?1 AND p.currentPointStreak > 0 ORDER BY p.currentPointStreak DESC")
    List<com.whoshot.entity.Player> findPlayersWithPointStreaks(String season);

    /**
     * Get "hot" players for a season ordered by hot flag.
     */
    @Query("SELECT p FROM Player p WHERE p.id.season = ?1 AND p.hot = true ORDER BY p.pointsPerLastNGames DESC")
    List<com.whoshot.entity.Player> findHotPlayers(String season);

    /**
     * Get players ordered by last N games PPG descending.
     */
    @Query("SELECT p FROM Player p WHERE p.id.season = ?1 AND p.pointsPerLastNGames IS NOT NULL ORDER BY p.pointsPerLastNGames DESC")
    List<com.whoshot.entity.Player> findByLast10GamesPPG(String season);

    /**
     * Find players by team code for a specific season.
     */
    List<com.whoshot.entity.Player> findByTeamCodeAndIdSeason(String teamCode, String season);

    /**
     * Get all players for search functionality (lightweight data).
     * Returns players ordered by last name, first name.
     */
    @Query("SELECT new SearchResult('PLAYER', " +
           "CAST(p.id.playerId AS string), CONCAT(p.firstName, ' ', p.lastName), " +
           "p.positionCode, p.teamCode, p.headshotUrl, p.id.season) " +
           "FROM Player p WHERE p.id.season = :season " +
           "ORDER BY p.lastName, p.firstName")
    List<SearchResult> findAllForSearch(@Param("season") String season);
}
