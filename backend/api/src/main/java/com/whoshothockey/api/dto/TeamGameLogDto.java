package com.whoshothockey.api.dto;

import java.time.LocalDate;

/**
 * DTO representing a single game in a team's game log.
 */
public record TeamGameLogDto(
        Long gameId,
        LocalDate gameDate,
        String opponentTeamCode,
        Boolean homeGame,
        Integer goalsFor,
        Integer goalsAgainst,
        Boolean won,
        Boolean overtimeLoss,
        String gameType,
        Integer gameNumber
) {}
