package com.whoshothockey.api.service;

import com.whoshothockey.domain.entity.TeamGame;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;

class MissedGamesTest {

    /** Games 1..count of one team, oldest first. */
    private static List<TeamGame> teamGames(int count) {
        return LongStream.rangeClosed(1, count).mapToObj(id -> {
            TeamGame game = new TeamGame();
            game.setGameId(id);
            return game;
        }).toList();
    }

    private static List<Long> ids(List<TeamGame> games) {
        return games.stream().map(TeamGame::getGameId).toList();
    }

    @Test
    void missed_areTheTeamGamesWithoutThePlayerAfterTheirFirstGame() {
        assertThat(ids(MissedGames.missed(teamGames(6), Set.of(1L, 2L, 4L)))).containsExactly(3L, 5L, 6L);
    }

    @Test
    void missed_ignoresGamesBeforeThePlayersFirstGameForTheTeam() {
        // Joined (traded, called up) at game 4; games 100 and 101 were for another team
        assertThat(ids(MissedGames.missed(teamGames(6), Set.of(100L, 101L, 4L, 6L)))).containsExactly(5L);
    }

    @Test
    void missed_withoutAGameForTheTeam_isEmpty() {
        assertThat(MissedGames.missed(teamGames(3), Set.of(100L))).isEmpty();
        assertThat(MissedGames.missed(teamGames(3), Set.of())).isEmpty();
    }

    @Test
    void inARow_countsTheTeamGamesAfterThePlayersLatestGame() {
        assertThat(MissedGames.inARow(teamGames(6), 4L)).isEqualTo(2);
        assertThat(MissedGames.inARow(teamGames(6), 6L)).isZero();
    }

    @Test
    void inARow_whenTheLatestGameWasForAnotherTeam_isZero() {
        assertThat(MissedGames.inARow(teamGames(6), 100L)).isZero();
        assertThat(MissedGames.inARow(teamGames(6), null)).isZero();
    }
}
