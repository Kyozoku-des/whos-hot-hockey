package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.SearchResult;
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
public interface PlayerRepository extends JpaRepository<Player, Player.PlayerId> {

    /**
     * Get all players for a season ordered by points descending.
     *
     * @param season season identifier
     * @return players sorted by total points descending
     */
    List<Player> findByIdSeasonOrderByPointsDesc(String season);

    /**
     * Get players with current point streaks for a season, ordered by streak length.
     *
     * @param season season identifier
     * @return players currently on a point streak
     */
    @Query("SELECT p FROM Player p WHERE p.id.season = ?1 AND p.currentPointStreak > 0 ORDER BY p.currentPointStreak DESC")
    List<Player> findPlayersWithPointStreaks(String season);

    /**
     * Get "hot" players for a season ordered by hot flag.
     *
     * @param season season identifier
     * @return players flagged as hot for the season
     */
    @Query("SELECT p FROM Player p WHERE p.id.season = ?1 AND p.hot = true ORDER BY p.pointsPerLastNGames DESC")
    List<Player> findHotPlayers(String season);

    /**
     * Get players ordered by last N games PPG descending.
     *
     * @param season season identifier
     * @return players sorted by recent points-per-game average
     */
    @Query("SELECT p FROM Player p WHERE p.id.season = ?1 AND p.pointsPerLastNGames IS NOT NULL ORDER BY p.pointsPerLastNGames DESC")
    List<Player> findByLast10GamesPPG(String season);

    /**
     * Find players by team code for a specific season.
     *
     * @param teamCode team abbreviation code
     * @param season season identifier
     * @return players on the specified team during the specified season
     */
    List<Player> findByTeamCodeAndIdSeason(String teamCode, String season);

    /**
     * Get all players for search functionality (lightweight data).
     * Returns players ordered by last name, first name.
     *
     * @param season season identifier used to scope search results
     * @return lightweight search records for players
     */
    @Query("SELECT new com.whoshot.nhl.domain.entity.SearchResult('PLAYER', " +
           "CAST(p.id.playerId AS string), CONCAT(p.firstName, ' ', p.lastName), " +
           "p.positionCode, p.teamCode, p.headshotUrl, p.id.season) " +
           "FROM Player p WHERE p.id.season = :season " +
           "ORDER BY p.lastName, p.firstName")
    List<SearchResult> findAllForSearch(@Param("season") String season);
}
