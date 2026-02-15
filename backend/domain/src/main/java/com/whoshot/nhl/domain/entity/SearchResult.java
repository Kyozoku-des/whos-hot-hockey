package com.whoshot.nhl.domain.entity;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPQL projection class for search queries.
 * Used as a constructor expression result in repository queries.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {

    private String type; // "PLAYER" or "TEAM"
    private String id; // playerId (as string) or teamCode
    private String name; // player full name or team name
    private String secondaryInfo; // player position or team abbreviation
    private String teamCode; // player's team code (null for teams)
    private String imageUrl; // headshot or logo
    private String season; // season ID for context
}
