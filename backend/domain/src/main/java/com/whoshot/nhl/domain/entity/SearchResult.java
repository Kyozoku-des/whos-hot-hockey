package com.whoshot.nhl.domain.entity;

/**
 * JPQL projection for search queries, built by a constructor expression in the repositories.
 *
 * @param type          {@code "PLAYER"} or {@code "TEAM"}
 * @param id            player ID (as a string) or team code
 * @param name          player full name or team name
 * @param secondaryInfo player position or team code
 * @param teamCode      the player's team code, or the team's own code
 * @param imageUrl      headshot or logo
 */
public record SearchResult(
        String type,
        String id,
        String name,
        String secondaryInfo,
        String teamCode,
        String imageUrl
) {}
