package com.whoshot.nhl.datajob.model;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.exception.BackfillValidationException;
import com.whoshot.nhl.datajob.util.SeasonValidator;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * A validated, resolved description of one season to backfill. Instances are only ever produced by
 * {@link #validate}, which guarantees every check below passed before any database write occurs
 * (FR-002).
 *
 * @param seasonId              season identifier in {@code YYYYYYYY} format
 * @param startDate             the season's start date, from the upstream seasons list
 * @param regularSeasonEndDate  the season's regular-season end date, from the upstream seasons list
 * @param standingsDate         {@code regularSeasonEndDate} formatted {@code YYYY-MM-DD}, used to
 *                              fetch that season's <em>final</em> standings (research R-001)
 * @param gameType              NHL game type; always regular season (2) per spec assumption A-002
 */
public record BackfillRequest(
        String seasonId,
        LocalDateTime startDate,
        LocalDateTime regularSeasonEndDate,
        String standingsDate,
        int gameType
) {

    public static final int REGULAR_SEASON_GAME_TYPE = 2;

    /**
     * Validates a requested season id against the upstream seasons list, in the order required by
     * FR-002: missing → malformed → unknown upstream → not-yet-started.
     *
     * @param seasonId        requested season id, possibly null or blank
     * @param upstreamSeasons the full seasons list from {@code NhlApiService.getSeasons()}
     * @return a resolved, validated request
     * @throws BackfillValidationException naming which check failed
     */
    public static BackfillRequest validate(String seasonId, List<SeasonDto> upstreamSeasons) {
        if (seasonId == null || seasonId.isBlank()) {
            throw new BackfillValidationException(
                    "no season specified; pass --backfill.season=YYYYYYYY");
        }

        if (SeasonValidator.isNotValidSeasonId(seasonId)) {
            throw new BackfillValidationException("malformed season id: " + seasonId);
        }

        SeasonDto matched = upstreamSeasons.stream()
                .filter(s -> seasonId.equals(s.getId()))
                .findFirst()
                .orElseThrow(() -> new BackfillValidationException(
                        "season does not exist upstream: " + seasonId));

        if (LocalDateTime.now().isBefore(matched.getStartDate())) {
            throw new BackfillValidationException("season has not started: " + seasonId);
        }

        String standingsDate = matched.getRegularSeasonEndDate().toLocalDate()
                .format(DateTimeFormatter.ISO_LOCAL_DATE);

        return new BackfillRequest(seasonId, matched.getStartDate(), matched.getRegularSeasonEndDate(),
                standingsDate, REGULAR_SEASON_GAME_TYPE);
    }
}
