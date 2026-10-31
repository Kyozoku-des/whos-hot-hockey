package com.whoshothockey.api.service;

import com.whoshothockey.domain.entity.GameLog;
import com.whoshothockey.domain.entity.TeamGame;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class MissedGamesTest {

    /** Games 1..count of COL, oldest first, each against a different opponent. */
    private static List<TeamGame> teamGames(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> {
            TeamGame game = new TeamGame();
            game.setGameId(id);
            game.setTeamCode("COL");
            game.setOpponentTeamCode(opponent(id));
            return game;
        }).toList();
    }

    private static String opponent(long gameId) {
        return "OPP" + gameId;
    }

    /** A game log of a player playing for COL. */
    private static GameLog forCol(long gameId) {
        return log(gameId, opponent(gameId));
    }

    private static GameLog log(long gameId, String opponent) {
        GameLog log = new GameLog();
        log.setGameId(gameId);
        log.setOpponentTeamCode(opponent);
        return log;
    }

    private static List<GameLog> forCol(long... gameIds) {
        return Arrays.stream(gameIds).mapToObj(MissedGamesTest::forCol).toList();
    }

    private static List<Long> ids(List<TeamGame> games) {
        return games.stream().map(TeamGame::getGameId).toList();
    }

    @Test
    void missed_areTheTeamGamesWithoutThePlayerAfterTheirFirstGame() {
        assertThat(ids(MissedGames.missed(teamGames(6), forCol(1, 2, 4)))).containsExactly(3L, 5L, 6L);
    }

    @Test
    void missed_ignoresGamesBeforeThePlayersFirstGameForTheTeam() {
        // Joined (traded, called up) at game 4; games 100 and 101 were for another team
        List<GameLog> played = List.of(log(100, "SEA"), log(101, "SEA"), forCol(4), forCol(6));
        assertThat(ids(MissedGames.missed(teamGames(6), played))).containsExactly(5L);
    }

    @Test
    void missed_aGameAgainstTheTeamBeforeATrade_isNotTheFirstGameForIt() {
        // Played game 2 against COL for its opponent, then joined COL at game 5
        List<GameLog> played = List.of(log(2, "COL"), forCol(5));
        assertThat(ids(MissedGames.missed(teamGames(6), played))).containsExactly(6L);
    }

    @Test
    void missed_withoutAGameForTheTeam_isEmpty() {
        assertThat(MissedGames.missed(teamGames(3), List.of(log(100, "SEA")))).isEmpty();
        assertThat(MissedGames.missed(teamGames(3), List.of())).isEmpty();
    }

    @Test
    void inARow_countsTheTeamGamesAfterThePlayersLatestGame() {
        assertThat(MissedGames.inARow(teamGames(6), forCol(4))).isEqualTo(2);
        assertThat(MissedGames.inARow(teamGames(6), forCol(6))).isZero();
    }

    @Test
    void inARow_whenTheLatestGameWasForAnotherTeam_isZero() {
        assertThat(MissedGames.inARow(teamGames(6), log(100, "SEA"))).isZero();
        // Latest game was against COL, just before being traded there
        assertThat(MissedGames.inARow(teamGames(6), log(4, "COL"))).isZero();
        assertThat(MissedGames.inARow(teamGames(6), null)).isZero();
    }
}
