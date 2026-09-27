package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.dto.nhlapi.TeamStandingsDto;
import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Failure isolation for {@link SeasonBackfillService} (FR-007, research R-012): a
 * {@link PlayerStatisticsException} or an exhausted-retry {@link ApiClientException} skips only
 * the affected player and names it in the summary with a reason, while failing to fetch the
 * season-wide standings is fatal to the run.
 */
@ExtendWith(MockitoExtension.class)
class BackfillFailureIsolationTest {

    private static final String SEASON = "20232024";
    private static final long BROKEN_PLAYER = 8470001L;
    private static final long HEALTHY_PLAYER = 8470002L;

    @Mock
    private NhlApiService nhlApiService;
    @Mock
    private DataSyncService dataSyncService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerFactory playerFactory;
    @Mock
    private GameLogWriter gameLogWriter;
    @Mock
    private TeamGameRepository teamGameRepository;
    @Mock
    private BackfillLockService backfillLockService;

    private SeasonBackfillService service;

    @BeforeEach
    void setUp() {
        service = new SeasonBackfillService(nhlApiService, dataSyncService, playerRepository,
                playerFactory, gameLogWriter, teamGameRepository, backfillLockService, 0);
        when(backfillLockService.tryLock(SEASON)).thenReturn(true);
    }

    @Test
    void playerStatisticsException_skipsOnlyThatPlayer_withReason() throws Exception {
        stubTeamsAndTwoPlayers();
        when(nhlApiService.getPlayerInfo(BROKEN_PLAYER)).thenReturn(info(BROKEN_PLAYER));
        when(nhlApiService.getPlayerInfo(HEALTHY_PLAYER)).thenReturn(info(HEALTHY_PLAYER));
        when(nhlApiService.getPlayerGameLogs(anyLong(), eq(SEASON), eq(2))).thenReturn(List.of());
        Player healthy = mock(Player.class);
        when(playerFactory.createFromApiData(any(), any(), any(), eq(SEASON))).thenAnswer(invocation -> {
            PlayerInfoDto info = invocation.getArgument(0);
            if (info.getPlayerId() == BROKEN_PLAYER) {
                throw new PlayerStatisticsException("points mismatch: standings 50, game logs 49");
            }
            return healthy;
        });

        BackfillSummary summary = service.run(request(), false);

        assertTrue(summary.success(), "one bad player must not fail the season");
        assertEquals(1, summary.playersWritten());
        assertEquals(List.of(new BackfillSummary.Skip("player", String.valueOf(BROKEN_PLAYER),
                "points mismatch: standings 50, game logs 49")), summary.skipped());
        verify(playerRepository).save(healthy);
        verify(gameLogWriter).writePlayerGameLogs(eq(HEALTHY_PLAYER), eq(SEASON), anyList(), anyList());
        verify(gameLogWriter, never()).writePlayerGameLogs(eq(BROKEN_PLAYER), anyString(), anyList(), anyList());
    }

    @Test
    void exhaustedRetryApiClientException_skipsOnlyThatPlayer_withReason() throws Exception {
        stubTeamsAndTwoPlayers();
        when(nhlApiService.getPlayerInfo(BROKEN_PLAYER))
                .thenThrow(new ApiClientException("GET player/8470001/landing failed after 3 attempts"));
        when(nhlApiService.getPlayerInfo(HEALTHY_PLAYER)).thenReturn(info(HEALTHY_PLAYER));
        when(nhlApiService.getPlayerGameLogs(HEALTHY_PLAYER, SEASON, 2)).thenReturn(List.of());
        when(playerFactory.createFromApiData(any(), any(), any(), eq(SEASON))).thenReturn(mock(Player.class));

        BackfillSummary summary = service.run(request(), false);

        assertTrue(summary.success());
        assertEquals(1, summary.playersWritten());
        assertEquals(1, summary.skipped().size());
        BackfillSummary.Skip skip = summary.skipped().getFirst();
        assertEquals("player", skip.kind());
        assertEquals(String.valueOf(BROKEN_PLAYER), skip.identifier());
        assertTrue(skip.reason().contains("failed after 3 attempts"), skip.reason());
        verify(nhlApiService, never()).getPlayerGameLogs(eq(BROKEN_PLAYER), anyString(), eq(2));
    }

