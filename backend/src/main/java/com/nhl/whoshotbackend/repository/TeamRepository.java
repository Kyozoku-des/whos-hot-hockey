package com.nhl.whoshotbackend.repository;

import com.nhl.whoshotbackend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
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
     */
    Optional<Team> findByTeamCodeAndSeason(String teamCode, String season);

    /**
     * Get all teams for a season ordered by points descending (standings).
     */
    List<Team> findBySeasonOrderByPointsDesc(String season);

    /**
     * Get teams with current win streaks for a season, ordered by streak length.
     */
    @Query("SELECT t FROM Team t WHERE t.season = ?1 AND t.currentWinStreak > 0 ORDER BY t.currentWinStreak DESC")
    List<Team> findTeamsWithWinStreaks(String season);

    /**
     * Get teams with current loss streaks for a season, ordered by streak length.
     */
    @Query("SELECT t FROM Team t WHERE t.season = ?1 AND t.currentLossStreak > 0 ORDER BY t.currentLossStreak DESC")
    List<Team> findTeamsWithLossStreaks(String season);
}
