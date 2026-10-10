package com.whoshot.nhl.api.dto;

import java.util.List;

/**
 * DTO for the homepage score ticker: one game day's games with their goals in the order scored.
 *
 * @param gameDate game day shown ({@code yyyy-MM-dd}), or null when no game has been played yet
 * @param live     whether any game of that day is in progress
 */
public record ScoreboardDto(
        String gameDate,
        boolean live,
        List<Game> games
) {

    public static ScoreboardDto empty() {
        return new ScoreboardDto(null, false, List.of());
    }

    /**
     * A game with its current or final score.
     *
     * @param lastPeriodType REG, OT or SO once the game has ended
     */
    public record Game(
            Long gameId,
            String gameState,
            String startTimeUtc,
            String awayTeamCode,
            Integer awayScore,
            String homeTeamCode,
            Integer homeScore,
            String lastPeriodType,
            List<Goal> goals
    ) {}

    /**
     * A goal with the score after it.
     *
     * @param assists zero to two assisting players, primary first
     */
    public record Goal(
            Integer goalNumber,
            Integer period,
            String periodType,
            String timeInPeriod,
            String teamCode,
            Integer awayScore,
            Integer homeScore,
            String strength,
            Point scorer,
            List<Point> assists
    ) {}

    /**
     * A player credited with a point on a goal.
     *
     * @param name           abbreviated name, e.g. "V. Arvidsson"
     * @param streakExtended whether the player also had a point in their previous game of the season
     * @param hot            whether the player's points per game over the last ten games meets the
     *                       hot threshold
     */
    public record Point(
            Long playerId,
            String name,
            boolean streakExtended,
            boolean hot
    ) {}
}
