package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.entity.TeamRosterEntry;
import com.whoshot.nhl.domain.repository.TeamRepository;
import com.whoshot.nhl.domain.repository.TeamRosterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Keeps each team's official roster current. The roster brings in players the points leaders leave
 * out (no points yet, goalies), so they are stored and shown like every other player.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TeamRosterSync {

    private final NhlApiService nhlApiService;
    private final TeamRepository teamRepository;
    private final TeamRosterRepository teamRosterRepository;
    private final SeasonDataWriter seasonDataWriter;
    private final FetchPipeline fetchPipeline;

    /**
     * Fetches every stored team's roster in parallel and writes each one in its own transaction.
     * A team whose roster cannot be fetched keeps its previous roster.
     *
     * @return IDs of every rostered player in the season
     */
    public Set<Long> syncRosters(String seasonId) {
        List<String> teamCodes = teamRepository.findBySeasonIdOrderByPointsDesc(seasonId).stream()
                .map(Team::getTeamCode)
                .toList();
        int[] written = new int[1];
        fetchPipeline.<String, List<Long>>run(teamCodes,
                teamCode -> nhlApiService.getTeamRoster(teamCode, seasonId),
                (teamCode, roster) -> {
                    try {
                        if (!roster.succeeded()) {
                            throw roster.failure();
                        }
                        seasonDataWriter.writeRoster(seasonId, teamCode, roster.value());
                        written[0]++;
                    } catch (Exception e) {
                        IngestionFailures.rethrowIfFatal(e);
                        log.warn("Could not write roster for {} in season {}: {}", teamCode, seasonId, e.getMessage());
                    }
                });
        log.info("Roster sync completed: {} of {} team rosters written", written[0], teamCodes.size());
        return toPlayerIds(teamRosterRepository.findBySeasonId(seasonId));
    }

    /**
     * IDs of the players on the given teams' stored rosters.
     */
    public Set<Long> rosterPlayerIds(String seasonId, Collection<String> teamCodes) {
        return toPlayerIds(teamRosterRepository.findBySeasonIdAndTeamCodeIn(seasonId, teamCodes));
    }

    private static Set<Long> toPlayerIds(List<TeamRosterEntry> entries) {
        return entries.stream().map(TeamRosterEntry::getPlayerId).collect(Collectors.toSet());
    }
}
