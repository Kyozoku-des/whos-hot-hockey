package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.PLAYER_ID;
import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.when;

/**
 * Integration coverage for spec US3 AS-2: a backfill that dies partway through, then is re-run,
 * converges on exactly the same database state as a run that was never interrupted.
 * <p>
 * The crash is simulated by a lost database connection in the player phase, after teams and team
 * games have already been committed — the same partial state a killed process would leave behind.
 */
class BackfillResumeIT extends PostgresIntegrationTestBase {

    private static final List<String> TABLES = List.of("teams", "team_games", "players", "game_logs");

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private SeasonBackfillService seasonBackfillService;
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private BackfillRequest request() {
        return BackfillRequest.validate(SEASON_ID, List.of(
                BackfillFixtures.season(SEASON_ID,
                        LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0))));
    }

    @Test
    void interruptedThenRerun_matchesAnUninterruptedRun() {
        // Baseline: one clean, uninterrupted run.
        BackfillTestSupport.stubUpstream(nhlApiService);
        assertTrue(seasonBackfillService.run(request(), false).success());
        Map<String, List<String>> uninterrupted = snapshot();
        jdbcTemplate.execute("TRUNCATE TABLE players, teams, game_logs, team_games RESTART IDENTITY");

        // Interrupted: the process dies while loading players.
        // Losing the database is the one failure a run does not skip past.
        when(nhlApiService.getPlayerGameLogs(PLAYER_ID, SEASON_ID, 2))
                .thenThrow(new DataAccessResourceFailureException("simulated crash mid-run"));
        assertThrows(DataAccessResourceFailureException.class, () -> seasonBackfillService.run(request(), false));
        Map<String, List<String>> partial = snapshot();
        assertFalse(partial.get("teams").isEmpty(), "the crash must happen after some data was committed");
        assertTrue(partial.get("players").isEmpty(), "the crash must happen before the player phase finished");

        // Re-run with upstream healthy again.
        reset(nhlApiService);
        BackfillTestSupport.stubUpstream(nhlApiService);
        assertTrue(seasonBackfillService.run(request(), false).success());

        assertEquals(uninterrupted, snapshot());
    }

    /**
     * Every row of every backfilled table as JSON, minus surrogate ids and update timestamps,
     * sorted so the comparison is independent of insert order.
     */
    private Map<String, List<String>> snapshot() {
        Map<String, List<String>> snapshot = new LinkedHashMap<>();
        for (String table : TABLES) {
            snapshot.put(table, jdbcTemplate.queryForList(
                    "SELECT (to_jsonb(t) - 'id' - 'last_updated')::text FROM " + table + " t ORDER BY 1",
                    String.class));
        }
        return snapshot;
    }
}
