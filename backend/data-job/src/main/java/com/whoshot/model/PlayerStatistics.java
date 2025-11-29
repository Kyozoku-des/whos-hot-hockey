package com.whoshot.model;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record PlayerStatistics(
    int gamesPlayed,
    int points,
    int goals,
    int assists,
    double pointsPerGame,
    int plusMinus,
    int currentPointStreak,
    int currentPointlessStreak,
    double pointsPerLastNGames,
    LocalDateTime lastUpdated
) {}
