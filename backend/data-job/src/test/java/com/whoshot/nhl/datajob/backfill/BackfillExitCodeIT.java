package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for the {@link BackfillRequest} validation → {@link SeasonBackfillService}
 * run outcomes that {@code BackfillRunner} maps to process exit codes (contracts/cli-contract.md,
 * FR-009). {@code System.exit} itself is not exercised here — only the outcomes it is derived from.
 */
class BackfillExitCodeIT extends PostgresIntegrationTestBase {

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private SeasonBackfillService seasonBackfillService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;

    @Test
    void invalidSeason_failsValidation_beforeAnyWrite() {
        // exit code 1: validation rejects the season before SeasonBackfillService.run is even called
        assertThrows(com.whoshot.nhl.datajob.exception.BackfillValidationException.class,
                () -> BackfillRequest.validate("not-a-season", List.of()));

        assertEquals(0, playerRepository.count());
        assertEquals(0, teamRepository.count());
    }

    @Test
    void fatalUpstreamFailure_reportsFailure_andWritesNothing() {
        // exit code 2: the standings fetch itself fails, so nothing about the season is known
        when(nhlApiService.getTeamStandings("2024-04-17"))
                .thenThrow(new ApiClientException("upstream unavailable"));

        BackfillRequest request = BackfillRequest.validate(SEASON_ID, List.of(
                BackfillFixtures.season(SEASON_ID,
                        LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0))));

        BackfillSummary summary = seasonBackfillService.run(request, false);

        assertFalse(summary.success());
        assertEquals(0, playerRepository.count());
        assertEquals(0, teamRepository.count());
    }

    @Test
    void successfulLoad_reportsSuccess() {
        BackfillTestSupport.stubUpstream(nhlApiService);

        BackfillRequest request = BackfillRequest.validate(SEASON_ID, List.of(
                BackfillFixtures.season(SEASON_ID,
                        LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0))));

        BackfillSummary summary = seasonBackfillService.run(request, false);

        assertTrue(summary.success());
    }
}
