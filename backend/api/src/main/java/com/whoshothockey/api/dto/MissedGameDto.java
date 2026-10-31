package com.whoshothockey.api.dto;

import java.time.LocalDate;

/**
 * DTO representing a team game a player did not play in.
 */
public record MissedGameDto(
        Long gameId,
        LocalDate gameDate,
        String opponentTeamCode,
        Boolean homeGame
) {}
