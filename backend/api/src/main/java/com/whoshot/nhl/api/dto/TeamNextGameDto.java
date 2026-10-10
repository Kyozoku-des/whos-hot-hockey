package com.whoshot.nhl.api.dto;

/**
 * DTO for a team's next game, shown as its next opponent.
 *
 * @param gameDate     local game date ({@code yyyy-MM-dd})
 * @param startTimeUtc ISO-8601 start time in UTC
 * @param gameState    NHL game state; LIVE or CRIT while the game is in progress
 * @param homeGame     whether the team plays at home
 */
public record TeamNextGameDto(
        String teamCode,
        String opponentTeamCode,
        boolean homeGame,
        String gameDate,
        String startTimeUtc,
        String gameState
) {
}
