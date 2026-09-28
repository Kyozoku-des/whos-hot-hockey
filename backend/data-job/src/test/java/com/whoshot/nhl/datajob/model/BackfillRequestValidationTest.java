package com.whoshot.nhl.datajob.model;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.exception.BackfillValidationException;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link BackfillRequest#validate}, covering FR-002's four-step validation order:
 * missing → malformed → unknown upstream → not-yet-started. No database is touched by validation.
 */
class BackfillRequestValidationTest {

    private SeasonDto season(String id, LocalDateTime start, LocalDateTime end) {
        SeasonDto dto = new SeasonDto();
        dto.setId(id);
        dto.setStartDate(start);
        dto.setRegularSeasonEndDate(end);
        return dto;
    }

    @Test
    void rejectsMissingSeason() {
        BackfillValidationException ex = assertThrows(BackfillValidationException.class,
                () -> BackfillRequest.validate(null, List.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("no season"));

        ex = assertThrows(BackfillValidationException.class,
                () -> BackfillRequest.validate("  ", List.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("no season"));
    }

    @Test
    void rejectsMalformedSeason() {
        BackfillValidationException ex = assertThrows(BackfillValidationException.class,
                () -> BackfillRequest.validate("not-a-season", List.of()));
        assertTrue(ex.getMessage().toLowerCase().contains("malformed"));
    }

    @Test
    void rejectsSeasonNotFoundUpstream() {
        SeasonDto known = season("20232024",
                LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 15, 0, 0));

        BackfillValidationException ex = assertThrows(BackfillValidationException.class,
                () -> BackfillRequest.validate("20222023", List.of(known)));
        assertTrue(ex.getMessage().toLowerCase().contains("does not exist"));
    }

    @Test
    void rejectsSeasonThatHasNotStarted() {
        // A syntactically valid season id (within SeasonValidator's max-bound tolerance of
        // "current season + 1") whose start date is nonetheless still in the future.
        String nextSeasonId = com.whoshot.nhl.datajob.util.SeasonValidator.yearToSeasonId(
                com.whoshot.nhl.datajob.util.SeasonValidator.getStartYear(
                        com.whoshot.nhl.datajob.util.SeasonValidator.getCurrentSeasonId()) + 1);
        SeasonDto notStarted = season(nextSeasonId,
                LocalDateTime.now().plusMonths(6), LocalDateTime.now().plusMonths(18));

        BackfillValidationException ex = assertThrows(BackfillValidationException.class,
                () -> BackfillRequest.validate(nextSeasonId, List.of(notStarted)));
        assertTrue(ex.getMessage().toLowerCase().contains("has not started"));
    }

    @Test
    void acceptsValidCompletedSeason_andDerivesStandingsDate() {
        SeasonDto completed = season("20232024",
                LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0));

        BackfillRequest request = BackfillRequest.validate("20232024", List.of(completed));

        assertEquals("20232024", request.seasonId());
        assertEquals("2024-04-17", request.standingsDate());
        assertEquals(2, request.gameType());
    }
}
