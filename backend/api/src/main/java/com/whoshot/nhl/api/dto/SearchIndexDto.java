package com.whoshot.nhl.api.dto;

import java.util.List;

/**
 * DTO wrapping the full autocomplete search index for a single season.
 * <p>
 * The season is carried once on the envelope rather than repeated on every
 * {@link SearchResultDto}: the index holds ~800 rows that all share the same
 * season, and clients cache the whole index, so they need the season as a
 * single value to know when their cached copy has gone stale.
 *
 * @param season  season ID the whole index belongs to
 * @param count   number of entries in {@code results}
 * @param results every team followed by every player
 */
public record SearchIndexDto(
        String season,
        int count,
        List<SearchResultDto> results
) {}
