package com.whoshot.nhl.domain.repository;

import com.whoshot.nhl.domain.entity.TeamRosterEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

/**
 * Repository for team roster entries.
 */
public interface TeamRosterRepository extends JpaRepository<TeamRosterEntry, TeamRosterEntry.TeamRosterKey> {

    List<TeamRosterEntry> findBySeasonId(String seasonId);

    List<TeamRosterEntry> findBySeasonIdAndTeamCodeIn(String seasonId, Collection<String> teamCodes);

    void deleteBySeasonIdAndTeamCode(String seasonId, String teamCode);
}
