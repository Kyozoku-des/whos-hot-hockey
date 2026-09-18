package com.whoshot.nhl.datajob.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BackfillSummary#render()} against the format documented in
 * contracts/cli-contract.md (FR-008).
 */
class BackfillSummaryTest {

    @Test
    void renderedSummary_containsAllRequiredFields() {
        BackfillSummary summary = new BackfillSummary("20242025");
        summary.addTeamsWritten(32);
        summary.addTeamGamesWritten(1312);
        summary.addPlayerWritten();
        summary.addGameLogsWritten(41);
        summary.skip("player", "8478402", "points mismatch (calculated 87, standings 88)");
        summary.finish();

        String rendered = summary.render();

        assertTrue(rendered.contains("season 20242025"));
        assertTrue(rendered.contains("Duration:"));
        assertTrue(rendered.contains("Teams:"));
        assertTrue(rendered.contains("32"));
        assertTrue(rendered.contains("Team games:"));
        assertTrue(rendered.contains("1312"));
        assertTrue(rendered.contains("Players:"));
        assertTrue(rendered.contains("Player game logs:"));
        assertTrue(rendered.contains("41"));
        assertTrue(rendered.contains("Skipped:"));
        assertTrue(rendered.contains("player 8478402: points mismatch"));
        assertTrue(rendered.contains("Result:"));
        assertTrue(rendered.contains("SUCCESS"));
    }

    @Test
    void markFailed_reflectedInRenderedResult() {
        BackfillSummary summary = new BackfillSummary("20242025");
        summary.markFailed();
        summary.finish();

        String rendered = summary.render();

        assertTrue(rendered.contains("FAILURE"));
        assertFalse(rendered.contains("SUCCESS"));
    }
}
