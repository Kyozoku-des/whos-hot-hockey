package com.whoshot.nhl.api.dto;

/**
 * DTO representing a player's standings/statistics for the homepage.
 */
public record PlayerStandingsDto(
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
        Double pointsPerLastNGames,
        Boolean hot,
        Boolean cold,
        Integer currentPointStreak,
        Integer currentPointlessStreak
) {}
