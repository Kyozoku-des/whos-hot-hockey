package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.TeamGame;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TeamGameRepository extends JpaRepository<TeamGame, Long> {

    List<TeamGame> findByTeamCodeAndSeasonIdOrderByGameDateDesc(String teamCode, String seasonId);
}
