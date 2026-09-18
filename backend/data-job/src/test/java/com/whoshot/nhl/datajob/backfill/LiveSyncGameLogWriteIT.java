package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.service.DataSyncService;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.PLAYER_ID;
import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for research R-003: the live sync (not just the backfill) must write
 * {@code game_logs} and {@code team_games}, otherwise a game-log graph has no current-season line
 * to compare a backfilled previous season against (SC-007).
 */
class LiveSyncGameLogWriteIT extends PostgresIntegrationTestBase {

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private DataSyncService dataSyncService;
    @Autowired
    private GameLogRepository gameLogRepository;
    @Autowired
    private TeamGameRepository teamGameRepository;

    @Test
    void liveSync_writesGameLogsAndTeamGames() throws PlayerStatisticsException {
        SeasonDto currentSeason = BackfillFixtures.season(SEASON_ID,
                LocalDateTime.now().minusMonths(3), LocalDateTime.now().plusMonths(3));
        when(nhlApiService.getSeasons()).thenReturn(List.of(currentSeason));
        when(nhlApiService.getLeagueSchedule()).thenReturn(List.of());
        BackfillTestSupport.stubUpstream(nhlApiService);
        // The live sync fetches standings via the no-arg /now overload, not the dated one.
        when(nhlApiService.getTeamStandings()).thenReturn(List.of(
                BackfillFixtures.teamStanding("COL"), BackfillFixtures.teamStanding("MTL")));
        // Unlike the backfill, the live sync skips inactive players, so this player must be active.
        when(nhlApiService.getPlayerInfo(PLAYER_ID)).thenReturn(
                BackfillFixtures.playerInfo(PLAYER_ID, true, "COL"));

        dataSyncService.resolveActiveSeasonForLiveSync();
        dataSyncService.syncTeams();
        dataSyncService.syncPlayers();

        assertFalse(gameLogRepository.findByPlayerIdAndSeasonId(PLAYER_ID, SEASON_ID).isEmpty(),
                "the live sync must populate game_logs, not just the backfill");
        assertFalse(teamGameRepository.findBySeasonId(SEASON_ID).isEmpty(),
                "the live sync must populate team_games, not just the backfill");
    }
}
