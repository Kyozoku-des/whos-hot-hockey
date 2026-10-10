package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.GameLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

/**
 * Repository for GameLog entities.
 */
public interface GameLogRepository extends JpaRepository<GameLog, Long> {

    /**
     * Get game logs for a specific player and season, ordered by game number ascending.
     *
     * @param playerId NHL player identifier
     * @param seasonId season identifier
     * @return season game logs in chronological game-number order
     */
    List<GameLog> findByPlayerIdAndSeasonIdOrderByGameNumberAsc(Long playerId, String seasonId);

    /**
     * Find game logs by player ID and season ID.
     *
     * @param playerId NHL player identifier
     * @param seasonId season identifier
     * @return all game logs for the player in the given season
     */
    List<GameLog> findByPlayerIdAndSeasonId(Long playerId, String seasonId);

    /**
     * Each given player's most recent game before a date in a season, one row per player.
     * Players with no earlier game in the season are absent.
     *
     * @param playerIds NHL player identifiers
     * @param seasonId  season identifier
     * @param gameDate  local game date ({@code yyyy-MM-dd}); only earlier games are considered
     * @return at most one game log per player
     */
    @Query(value = """
            SELECT DISTINCT ON (g.player_id) g.*
            FROM game_logs g
            WHERE g.player_id IN (:playerIds) AND g.season_id = :seasonId AND g.game_date < :gameDate
            ORDER BY g.player_id, g.game_date DESC
            """, nativeQuery = true)
    List<GameLog> findPreviousGames(@Param("playerIds") Collection<Long> playerIds,
                                    @Param("seasonId") String seasonId,
                                    @Param("gameDate") String gameDate);
}
