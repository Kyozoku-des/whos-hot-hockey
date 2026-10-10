package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.stream.LongStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atMost;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Full player sync over a four-worker {@link FetchPipeline} (issue #29): writes stay in standings
 * order whatever order fetches finish in, record-level failures skip only their record, and a lost
 * database stops the sync.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DataSyncServiceParallelSyncTest {

    private static final String SEASON = "20252026";
    private static final List<Long> PLAYER_IDS = LongStream.rangeClosed(1, 30).boxed().toList();

    @Mock
    private NhlApiService nhlApiService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerFactory playerFactory;
    @Mock
    private SeasonDataWriter seasonDataWriter;
    @Mock
    private TeamGameRepository teamGameRepository;
    @Mock
    private GameLogWriter gameLogWriter;

    private final FetchPipeline pipeline = new FetchPipeline(4);
    private DataSyncService service;

    @BeforeEach
    void setUp() throws Exception {
        var season = new SeasonDto();
        season.setId(SEASON);
        season.setStartDate(LocalDateTime.parse("2025-10-07T17:00:00"));
        when(nhlApiService.getSeasons()).thenReturn(List.of(season));
        when(nhlApiService.getLeagueSchedule()).thenReturn(List.of());
        when(nhlApiService.getPlayerStandingsOrder(SEASON, 2))
                .thenReturn(PLAYER_IDS.stream().map(DataSyncServiceParallelSyncTest::standing).toList());
        when(nhlApiService.getPlayerInfo(anyLong())).thenAnswer(invocation -> {
            jitter();
            return info(invocation.getArgument(0));
        });
        when(nhlApiService.getPlayerGameLogs(anyLong(), eq(SEASON), eq(2))).thenAnswer(invocation -> {
            jitter();
            return List.of();
        });
        when(playerFactory.createFromApiData(any(), any(), any(), any())).thenAnswer(invocation ->
                player(invocation.<PlayerInfoDto>getArgument(0).getPlayerId()));
        when(teamGameRepository.findBySeasonId(SEASON)).thenReturn(List.of());
        service = new DataSyncService(nhlApiService, playerRepository, playerFactory, seasonDataWriter,
                teamGameRepository, gameLogWriter, pipeline,
                new PlayerInfoCache(java.time.Duration.ofMinutes(30), 100), org.mockito.Mockito.mock(TeamRosterSync.class));
        service.initialize();
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        pipeline.destroy();
    }

    @Test
    void playersAreWrittenInStandingsOrder_whateverOrderFetchesFinishIn() {
        DataSyncService.SyncResult result = service.syncPlayers();

        assertEquals(new DataSyncService.SyncResult(30, 0, 0, 0), result);
        ArgumentCaptor<Player> written = ArgumentCaptor.forClass(Player.class);
        verify(seasonDataWriter, times(30)).writePlayer(written.capture(), anyList(), any());
        assertEquals(PLAYER_IDS, written.getAllValues().stream().map(p -> p.getId().playerId()).toList());
    }

    @Test
    void recordLevelFailures_skipOnlyTheirPlayer_andTotalsMismatchIsDeferred() throws Exception {
        doThrow(new ApiClientException("HTTP 503")).when(nhlApiService).getPlayerInfo(5L);
        doReturn(inactive(6L)).when(nhlApiService).getPlayerInfo(6L);
        doAnswer(invocation -> {
            long id = invocation.<PlayerInfoDto>getArgument(0).getPlayerId();
            if (id == 7L) {
                throw new PlayerStatisticsException("points mismatch: standings 10, game logs 11");
            }
            return player(id);
        }).when(playerFactory).createFromApiData(any(), any(), any(), any());

        DataSyncService.SyncResult result = service.syncPlayers();

        assertEquals(new DataSyncService.SyncResult(27, 1, 2, 1), result);
        verify(seasonDataWriter, times(27)).writePlayer(any(), anyList(), any());
    }

    @Test
    void lostDatabase_stopsTheSync() {
        doThrow(new DataAccessResourceFailureException("connection refused"))
                .when(seasonDataWriter).writePlayer(any(), anyList(), any());

        assertThrows(DataAccessResourceFailureException.class, service::syncPlayers);
        verify(seasonDataWriter, times(1)).writePlayer(any(), anyList(), any());
        // At most the bounded window was fetched ahead before the run stopped.
        verify(nhlApiService, atMost(9)).getPlayerInfo(anyLong());
    }

    @Test
    void teamSchedules_failedTeamsAreLeftOut_othersWritten() {
        when(nhlApiService.getTeamSchedule("EDM", SEASON)).thenReturn(List.of());
        when(nhlApiService.getTeamSchedule("VAN", SEASON)).thenThrow(new ApiClientException("HTTP 502"));
        when(nhlApiService.getTeamSchedule("CGY", SEASON)).thenReturn(List.of());

        assertEquals(Set.of("EDM", "CGY"), service.syncTeamGamesForCodes(Set.of("EDM", "VAN", "CGY")));
        verify(gameLogWriter).writeTeamGames("EDM", SEASON, List.of());
        verify(gameLogWriter).writeTeamGames("CGY", SEASON, List.of());
    }

    private static void jitter() throws InterruptedException {
        TimeUnit.MILLISECONDS.sleep(ThreadLocalRandom.current().nextInt(0, 6));
    }

    private static Player player(long id) {
        return Player.builder().id(new Player.PlayerId(id, SEASON)).build();
    }

    private static PlayerStandingDto standing(long id) {
        var standing = new PlayerStandingDto();
        standing.setId(id);
        return standing;
    }

    private static PlayerInfoDto info(long id) {
        var info = new PlayerInfoDto();
        info.setPlayerId(id);
        info.setActive(true);
        info.setCurrentTeamAbbrev("EDM");
        return info;
    }

    private static PlayerInfoDto inactive(long id) {
        var info = info(id);
        info.setActive(false);
        return info;
    }
}
