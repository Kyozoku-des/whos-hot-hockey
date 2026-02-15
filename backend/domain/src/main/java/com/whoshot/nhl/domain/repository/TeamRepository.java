package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.SearchResult;
import com.whoshot.nhl.domain.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Team entities.
 * Note: Uses composite key (teamCode + season).
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Team.TeamKey> {

    /**
     * Find a team by its three-letter team code and season.
     *
     * @param teamCode team abbreviation code
     * @param season season identifier
     * @return matching team if one exists
     */
    Optional<Team> findByTeamCodeAndSeason(String teamCode, String season);

    /**
     * Get all teams for a season ordered by points descending (standings).
     *
     * @param season season identifier
     * @return teams sorted by standings points descending
     */
    List<Team> findBySeasonOrderByPointsDesc(String season);

    /**
     * Get teams with current win streaks for a season, ordered by streak length.
     *
     * @param season season identifier
     * @return teams currently on a win streak
     */
    @Query("SELECT t FROM Team t WHERE t.season = ?1 AND t.currentWinStreak > 0 ORDER BY t.currentWinStreak DESC")
    List<Team> findTeamsWithWinStreaks(String season);

    /**
     * Get teams with current loss streaks for a season, ordered by streak length.
     *
     * @param season season identifier
     * @return teams currently on a losing streak
     */
    @Query("SELECT t FROM Team t WHERE t.season = ?1 AND t.currentLossStreak > 0 ORDER BY t.currentLossStreak DESC")
    List<Team> findTeamsWithLossStreaks(String season);

    /**
     * Get all teams for search functionality (lightweight data).
     * Returns teams ordered by team name.
     *
     * @param season season identifier used to scope search results
     * @return lightweight search records for teams
     */
    @Query("SELECT new com.whoshot.nhl.domain.entity.SearchResult('TEAM', " +
           "t.teamCode, t.teamName, " +
           "t.teamCode, t.teamCode, t.logoUrl, t.season) " +
           "FROM Team t WHERE t.season = :season " +
           "ORDER BY t.teamName")
    List<SearchResult> findAllForSearch(@Param("season") String season);
}
