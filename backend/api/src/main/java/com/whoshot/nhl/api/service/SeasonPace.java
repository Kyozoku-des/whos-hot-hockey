package com.whoshot.nhl.api.service;

/**
 * Regular-season length and "on pace for" projections.
 */
public final class SeasonPace {

    private SeasonPace() {}

    /**
     * Number of regular-season games each team plays in a season.
     *
     * @param seasonId season identifier, e.g. "20252026"; null or malformed falls back to 82
     * @return games per team in the regular season
     */
    public static int regularSeasonGames(String seasonId) {
        int startYear;
        try {
            startYear = Integer.parseInt(seasonId.substring(0, 4));
        } catch (RuntimeException e) {
            return 82;
        }
        if (startYear >= 2025) {
            return 84;
        }
        return switch (startYear) {
            case 1994, 2012 -> 48;
            case 2020 -> 56;
            default -> 82;
        };
    }

    /**
     * Points at the end of the season if the current points-per-game rate holds.
     *
     * @param points points so far
     * @param gamesPlayed games played so far
     * @param seasonGames regular-season length
     * @return projected season points, or null before the first game
     */
    public static Integer projectedPoints(Integer points, Integer gamesPlayed, int seasonGames) {
        if (points == null || gamesPlayed == null || gamesPlayed <= 0) {
            return null;
        }
        return (int) Math.round((double) points / gamesPlayed * seasonGames);
    }
}