    @Test
    void failedTeamSchedule_skipsOnlyThatTeam_withReason() throws Exception {
        List<TeamStandingsDto> standings = List.of(teamStanding("COL"), teamStanding("MTL"));
        when(nhlApiService.getTeamStandings("2024-04-17")).thenReturn(standings);
        when(dataSyncService.persistStandings(SEASON, null, standings)).thenReturn(2);
        when(nhlApiService.getTeamSchedule("COL", SEASON)).thenThrow(new ApiClientException("schedule 503"));
        when(nhlApiService.getTeamSchedule("MTL", SEASON)).thenReturn(List.of());
        when(gameLogWriter.writeTeamGames("MTL", SEASON, List.of())).thenReturn(0);
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2)).thenReturn(List.of());

        BackfillSummary summary = service.run(request(), false);

        assertTrue(summary.success());
        assertEquals(2, summary.teamsWritten());
        assertEquals(List.of(new BackfillSummary.Skip("team", "COL", "schedule 503")), summary.skipped());
    }

    @Test
    void failedStandingsFetch_isFatal_andWritesNothing() {
        when(nhlApiService.getTeamStandings("2024-04-17")).thenThrow(new ApiClientException("standings 503"));

        BackfillSummary summary = service.run(request(), false);

        assertFalse(summary.success());
        verifyNoInteractions(dataSyncService, playerRepository, gameLogWriter);
        verify(backfillLockService).unlock(SEASON);
    }

    @Test
    void emptyStandingsResponse_isFatal() {
        when(nhlApiService.getTeamStandings("2024-04-17")).thenReturn(List.of());

        BackfillSummary summary = service.run(request(), false);

        assertFalse(summary.success());
        verifyNoInteractions(dataSyncService, playerRepository, gameLogWriter);
    }

    @Test
    void failedPlayerStandingsFetch_isFatal() {
        List<TeamStandingsDto> standings = List.of(teamStanding("COL"));
        when(nhlApiService.getTeamStandings("2024-04-17")).thenReturn(standings);
        when(dataSyncService.persistStandings(SEASON, null, standings)).thenReturn(1);
        when(nhlApiService.getTeamSchedule("COL", SEASON)).thenReturn(List.of());
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2)).thenThrow(new ApiClientException("skater summary 503"));

        BackfillSummary summary = service.run(request(), false);

        assertFalse(summary.success());
        verifyNoInteractions(playerRepository);
    }

    private void stubTeamsAndTwoPlayers() {
        List<TeamStandingsDto> standings = List.of(teamStanding("COL"));
        when(nhlApiService.getTeamStandings("2024-04-17")).thenReturn(standings);
        when(dataSyncService.persistStandings(SEASON, null, standings)).thenReturn(1);
        when(nhlApiService.getTeamSchedule("COL", SEASON)).thenReturn(List.of());
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2))
                .thenReturn(List.of(standing(BROKEN_PLAYER), standing(HEALTHY_PLAYER)));
        when(teamGameRepository.findBySeasonId(SEASON)).thenReturn(List.of());
    }

    private static BackfillRequest request() {
        return new BackfillRequest(SEASON,
                LocalDateTime.of(2023, 10, 1, 0, 0),
                LocalDateTime.of(2024, 4, 17, 0, 0),
                "2024-04-17", 2);
    }

    private static TeamStandingsDto teamStanding(String code) {
        TeamStandingsDto dto = new TeamStandingsDto();
        TeamStandingsDto.LocalizedField field = new TeamStandingsDto.LocalizedField();
        field.setDefaultValue(code);
        dto.setTeamAbbrev(field);
        return dto;
    }

    private static PlayerStandingDto standing(long id) {
        PlayerStandingDto dto = new PlayerStandingDto();
        dto.setId(id);
        dto.setPoints(50);
        return dto;
    }

    private static PlayerInfoDto info(long id) {
        PlayerInfoDto dto = new PlayerInfoDto();
        dto.setPlayerId(id);
        return dto;
    }
}
