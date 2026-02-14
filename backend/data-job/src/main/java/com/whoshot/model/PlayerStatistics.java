package com.whoshot.model;

import lombok.Builder;

import java.time.LocalDateTime;

/**
 * Immutable calculated statistics for a player over a specific season window.
 *
 * @param gamesPlayed number of games represented in the calculation
 * @param points total points accumulated in the sampled games
 * @param goals total goals accumulated in the sampled games
 * @param assists total assists accumulated in the sampled games
 * @param pointsPerGame average points per game across sampled games
 * @param plusMinus cumulative plus/minus across sampled games
 * @param currentPointStreak current consecutive games with at least one point
 * @param currentPointlessStreak current consecutive games with zero points
 * @param pointsPerLastNGames average points across the last configured N games
 * @param lastUpdated timestamp when this aggregate was generated
 */
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
