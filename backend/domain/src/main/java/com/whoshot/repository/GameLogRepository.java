package com.whoshot.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GameLog entities.
 */
@Repository
public interface GameLogRepository extends JpaRepository<com.whoshot.entity.GameLog, Long> {

    /**
     * Get game logs for a specific player, ordered by date descending.
     */
    List<com.whoshot.entity.GameLog> findByPlayerIdOrderByGameDateDesc(Long playerId);

    /**
     * Get the last N game logs for a player.
     */
    @Query(value = "SELECT * FROM game_logs WHERE player_id = :playerId ORDER BY game_date DESC LIMIT :limit", nativeQuery = true)
    List<com.whoshot.entity.GameLog> findLastNGamesByPlayer(@Param("playerId") Long playerId, @Param("limit") int limit);

    /**
     * Get game logs for a specific player and season, ordered by game number ascending.
     */
    @Query(value = "SELECT * FROM game_logs WHERE player_id = :playerId AND season_id = :seasonId ORDER BY game_number ASC", nativeQuery = true)
    List<com.whoshot.entity.GameLog> findByPlayerIdAndSeasonIdOrderByGameNumberAsc(@Param("playerId") Long playerId, @Param("seasonId") String seasonId);

    /**
     * Find game logs by player ID and season ID.
     */
    @Query(value = "SELECT * FROM game_logs WHERE player_id = :playerId AND season_id = :seasonId", nativeQuery = true)
    List<com.whoshot.entity.GameLog> findByPlayerIdAndSeasonId(@Param("playerId") Long playerId, @Param("seasonId") String seasonId);
}
