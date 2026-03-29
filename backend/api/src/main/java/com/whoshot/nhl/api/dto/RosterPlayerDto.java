package com.whoshot.nhl.api.dto;

/**
 * DTO representing a player on a team's roster.
 */
public record RosterPlayerDto(
        Long playerId,
        String fullName,
        String positionCode,
        String teamCode,
        String headshotUrl
) {}
