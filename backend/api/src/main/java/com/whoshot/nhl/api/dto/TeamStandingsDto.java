package com.whoshot.nhl.api.dto;

/**
 * DTO representing a team's standings for the homepage.
 * Contains all Team entity fields except season and lastUpdated.
 */
public record TeamStandingsDto(
        String teamCode,
        String teamName,
        String franchiseName,
        String logoUrl,
        Integer gamesPlayed,
        Integer wins,
        Integer losses,
        Integer overtimeLosses,
        Integer points,
        Double pointPercentage,
        Integer goalsFor,
        Integer goalsAgainst,
        Integer goalDifferential,
        String conferenceName,
        String divisionName,
        Integer currentWinStreak,
        Integer currentLossStreak,
        Double last10GamesWinPercentage,
        Double last10GamesPointPercentage,
        Double last10GamesPPG,
        Boolean hot,
        Boolean cold,
        Boolean pointStreak,
        String nextOpponentCode,
        String nextGameDate,
        Boolean nextGameIsHome
) {}
