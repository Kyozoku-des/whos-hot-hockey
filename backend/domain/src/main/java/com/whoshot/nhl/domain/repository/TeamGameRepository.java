package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.TeamGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TeamGame entities.
 */
@Repository
public interface TeamGameRepository extends JpaRepository<TeamGame, Long> {

    List<TeamGame> findByTeamCodeAndSeasonIdOrderByGameDateDesc(String teamCode, String seasonId);

    /**
     * All team games for a season, across every team. Used to resolve a player's {@code gameWon}
     * flag during a backfill without re-fetching each team's schedule.
     *
     * @param seasonId season identifier
     * @return all team games recorded for the season
     */
    List<TeamGame> findBySeasonId(String seasonId);
}
