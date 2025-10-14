package com.nhl.whoshotbackend.repository;

import com.nhl.whoshotbackend.entity.Team;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Team entities.
 */
@Repository
public interface TeamRepository extends JpaRepository<Team, Long> {

    /**
     * Find a team by its three-letter team code.
     */
    Optional<Team> findByTeamCode(String teamCode);

    /**
     * Get all teams ordered by points descending (standings).
     */
    List<Team> findAllByOrderByPointsDesc();

    /**
     * Get teams with current win streaks, ordered by streak length.
     */
    @Query("SELECT t FROM Team t WHERE t.currentWinStreak > 0 ORDER BY t.currentWinStreak DESC")
    List<Team> findTeamsWithWinStreaks();

    /**
     * Get teams with current loss streaks, ordered by streak length.
     */
    @Query("SELECT t FROM Team t WHERE t.currentLossStreak > 0 ORDER BY t.currentLossStreak DESC")
    List<Team> findTeamsWithLossStreaks();
}
