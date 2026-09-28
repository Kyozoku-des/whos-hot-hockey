package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import com.whoshot.nhl.domain.entity.GameLog;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.PLAYER_ID;
import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration coverage for SC-003: every loaded player's season totals equal the sum of that
 * player's loaded per-game logs.
 */
class BackfillTotalsConsistencyIT extends PostgresIntegrationTestBase {

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private SeasonBackfillService seasonBackfillService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private GameLogRepository gameLogRepository;

    @Test
    void loadedPlayerTotals_matchSumOfLoadedGameLogs() {
        BackfillTestSupport.stubUpstream(nhlApiService);

        BackfillRequest request = BackfillRequest.validate(SEASON_ID, List.of(
                BackfillFixtures.season(SEASON_ID,
                        LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0))));

        BackfillSummary summary = seasonBackfillService.run(request, false);
        assertTrue(summary.success());
        assertTrue(summary.skipped().isEmpty(), "the fixture data must be internally consistent");

        Player player = playerRepository.findById(new Player.PlayerId(PLAYER_ID, SEASON_ID)).orElseThrow();
        List<GameLog> gameLogs = gameLogRepository.findByPlayerIdAndSeasonId(PLAYER_ID, SEASON_ID);

        int summedPoints = gameLogs.stream().mapToInt(GameLog::getPoints).sum();
        assertEquals(player.getPoints(), summedPoints);
        assertEquals(2, gameLogs.size());

        // gameNumber must be dense and chronological (1, 2), regardless of upstream order.
        List<Integer> gameNumbers = gameLogs.stream().map(GameLog::getGameNumber).sorted().toList();
        assertEquals(List.of(1, 2), gameNumbers);
    }
}
