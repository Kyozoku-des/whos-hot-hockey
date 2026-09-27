package com.whoshot.nhl.api.dto;

import java.util.List;

/**
 * DTO representing detailed team information including roster.
 * Contains all Team entity fields except season and lastUpdated, plus a roster list.
 */
public record TeamDetailDto(
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
        Boolean nextGameIsHome,
        List<RosterPlayerDto> roster
) {}
