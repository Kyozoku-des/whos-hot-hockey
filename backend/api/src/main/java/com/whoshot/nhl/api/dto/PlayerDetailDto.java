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
        Integer seasonGames,
        Integer projectedPoints
) {}
