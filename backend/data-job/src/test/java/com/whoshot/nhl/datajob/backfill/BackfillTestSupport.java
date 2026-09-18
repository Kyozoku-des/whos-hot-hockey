package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.service.NhlApiService;

import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.PLAYER_ID;
import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Stubs a mocked {@link NhlApiService} with {@link BackfillFixtures}' internally-consistent
 * two-team, one-player dataset, shared across backfill integration tests.
 */
final class BackfillTestSupport {

    private BackfillTestSupport() {
    }

    static void stubUpstream(NhlApiService nhlApiService) {
        when(nhlApiService.getTeamStandings("2024-04-17")).thenReturn(List.of(
                BackfillFixtures.teamStanding("COL"),
                BackfillFixtures.teamStanding("MTL")));
        when(nhlApiService.getTeamSchedule(anyString(), eq(SEASON_ID)))
                .thenReturn(BackfillFixtures.twoGameSchedule());
        when(nhlApiService.getPlayerStandingsOrder(SEASON_ID, 2))
                .thenReturn(List.of(BackfillFixtures.playerStanding(PLAYER_ID, 3)));
        when(nhlApiService.getPlayerInfo(PLAYER_ID))
                .thenReturn(BackfillFixtures.playerInfo(PLAYER_ID, false, "COL"));
        when(nhlApiService.getPlayerGameLogs(PLAYER_ID, SEASON_ID, 2))
                .thenReturn(BackfillFixtures.twoGameLogsMostRecentFirst());
    }
}
