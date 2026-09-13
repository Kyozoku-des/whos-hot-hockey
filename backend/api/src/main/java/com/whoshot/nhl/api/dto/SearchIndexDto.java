package com.whoshot.nhl.api.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * DTO wrapping the full autocomplete search index for a single season.
 * <p>
 * The season is carried once on the envelope rather than repeated on every
 * {@link SearchResultDto}: the index holds ~800 rows that all share the same
 * season, and clients cache the whole index, so they need the season as a
 * single value to know when their cached copy has gone stale.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchIndexDto {

    private String season; // season ID the whole index belongs to
    private int count; // number of entries in results
    private List<SearchResultDto> results;
}
