package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.SearchResult;
import com.whoshot.nhl.domain.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Team entities.
 * Note: Uses composite key (teamCode + seasonId).
 */
public interface TeamRepository extends JpaRepository<Team, Team.TeamKey> {

    /**
     * Find a team by its three-letter team code and season.
     *
     * @param teamCode team abbreviation code
     * @param seasonId season identifier
     * @return matching team if one exists
     */
    Optional<Team> findByTeamCodeAndSeasonId(String teamCode, String seasonId);

    /**
     * Get all teams for a season ordered by points descending (standings).
     *
     * @param seasonId season identifier
     * @return teams sorted by standings points descending
     */
    List<Team> findBySeasonIdOrderByPointsDesc(String seasonId);

    /**
     * Get teams with current win streaks for a season, ordered by streak length.
     *
     * @param seasonId season identifier
     * @return teams currently on a win streak
     */
    @Query("SELECT t FROM Team t WHERE t.seasonId = ?1 AND t.currentWinStreak > 0 ORDER BY t.currentWinStreak DESC")
    List<Team> findTeamsWithWinStreaks(String seasonId);

    /**
     * Get teams with current loss streaks for a season, ordered by streak length.
     *
     * @param seasonId season identifier
     * @return teams currently on a losing streak
     */
    @Query("SELECT t FROM Team t WHERE t.seasonId = ?1 AND t.currentLossStreak > 0 ORDER BY t.currentLossStreak DESC")
    List<Team> findTeamsWithLossStreaks(String seasonId);

    /**
     * Get all teams for search functionality (lightweight data).
     * Returns teams ordered by team name.
     *
     * @param seasonId season identifier used to scope search results
     * @return lightweight search records for teams
     */
    @Query("SELECT new com.whoshot.nhl.domain.entity.SearchResult('TEAM', " +
           "t.teamCode, t.teamName, " +
           "t.teamCode, t.teamCode, t.logoUrl) " +
           "FROM Team t WHERE t.seasonId = :seasonId " +
           "ORDER BY t.teamName")
    List<SearchResult> findAllForSearch(@Param("seasonId") String seasonId);
}
