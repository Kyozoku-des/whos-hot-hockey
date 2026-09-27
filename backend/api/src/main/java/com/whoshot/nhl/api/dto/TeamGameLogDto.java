package com.whoshot.nhl.api.dto;

/**
 * DTO representing a single game in a team's game log.
 */
public record TeamGameLogDto(
        Long gameId,
        String gameDate,
        String opponentTeamCode,
        Boolean homeGame,
        Integer goalsFor,
        Integer goalsAgainst,
        Boolean won,
        Boolean overtimeLoss,
        String gameType,
        Integer gameNumber
) {}
