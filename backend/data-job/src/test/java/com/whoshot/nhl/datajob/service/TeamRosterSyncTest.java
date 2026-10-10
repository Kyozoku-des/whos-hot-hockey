package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.entity.TeamRosterEntry;
import com.whoshot.nhl.domain.repository.TeamRepository;
import com.whoshot.nhl.domain.repository.TeamRosterRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Every stored team's roster is written on its own; a team that cannot be fetched keeps its
 * previous roster.
 */
class TeamRosterSyncTest {

    private static final String SEASON = "20262027";

    private final NhlApiService nhlApiService = mock(NhlApiService.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final TeamRosterRepository teamRosterRepository = mock(TeamRosterRepository.class);
    private final SeasonDataWriter seasonDataWriter = mock(SeasonDataWriter.class);
    private final TeamRosterSync sync = new TeamRosterSync(nhlApiService, teamRepository, teamRosterRepository,
            seasonDataWriter, FetchPipeline.sequential());

    @Test
    void syncRosters_writesEachFetchedRosterAndReturnsStoredRosterIds() {
        when(teamRepository.findBySeasonIdOrderByPointsDesc(SEASON)).thenReturn(List.of(team("VGK"), team("EDM")));
        when(nhlApiService.getTeamRoster("VGK", SEASON)).thenReturn(List.of(1L, 2L));
        when(nhlApiService.getTeamRoster("EDM", SEASON)).thenThrow(new ApiClientException("roster 503"));
        when(teamRosterRepository.findBySeasonId(SEASON)).thenReturn(List.of(
                new TeamRosterEntry(SEASON, 1L, "VGK", null),
                new TeamRosterEntry(SEASON, 2L, "VGK", null),
                new TeamRosterEntry(SEASON, 3L, "EDM", null)));

        assertThat(sync.syncRosters(SEASON)).containsExactlyInAnyOrder(1L, 2L, 3L);
        verify(seasonDataWriter).writeRoster(SEASON, "VGK", List.of(1L, 2L));
        verify(seasonDataWriter, never()).writeRoster(eq(SEASON), eq("EDM"), any());
    }

    @Test
    void rosterPlayerIds_readsOnlyTheGivenTeams() {
        when(teamRosterRepository.findBySeasonIdAndTeamCodeIn(SEASON, Set.of("VGK")))
                .thenReturn(List.of(new TeamRosterEntry(SEASON, 1L, "VGK", null)));

        assertThat(sync.rosterPlayerIds(SEASON, Set.of("VGK"))).containsExactly(1L);
    }

    private static Team team(String teamCode) {
        Team team = new Team();
        team.setTeamCode(teamCode);
        return team;
    }
}
