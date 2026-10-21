package com.whoshothockey.api.dto;

import java.time.Instant;
import java.time.LocalDate;

/**
 * DTO for a team's next game, shown as its next opponent.
 *
 * @param gameDate     local game date, serialized as {@code yyyy-MM-dd}
 * @param startTimeUtc start time, serialized as ISO-8601 in UTC
 * @param gameState    NHL game state; LIVE or CRIT while the game is in progress
 * @param homeGame     whether the team plays at home
 */
public record TeamNextGameDto(
        String teamCode,
        String opponentTeamCode,
        boolean homeGame,
        LocalDate gameDate,
        Instant startTimeUtc,
        String gameState
) {
}
