package com.whoshothockey.domain.repository;

import com.whoshothockey.domain.entity.TeamNextGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for each team's next game.
 */
public interface TeamNextGameRepository extends JpaRepository<TeamNextGame, TeamNextGame.TeamNextGameKey> {

    List<TeamNextGame> findBySeasonId(String seasonId);
}
