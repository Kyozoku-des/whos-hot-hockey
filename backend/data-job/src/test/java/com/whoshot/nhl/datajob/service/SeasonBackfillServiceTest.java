package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.dto.nhlapi.TeamStandingsDto;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Orchestration tests for {@link SeasonBackfillService}: load order (teams → team games → players
 * → player game logs) and the rule that retired players are still loaded for a past season
 * (research R-002).
 */
@ExtendWith(MockitoExtension.class)
class SeasonBackfillServiceTest {

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

    private BackfillRequest request() {
        return new BackfillRequest("20232024",
                LocalDateTime.of(2023, 10, 1, 0, 0),
                LocalDateTime.of(2024, 4, 17, 0, 0),
                "2024-04-17", 2);
    }

    private TeamStandingsDto teamStanding(String code) {
        TeamStandingsDto dto = new TeamStandingsDto();
        TeamStandingsDto.LocalizedField field = new TeamStandingsDto.LocalizedField();
        field.setDefaultValue(code);
        dto.setTeamAbbrev(field);
        return dto;
    }

    @Test
    void loadOrder_isTeams_thenTeamGames_thenPlayers_thenPlayerGameLogs() throws Exception {
        service = new SeasonBackfillService(nhlApiService, dataSyncService, playerRepository,
                playerFactory, gameLogWriter, teamGameRepository, backfillLockService, 0);

        when(backfillLockService.tryLock(anyString())).thenReturn(true);
        List<TeamStandingsDto> standings = List.of(teamStanding("COL"));
        when(nhlApiService.getTeamStandings("2024-04-17")).thenReturn(standings);
        when(dataSyncService.persistStandings(eq("20232024"), any(), eq(standings))).thenReturn(1);
        when(nhlApiService.getTeamSchedule(eq("COL"), eq("20232024"))).thenReturn(List.of());
        when(gameLogWriter.writeTeamGames(anyString(), anyString(), anyList())).thenReturn(0);

        PlayerStandingDto standing = new PlayerStandingDto();
        standing.setId(8478402L);
        standing.setPoints(50);
        when(nhlApiService.getPlayerStandingsOrder("20232024", 2)).thenReturn(List.of(standing));

        PlayerInfoDto info = new PlayerInfoDto();
        info.setPlayerId(8478402L);
        info.setActive(false); // retired since the backfilled season
        info.setFirstName(name("Connor"));
        info.setLastName(name("McDavid"));
        when(nhlApiService.getPlayerInfo(8478402L)).thenReturn(info);
        when(nhlApiService.getPlayerGameLogs(eq(8478402L), eq("20232024"), eq(2))).thenReturn(List.of());
        when(playerFactory.createFromApiData(any(), any(), any(), any())).thenReturn(mock(Player.class));
        when(teamGameRepository.findBySeasonId("20232024")).thenReturn(List.of());
        when(gameLogWriter.writePlayerGameLogs(anyLong(), anyString(), anyList(), anyList())).thenReturn(0);

        InOrder inOrder = inOrder(dataSyncService, nhlApiService, gameLogWriter, playerRepository);

        BackfillSummary summary = service.run(request(), false);

        inOrder.verify(dataSyncService).persistStandings(eq("20232024"), any(), eq(standings));
        inOrder.verify(nhlApiService).getTeamSchedule("COL", "20232024");
        inOrder.verify(nhlApiService).getPlayerStandingsOrder("20232024", 2);
        inOrder.verify(nhlApiService).getPlayerGameLogs(8478402L, "20232024", 2);

        assertEquals(1, summary.playersWritten());
        assertEquals(true, summary.success());
    }

    @Test
    void retiredPlayers_areNotSkipped() throws Exception {
        service = new SeasonBackfillService(nhlApiService, dataSyncService, playerRepository,
                playerFactory, gameLogWriter, teamGameRepository, backfillLockService, 0);

        when(backfillLockService.tryLock(anyString())).thenReturn(true);
        List<TeamStandingsDto> standings = List.of(teamStanding("COL"));
        when(nhlApiService.getTeamStandings("2024-04-17")).thenReturn(standings);
        when(dataSyncService.persistStandings(anyString(), any(), anyList())).thenReturn(1);
        when(nhlApiService.getTeamSchedule(anyString(), anyString())).thenReturn(List.of());
        when(gameLogWriter.writeTeamGames(anyString(), anyString(), anyList())).thenReturn(0);

        PlayerStandingDto standing = new PlayerStandingDto();
        standing.setId(8478402L);
        standing.setPoints(50);
        when(nhlApiService.getPlayerStandingsOrder("20232024", 2)).thenReturn(List.of(standing));

        PlayerInfoDto info = new PlayerInfoDto();
        info.setPlayerId(8478402L);
        info.setActive(false); // retired
        info.setFirstName(name("Connor"));
        info.setLastName(name("McDavid"));
        when(nhlApiService.getPlayerInfo(8478402L)).thenReturn(info);
        when(nhlApiService.getPlayerGameLogs(eq(8478402L), eq("20232024"), eq(2))).thenReturn(List.of());
        when(playerFactory.createFromApiData(any(), any(), any(), any())).thenReturn(mock(Player.class));
        when(teamGameRepository.findBySeasonId("20232024")).thenReturn(List.of());
        when(gameLogWriter.writePlayerGameLogs(anyLong(), anyString(), anyList(), anyList())).thenReturn(0);

        BackfillSummary summary = service.run(request(), false);

        assertEquals(1, summary.playersWritten(), "an inactive/retired player must still be loaded for a past season");
        assertEquals(0, summary.skipped().size());
    }

    private static PlayerInfoDto.NameDto name(String value) {
        PlayerInfoDto.NameDto dto = new PlayerInfoDto.NameDto();
        dto.setName(value);
        return dto;
    }
}
