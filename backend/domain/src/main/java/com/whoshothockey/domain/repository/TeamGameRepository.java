package com.whoshothockey.domain.repository;

import com.whoshothockey.domain.entity.TeamGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for TeamGame entities.
 */
public interface TeamGameRepository extends JpaRepository<TeamGame, Long> {

    List<TeamGame> findByTeamCodeAndSeasonIdOrderByGameDateDesc(String teamCode, String seasonId);

    /**
     * A team's games in a season, oldest first.
     *
     * @param teamCode team abbreviation code
     * @param seasonId season identifier
     * @return the team's games in chronological order
     */
    List<TeamGame> findByTeamCodeAndSeasonIdOrderByGameDateAsc(String teamCode, String seasonId);

    /**
     * All team games for a season, across every team. Used to resolve a player's {@code gameWon}
     * flag during a backfill without re-fetching each team's schedule.
     *
     * @param seasonId season identifier
     * @return all team games recorded for the season
     */
    List<TeamGame> findBySeasonId(String seasonId);
}
