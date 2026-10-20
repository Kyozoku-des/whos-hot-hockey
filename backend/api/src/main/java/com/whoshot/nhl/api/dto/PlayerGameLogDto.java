package com.whoshot.nhl.api.dto;

import java.time.LocalDate;

/**
 * DTO representing a single game log entry for a player.
 */
public record PlayerGameLogDto(
        Long gameId,
        LocalDate gameDate,
        String opponentTeamCode,
        Boolean homeGame,
        Integer goals,
        Integer assists,
        Integer points,
        Integer plusMinus,
        Integer shots,
        Integer timeOnIce,
        Boolean gameWon,
        Integer gameNumber
) {}
