package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.GameLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GameLog entities.
 */
@Repository
public interface GameLogRepository extends JpaRepository<GameLog, Long> {

    /**
     * Get game logs for a specific player, ordered by date descending.
     *
     * @param playerId NHL player identifier
     * @return game logs sorted by most recent date first
     */
    List<GameLog> findByPlayerIdOrderByGameDateDesc(Long playerId);

    /**
     * Get the last N game logs for a player.
     *
     * @param playerId NHL player identifier
     * @param limit number of game logs to return
     * @return most recent game logs up to the requested limit
     */
    @Query(value = "SELECT * FROM game_logs WHERE player_id = :playerId ORDER BY game_date DESC LIMIT :limit", nativeQuery = true)
    List<GameLog> findLastNGamesByPlayer(@Param("playerId") Long playerId, @Param("limit") int limit);

    /**
     * Get game logs for a specific player and season, ordered by game number ascending.
     *
     * @param playerId NHL player identifier
     * @param seasonId season identifier
     * @return season game logs in chronological game-number order
     */
    @Query(value = "SELECT * FROM game_logs WHERE player_id = :playerId AND season_id = :seasonId ORDER BY game_number ASC", nativeQuery = true)
    List<GameLog> findByPlayerIdAndSeasonIdOrderByGameNumberAsc(@Param("playerId") Long playerId, @Param("seasonId") String seasonId);

    /**
     * Find game logs by player ID and season ID.
     *
     * @param playerId NHL player identifier
     * @param seasonId season identifier
     * @return all game logs for the player in the given season
     */
    @Query(value = "SELECT * FROM game_logs WHERE player_id = :playerId AND season_id = :seasonId", nativeQuery = true)
    List<GameLog> findByPlayerIdAndSeasonId(@Param("playerId") Long playerId, @Param("seasonId") String seasonId);
}
