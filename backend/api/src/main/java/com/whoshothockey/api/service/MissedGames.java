package com.whoshothockey.api.service;

import com.whoshothockey.domain.entity.GameLog;
import com.whoshothockey.domain.entity.TeamGame;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Games a player sat out for their current team.
 * <p>
 * A game counts as missed when the player's current team played it after the player's first game
 * for that team and the player has no game log for it. Games before that first game, such as
 * those of a team the player was traded from, never count.
 * <p>
 * A game log only counts for the team when the player faced the team's opponent: the same game is
 * also a game of the other side, which a player traded in later may have played for.
 */
public final class MissedGames {

    private MissedGames() {}

    /** A game as one side saw it: the game and who that side played. */
    private record Side(Long gameId, String opponentTeamCode) {}

    /**
     * The team's games the player missed since first playing for it.
     *
     * @param teamGames the current team's games, oldest first
     * @param playerGames every game log of the player in the season
     * @return missed team games, oldest first
     */
    public static List<TeamGame> missed(List<TeamGame> teamGames, Collection<GameLog> playerGames) {
        Set<Side> played = playerGames.stream()
                .map(log -> new Side(log.getGameId(), log.getOpponentTeamCode()))
                .collect(Collectors.toSet());
        List<TeamGame> missed = new ArrayList<>();
        boolean joined = false;
        for (TeamGame game : teamGames) {
            if (played.contains(new Side(game.getGameId(), game.getOpponentTeamCode()))) {
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
     * @param latestGame the player's most recent game log, or null without one
     * @return games the team played after the player's latest game; 0 when that game was not
     *         played for this team
     */
    public static int inARow(List<TeamGame> teamGames, GameLog latestGame) {
        if (latestGame == null) {
            return 0;
        }
        for (int i = teamGames.size() - 1; i >= 0; i--) {
            TeamGame game = teamGames.get(i);
            if (Objects.equals(latestGame.getGameId(), game.getGameId())
                    && Objects.equals(latestGame.getOpponentTeamCode(), game.getOpponentTeamCode())) {
                return teamGames.size() - 1 - i;
            }
        }
        return 0;
    }
}
