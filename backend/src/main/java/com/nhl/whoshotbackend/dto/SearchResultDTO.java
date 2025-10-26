package com.nhl.whoshotbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for search results.
 * Used to return lightweight search data for autocomplete functionality.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultDTO {

    private String type; // "PLAYER" or "TEAM"
    private String id; // playerId (as string) or teamCode
    private String name; // player full name or team name
    private String secondaryInfo; // player position or team abbreviation
    private String teamCode; // player's team code (null for teams)
    private String imageUrl; // headshot or logo
    private String season; // season ID for context
}
