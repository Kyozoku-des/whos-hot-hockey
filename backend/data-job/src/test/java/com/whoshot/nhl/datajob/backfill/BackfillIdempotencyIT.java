package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration coverage for FR-006 / SC-004: running the same season backfill twice converges on
 * identical row counts, with no duplicates.
 */
class BackfillIdempotencyIT extends PostgresIntegrationTestBase {

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private SeasonBackfillService seasonBackfillService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private GameLogRepository gameLogRepository;
    @Autowired
    private TeamGameRepository teamGameRepository;

    private BackfillRequest request() {
        return BackfillRequest.validate(SEASON_ID, List.of(
                BackfillFixtures.season(SEASON_ID,
                        LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0))));
    }

    @Test
    void rerunningTheSameSeason_producesIdenticalCounts_noDuplicates() {
        BackfillTestSupport.stubUpstream(nhlApiService);

        BackfillSummary first = seasonBackfillService.run(request(), false);
        assertTrue(first.success());

        long playersAfterFirst = playerRepository.count();
        long teamsAfterFirst = teamRepository.count();
        long gameLogsAfterFirst = gameLogRepository.count();
        long teamGamesAfterFirst = teamGameRepository.count();

        BackfillSummary second = seasonBackfillService.run(request(), false);
        assertTrue(second.success());

        assertEquals(playersAfterFirst, playerRepository.count());
        assertEquals(teamsAfterFirst, teamRepository.count());
        assertEquals(gameLogsAfterFirst, gameLogRepository.count());
        assertEquals(teamGamesAfterFirst, teamGameRepository.count());
    }
}
