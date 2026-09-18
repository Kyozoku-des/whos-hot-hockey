package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.TeamGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for TeamGame entities.
 */
@Repository
public interface TeamGameRepository extends JpaRepository<TeamGame, Long> {

    List<TeamGame> findByTeamCodeAndSeasonIdOrderByGameDateDesc(String teamCode, String seasonId);

    /**
     * Find the existing row for a team/game pair, used to upsert a team game without creating
     * duplicates when a load (backfill or live sync) is re-run.
     *
     * @param teamCode team abbreviation
     * @param gameId   NHL game identifier
     * @return the existing team game row, if one was already written
     */
    Optional<TeamGame> findByTeamCodeAndGameId(String teamCode, Long gameId);

    /**
     * All team games for a season, across every team. Used to resolve a player's {@code gameWon}
     * flag during a backfill without re-fetching each team's schedule.
     *
     * @param seasonId season identifier
     * @return all team games recorded for the season
     */
    List<TeamGame> findBySeasonId(String seasonId);
}
