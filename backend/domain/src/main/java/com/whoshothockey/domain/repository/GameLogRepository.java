package com.whoshothockey.domain.repository;

import com.whoshothockey.domain.entity.GameLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
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
     * @param gameDate  local game date; only earlier games are considered
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
                                    @Param("gameDate") LocalDate gameDate);

    /**
     * Each stored player's most recent game in a season, one row per player.
     * Players with no game in the season are absent.
     *
     * @param seasonId season identifier
     * @return at most one game log per player
     */
    @Query(value = """
            SELECT g.*
            FROM players p
            CROSS JOIN LATERAL (
                SELECT *
                FROM game_logs l
                WHERE l.player_id = p.player_id AND l.season_id = p.season_id
                ORDER BY l.game_date DESC
                LIMIT 1
            ) g
            WHERE p.season_id = :seasonId
            """, nativeQuery = true)
    List<GameLog> findLatestGames(@Param("seasonId") String seasonId);
}
