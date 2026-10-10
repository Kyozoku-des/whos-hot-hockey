package com.whoshot.nhl.api.service;

import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import com.whoshot.nhl.domain.repository.TeamNextGameRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TeamServiceTest {

    private static final String SEASON = "20262027";

    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private TeamService teamService;

    @BeforeEach
    void setUp() {
        teamService = new TeamService(teamRepository, new SeasonResolver(mock(CurrentSeasonRepository.class)),
                mock(TeamGameRepository.class), playerRepository, mock(TeamNextGameRepository.class));
        Team team = new Team();
        team.setTeamCode("VGK");
        when(teamRepository.findByTeamCodeAndSeasonId("VGK", SEASON)).thenReturn(Optional.of(team));
    }

    @Test
    void teamDetail_listsTheOfficialRosterWithStats() {
        when(playerRepository.findRosterPlayers("VGK", SEASON)).thenReturn(List.of(player(1L, 0, 0, 0)));
        when(playerRepository.findByTeamCodeAndIdSeasonId("VGK", SEASON)).thenReturn(List.of(player(2L, 3, 4, 7)));

        assertThat(teamService.getTeamDetail("VGK", SEASON).roster()).singleElement().satisfies(entry -> {
            assertThat(entry.playerId()).isEqualTo(1L);
            assertThat(entry.points()).isZero();
        });
    }

    @Test
    void teamDetail_withoutStoredRoster_listsTheTeamsPlayers() {
        when(playerRepository.findByTeamCodeAndIdSeasonId("VGK", SEASON)).thenReturn(List.of(player(2L, 3, 4, 7)));

        assertThat(teamService.getTeamDetail("VGK", SEASON).roster()).singleElement().satisfies(entry -> {
            assertThat(entry.playerId()).isEqualTo(2L);
            assertThat(entry.goals()).isEqualTo(3);
            assertThat(entry.assists()).isEqualTo(4);
            assertThat(entry.points()).isEqualTo(7);
        });
    }

    @Test
    void teamDetail_projectsSeasonPointsAtTheCurrentPace() {
        Team team = new Team();
        team.setTeamCode("EDM");
        team.setGamesPlayed(21);
        team.setPoints(30);
        when(teamRepository.findByTeamCodeAndSeasonId("EDM", SEASON)).thenReturn(Optional.of(team));

        var detail = teamService.getTeamDetail("EDM", SEASON);

        assertThat(detail.seasonGames()).isEqualTo(84);
        assertThat(detail.projectedPoints()).isEqualTo(120);
    }

    private static Player player(long id, int goals, int assists, int points) {
        return Player.builder().id(new Player.PlayerId(id, SEASON))
                .goals(goals).assists(assists).points(points).build();
    }
}
