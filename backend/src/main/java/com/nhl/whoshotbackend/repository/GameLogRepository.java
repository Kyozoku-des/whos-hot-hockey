package com.nhl.whoshotbackend.repository;

import com.nhl.whoshotbackend.entity.GameLog;
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
     */
    List<GameLog> findByPlayerIdOrderByGameDateDesc(Long playerId);

    /**
     * Get the last N game logs for a player.
     */
    @Query(value = "SELECT * FROM game_logs WHERE player_id = :playerId ORDER BY game_date DESC LIMIT :limit", nativeQuery = true)
    List<GameLog> findLastNGamesByPlayer(@Param("playerId") Long playerId, @Param("limit") int limit);
}
