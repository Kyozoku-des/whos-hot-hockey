package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.SearchResult;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Set;

/**
 * Repository for Player entities.
 * Note: Uses composite key (playerId + seasonId).
 */
public interface PlayerRepository extends JpaRepository<Player, Player.PlayerId> {

    /**
     * Get all players for a season ordered by points descending.
     *
     * @param seasonId season identifier
     * @return players sorted by total points descending
     */
    List<Player> findByIdSeasonIdOrderByPointsDesc(String seasonId);

    /**
     * Get players with current point streaks for a season, ordered by streak length.
     *
     * @param seasonId season identifier
     * @return players currently on a point streak
     */
    @Query("SELECT p FROM Player p WHERE p.id.seasonId = ?1 AND p.currentPointStreak > 0 ORDER BY p.currentPointStreak DESC")
    List<Player> findPlayersWithPointStreaks(String seasonId);

    /**
     * Get players ordered by last N games PPG descending.
     *
     * @param seasonId season identifier
     * @return players sorted by recent points-per-game average
     */
    @Query("SELECT p FROM Player p WHERE p.id.seasonId = ?1 AND p.pointsPerLastNGames IS NOT NULL ORDER BY p.pointsPerLastNGames DESC")
    List<Player> findOrderedByPointsPerLastNGames(String seasonId);

    /**
     * Find players by team code for a specific season.
     *
     * @param teamCode team abbreviation code
     * @param seasonId season identifier
     * @return players on the specified team during the specified season
     */
    List<Player> findByTeamCodeAndIdSeasonId(String teamCode, String seasonId);

    /**
     * Find the players on a team's official roster for a season.
     *
     * @param teamCode team abbreviation code
     * @param seasonId season identifier
     * @return stored players listed on the team's roster
     */
    @Query("SELECT p FROM Player p, TeamRosterEntry r " +
           "WHERE r.seasonId = :seasonId AND r.teamCode = :teamCode " +
           "AND p.id.seasonId = r.seasonId AND p.id.playerId = r.playerId")
    List<Player> findRosterPlayers(@Param("teamCode") String teamCode, @Param("seasonId") String seasonId);

    /**
     * Get the IDs of every player stored for a season.
     *
     * @param seasonId season identifier
     * @return stored player IDs for the season
     */
    @Query("SELECT p.id.playerId FROM Player p WHERE p.id.seasonId = :seasonId")
    Set<Long> findPlayerIdsBySeasonId(@Param("seasonId") String seasonId);

    /**
     * Get all players for search functionality (lightweight data).
     * Returns players ordered by last name, first name.
     *
     * @param seasonId season identifier used to scope search results
     * @return lightweight search records for players
     */
    @Query("SELECT new com.whoshot.nhl.domain.entity.SearchResult('PLAYER', " +
           "CAST(p.id.playerId AS string), CONCAT(p.firstName, ' ', p.lastName), " +
           "p.positionCode, p.teamCode, p.headshotUrl) " +
           "FROM Player p WHERE p.id.seasonId = :seasonId " +
           "ORDER BY p.lastName, p.firstName")
    List<SearchResult> findAllForSearch(@Param("seasonId") String seasonId);
}
