package com.whoshothockey.api.dto;

import java.util.List;

/**
 * DTO for the homepage watchlist ticker: players and teams whose recent form stands out.
 *
 * @param season season the form is measured in, or null when no season is active
 * @param items  at most one entry per player or team, highest score first
 */
public record WatchlistDto(
        String season,
        List<Item> items
) {

    public static WatchlistDto empty(String season) {
        return new WatchlistDto(season, List.of());
    }

    /** Whether the entry is a player or a team. */
    public enum Type { PLAYER, TEAM }

    /** Whether recent form is above or below the expectation. */
    public enum Direction { UP, DOWN }

    /** What recent form was compared with. */
    public enum Rule {
        /** The player's or team's own rate last season. */
        LAST_SEASON,
        /** The player's or team's own rate in the games before the recent ones this season. */
        EARLIER_THIS_SEASON,
        /** The league rate this season, by position group for players. */
        LEAGUE
    }

    /**
     * A player or team with the comparison that stands out most.
     *
     * @param id          NHL player ID, or team code for teams
     * @param teamCode    the player's team, or the team itself
     * @param recent      points per game over the recent games for players, point percentage for teams
     * @param baseline    the same rate for what it was compared with
     * @param recentGames number of recent games, at most ten
     * @param score       how unusual the difference is, in standard deviations; higher is more unusual
     */
    public record Item(
            Type type,
            String id,
            String name,
            String teamCode,
            Direction direction,
            Rule rule,
            double recent,
            double baseline,
            int recentGames,
            double score
    ) {}
}
