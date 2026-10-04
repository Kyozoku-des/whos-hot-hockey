package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Game-time sync must spend API calls only on players of teams that are playing, and must keep
 * their game logs current, not just their season totals.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSyncServiceScopedSyncTest {

    private static final String SEASON = "20252026";

    @Mock
    private NhlApiService nhlApiService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerFactory playerFactory;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private CurrentSeasonRepository currentSeasonRepository;
    @Mock
    private TeamGameRepository teamGameRepository;
    @Mock
    private GameLogWriter gameLogWriter;

    private DataSyncService service;

    @BeforeEach
    void setUp() throws Exception {
        var season = new SeasonDto();
        season.setId(SEASON);
        season.setStartDate(LocalDateTime.parse("2025-10-07T17:00:00"));
        when(nhlApiService.getSeasons()).thenReturn(List.of(season));
        when(nhlApiService.getLeagueSchedule()).thenReturn(List.of());
        when(playerFactory.createFromApiData(any(), any(), any(), any())).thenAnswer(invocation ->
                player(invocation.<PlayerInfoDto>getArgument(0).getPlayerId()));
        // A real writer over mocked repositories, so writes are observable on the mocks.
        var writer = new SeasonDataWriter(currentSeasonRepository, teamRepository, playerRepository, gameLogWriter);
        service = new DataSyncService(nhlApiService, playerRepository, playerFactory, writer,
                teamGameRepository, gameLogWriter, FetchPipeline.sequential(),
                new PlayerInfoCache(java.time.Duration.ofMinutes(30), 100));
        service.initialize();
    }

    @Test
    void scopedPlayerSync_callsApiOnlyForStoredRosterOfPlayingTeams() throws Exception {
        when(playerRepository.findByTeamCodeAndIdSeasonId("EDM", SEASON)).thenReturn(List.of(player(1L)));
        when(playerRepository.findByTeamCodeAndIdSeasonId("VAN", SEASON)).thenReturn(List.of(player(2L)));
        when(playerRepository.findPlayerIdsBySeasonId(SEASON)).thenReturn(Set.of(1L, 2L, 3L));
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2))
                .thenReturn(List.of(standing(1L), standing(2L), standing(3L)));
        when(nhlApiService.getPlayerInfo(1L)).thenReturn(info(1L, "EDM"));
        when(nhlApiService.getPlayerInfo(2L)).thenReturn(info(2L, "VAN"));
        when(nhlApiService.getPlayerGameLogs(anyLong(), eq(SEASON), eq(2))).thenReturn(List.of());

        int written = service.syncPlayersForTeams(Set.of("EDM", "VAN"));

        assertEquals(2, written);
        verify(nhlApiService, never()).getPlayerInfo(3L);
        verify(gameLogWriter).writePlayerGameLogs(eq(1L), eq(SEASON), any(), any());
        verify(gameLogWriter).writePlayerGameLogs(eq(2L), eq(SEASON), any(), any());
    }

    @Test
    void scopedPlayerSync_includesPlayersWithNoStoredRowYet() throws Exception {
        // Opening night or a season debut: the player is in the standings but was never stored.
        when(playerRepository.findPlayerIdsBySeasonId(SEASON)).thenReturn(Set.of(3L));
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2)).thenReturn(List.of(standing(1L), standing(3L)));
        when(nhlApiService.getPlayerInfo(1L)).thenReturn(info(1L, "EDM"));
        when(nhlApiService.getPlayerGameLogs(anyLong(), eq(SEASON), eq(2))).thenReturn(List.of());

        assertEquals(1, service.syncPlayersForTeams(Set.of("EDM")));
        verify(nhlApiService, never()).getPlayerInfo(3L);
        verify(gameLogWriter).writePlayerGameLogs(eq(1L), eq(SEASON), any(), any());
    }

    @Test
    void scopedPlayerSync_skipsPlayerTradedAwaySinceLastFullSync() throws Exception {
        when(playerRepository.findByTeamCodeAndIdSeasonId("EDM", SEASON)).thenReturn(List.of(player(1L)));
        when(playerRepository.findPlayerIdsBySeasonId(SEASON)).thenReturn(Set.of(1L));
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2)).thenReturn(List.of(standing(1L)));
        when(nhlApiService.getPlayerInfo(1L)).thenReturn(info(1L, "TOR"));

        assertEquals(0, service.syncPlayersForTeams(Set.of("EDM")));
        verify(nhlApiService, never()).getPlayerGameLogs(anyLong(), any(), anyInt());
        verify(playerRepository, never()).save(any());
    }

    @Test
    void consecutivePolls_reuseRecentProfiles_butAlwaysRefetchGameLogs() throws Exception {
        when(playerRepository.findByTeamCodeAndIdSeasonId("EDM", SEASON)).thenReturn(List.of(player(1L)));
        when(playerRepository.findPlayerIdsBySeasonId(SEASON)).thenReturn(Set.of(1L));
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2)).thenReturn(List.of(standing(1L)));
        when(nhlApiService.getPlayerInfo(1L)).thenReturn(info(1L, "EDM"));
        when(nhlApiService.getPlayerGameLogs(anyLong(), eq(SEASON), eq(2))).thenReturn(List.of());

        service.syncPlayersForTeams(Set.of("EDM"));
        service.syncPlayersForTeams(Set.of("EDM"));

        verify(nhlApiService, times(1)).getPlayerInfo(1L);
        verify(nhlApiService, times(2)).getPlayerGameLogs(1L, SEASON, 2);
    }

    @Test
    void fullSync_refreshesProfilesThatPollsReuse() throws Exception {
        when(playerRepository.findByTeamCodeAndIdSeasonId("EDM", SEASON)).thenReturn(List.of(player(1L)));
        when(playerRepository.findPlayerIdsBySeasonId(SEASON)).thenReturn(Set.of(1L));
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2)).thenReturn(List.of(standing(1L)));
        when(nhlApiService.getPlayerInfo(1L)).thenReturn(info(1L, "EDM"), info(1L, "TOR"));
        when(nhlApiService.getPlayerGameLogs(anyLong(), eq(SEASON), eq(2))).thenReturn(List.of());

        service.syncPlayersForTeams(Set.of("EDM"));
        service.syncPlayers(); // traded to TOR: the full sync sees it and updates the cache

        assertEquals(0, service.syncPlayersForTeams(Set.of("EDM")));
        verify(nhlApiService, times(2)).getPlayerInfo(1L);
    }

    @Test
    void teamGameSync_writesOnlyRequestedTeams() {
        when(nhlApiService.getTeamSchedule("EDM", SEASON)).thenReturn(List.of());

        assertEquals(Set.of("EDM"), service.syncTeamGamesForCodes(Set.of("EDM")));
        verify(nhlApiService).getTeamSchedule("EDM", SEASON);
        verify(gameLogWriter).writeTeamGames("EDM", SEASON, List.of());
        verifyNoMoreInteractions(gameLogWriter);
    }

    @Test
    void teamGameSync_reportsOnlyTeamsThatWereWritten() {
        when(nhlApiService.getTeamSchedule("EDM", SEASON)).thenReturn(List.of());
        when(nhlApiService.getTeamSchedule("VAN", SEASON)).thenThrow(new RuntimeException("503"));

        assertEquals(Set.of("EDM"), service.syncTeamGamesForCodes(Set.of("EDM", "VAN")));
    }

    private static Player player(long id) {
        return Player.builder().id(new Player.PlayerId(id, SEASON)).build();
    }

    private static PlayerStandingDto standing(long id) {
        var standing = new PlayerStandingDto();
        standing.setId(id);
        return standing;
    }

    private static PlayerInfoDto info(long id, String team) {
        var info = new PlayerInfoDto();
        info.setPlayerId(id);
        info.setActive(true);
        info.setCurrentTeamAbbrev(team);
        return info;
    }
}
