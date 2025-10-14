package com.nhl.whoshotbackend.repository;

import com.nhl.whoshotbackend.entity.TeamGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for TeamGame entities.
 */
@Repository
public interface TeamGameRepository extends JpaRepository<TeamGame, Long> {

    /**
     * Get games for a specific team, ordered by date descending.
     */
    List<TeamGame> findByTeamCodeOrderByGameDateDesc(String teamCode);

    /**
     * Get the last N games for a team.
     */
    @Query(value = "SELECT * FROM team_games WHERE team_code = :teamCode ORDER BY game_date DESC LIMIT :limit", nativeQuery = true)
    List<TeamGame> findLastNGamesByTeam(@Param("teamCode") String teamCode, @Param("limit") int limit);
}
