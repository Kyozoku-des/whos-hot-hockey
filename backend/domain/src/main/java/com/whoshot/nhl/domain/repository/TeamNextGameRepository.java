package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.TeamNextGame;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/**
 * Repository for each team's next game.
 */
public interface TeamNextGameRepository extends JpaRepository<TeamNextGame, TeamNextGame.TeamNextGameKey> {

    List<TeamNextGame> findBySeasonId(String seasonId);
}
