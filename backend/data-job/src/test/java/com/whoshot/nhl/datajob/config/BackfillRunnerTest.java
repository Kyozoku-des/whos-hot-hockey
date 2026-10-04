package com.whoshot.nhl.datajob.config;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ConfigurableApplicationContext;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Exit-code mapping of {@link BackfillRunner} (contracts/cli-contract.md, FR-009). Exercises
 * {@code execute()} rather than {@code run()}, which would call {@code System.exit}.
 */
@ExtendWith(MockitoExtension.class)
class BackfillRunnerTest {

    private static final String SEASON = "20232024";

    @Mock
    private NhlApiService nhlApiService;
    @Mock
    private SeasonBackfillService seasonBackfillService;
    @Mock
    private ConfigurableApplicationContext context;

    private BackfillRunner runner;

    @BeforeEach
    void setUp() {
        runner = new BackfillRunner(nhlApiService, seasonBackfillService, context, SEASON, false);
    }

    @Test
    void successfulRun_exits0() {
        when(nhlApiService.getSeasons()).thenReturn(List.of(knownSeason()));
        when(seasonBackfillService.run(any(), anyBoolean())).thenReturn(new BackfillSummary(SEASON).finish());

        assertEquals(0, runner.execute());
    }

    @Test
    void unknownSeason_exits1_withoutRunning() {
        when(nhlApiService.getSeasons()).thenReturn(List.of());

        assertEquals(1, runner.execute());
        verifyNoInteractions(seasonBackfillService);
    }

    @Test
    void seasonListUnavailable_exits2_withoutRunning() {
        when(nhlApiService.getSeasons()).thenThrow(new ApiClientException("seasons 503 after 3 attempts"));

        assertEquals(2, runner.execute());
        verifyNoInteractions(seasonBackfillService);
    }

    @Test
    void failedRun_exits2() {
        when(nhlApiService.getSeasons()).thenReturn(List.of(knownSeason()));
        BackfillSummary failed = new BackfillSummary(SEASON);
        failed.markFailed();
        when(seasonBackfillService.run(any(), anyBoolean())).thenReturn(failed.finish());

        assertEquals(2, runner.execute());
    }

    @Test
    void alreadyRunning_exits3() {
        when(nhlApiService.getSeasons()).thenReturn(List.of(knownSeason()));
        when(seasonBackfillService.run(any(), anyBoolean()))
                .thenThrow(new SeasonBackfillService.AlreadyRunningException(SEASON));

        assertEquals(3, runner.execute());
    }

    private static SeasonDto knownSeason() {
        SeasonDto season = new SeasonDto();
        season.setId(SEASON);
        season.setStartDate(LocalDateTime.of(2023, 10, 10, 0, 0));
        season.setRegularSeasonEndDate(LocalDateTime.of(2024, 4, 18, 0, 0));
        return season;
    }
}
