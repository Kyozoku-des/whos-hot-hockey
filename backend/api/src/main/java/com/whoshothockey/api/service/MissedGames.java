package com.whoshothockey.api.service;

import com.whoshothockey.domain.entity.TeamGame;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Games a player sat out for their current team.
 * <p>
 * A game counts as missed when the player's current team played it after the player's first game
 * for that team and the player has no game log for it. Games before that first game, such as
 * those of a team the player was traded from, never count.
 */
public final class MissedGames {

    private MissedGames() {}

    /**
     * The team's games the player missed since first playing for it.
     *
     * @param teamGames the current team's games, oldest first
     * @param playedGameIds ids of every game the player has a game log for
     * @return missed team games, oldest first
     */
    public static List<TeamGame> missed(List<TeamGame> teamGames, Set<Long> playedGameIds) {
        List<TeamGame> missed = new ArrayList<>();
        boolean joined = false;
        for (TeamGame game : teamGames) {
            if (playedGameIds.contains(game.getGameId())) {
                joined = true;
            } else if (joined) {
                missed.add(game);
            }
        }
        return missed;
    }

    /**
     * Team games in a row the player has missed up to now.
     *
     * @param teamGames the current team's games, oldest first
     * @param latestGameId id of the player's most recent game, or null without one
     * @return games the team played after the player's latest game; 0 when that game was not
     *         played for this team
     */
    public static int inARow(List<TeamGame> teamGames, Long latestGameId) {
        if (latestGameId == null) {
            return 0;
        }
        for (int i = teamGames.size() - 1; i >= 0; i--) {
            if (latestGameId.equals(teamGames.get(i).getGameId())) {
                return teamGames.size() - 1 - i;
            }
        }
        return 0;
    }
}
