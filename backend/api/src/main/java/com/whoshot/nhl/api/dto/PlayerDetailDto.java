package com.whoshot.nhl.api.dto;

/**
 * DTO representing detailed player information for the player detail page.
 */
public record PlayerDetailDto(
        Long playerId,
        String firstName,
        String lastName,
        String fullName,
        String positionCode,
        String teamCode,
        String teamLogoUrl,
        String headshotUrl,
        Integer gamesPlayed,
        Integer goals,
        Integer assists,
        Integer points,
        Double pointsPerGame,
        Integer plusMinus,
        Integer currentPointStreak,
        Integer currentPointlessStreak,
        Double pointsPerLastNGames,
        Boolean hot,
        Boolean cold,
        NextGameDto nextGame
) {
    /**
     * Nested DTO for the player's next scheduled game.
     */
    public record NextGameDto(
            String date,
            String opponentAbbrev,
            String homeRoadFlag
    ) {}
}
