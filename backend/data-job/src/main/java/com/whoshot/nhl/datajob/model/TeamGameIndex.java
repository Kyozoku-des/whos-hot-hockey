package com.whoshot.nhl.datajob.model;

import com.whoshot.nhl.domain.entity.TeamGame;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Immutable lookup of team-game outcomes for one season, keyed by {@code (gameId, opponentTeamCode)}.
 * <p>
 * Built once from the {@code team_games} rows written before players are loaded, then shared
 * read-only by every player's game-log write: among the (up to) two rows recorded for a game, the
 * row whose opponent is the player's recorded opponent is the player's own team's row. Holds plain
 * values, never managed JPA entities, so it is safe to share across threads.
 */
public final class TeamGameIndex {

    private static final TeamGameIndex EMPTY = new TeamGameIndex(Map.of());

    private final Map<Key, Boolean> wonByGameAndOpponent;

    private record Key(Long gameId, String opponentTeamCode) {
    }

    private TeamGameIndex(Map<Key, Boolean> wonByGameAndOpponent) {
        this.wonByGameAndOpponent = wonByGameAndOpponent;
    }

    /** An index with no games: every lookup resolves to unknown. */
    public static TeamGameIndex empty() {
        return EMPTY;
    }

    /**
     * Indexes the given team games.
     *
     * @param teamGames team-game rows for one season
     * @return the index; rows without a game id or opponent are ignored
     */
    public static TeamGameIndex of(List<TeamGame> teamGames) {
        Map<Key, Boolean> index = new HashMap<>();
        for (TeamGame teamGame : teamGames) {
            if (teamGame.getGameId() != null && teamGame.getOpponentTeamCode() != null) {
                index.putIfAbsent(new Key(teamGame.getGameId(), teamGame.getOpponentTeamCode()), teamGame.getWon());
            }
        }
        return new TeamGameIndex(Map.copyOf(withoutNullValues(index)));
    }

    /**
     * Whether the team that played {@code opponentTeamCode} in {@code gameId} won.
     *
     * @return the result, or null if that team game has not been loaded; never a fabricated result
     */
    public Boolean wonAgainst(Long gameId, String opponentTeamCode) {
        if (gameId == null || opponentTeamCode == null) {
            return null;
        }
        return wonByGameAndOpponent.get(new Key(gameId, opponentTeamCode));
    }

    /** Number of indexed team games. */
    public int size() {
        return wonByGameAndOpponent.size();
    }

    private static Map<Key, Boolean> withoutNullValues(Map<Key, Boolean> index) {
        index.values().removeIf(java.util.Objects::isNull);
        return index;
    }
}
