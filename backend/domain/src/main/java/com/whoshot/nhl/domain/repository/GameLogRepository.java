package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.GameLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for GameLog entities.
 */
@Repository
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
}
