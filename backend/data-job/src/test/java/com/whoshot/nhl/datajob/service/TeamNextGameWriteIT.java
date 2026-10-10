package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.backfill.PostgresIntegrationTestBase;
import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import com.whoshot.nhl.domain.entity.TeamNextGame;
import com.whoshot.nhl.domain.repository.TeamNextGameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link GameLogWriter#writeTeamGames} against PostgreSQL: the team's next game is stored,
 * replaced as the schedule advances, and removed once no game is left (issue #44).
 */
class TeamNextGameWriteIT extends PostgresIntegrationTestBase {

    private static final String SEASON_ID = "20262027";

    @Autowired
    private GameLogWriter writer;

    @Autowired
    private TeamNextGameRepository repository;

    @Test
    void storesNextGame_thenAdvances_thenRemovesWhenScheduleIsDone() {
        GameDto first = game(1L, "2026-10-10T23:00:00Z", GameState.FUT, "COL", "MTL");
        GameDto second = game(2L, "2026-10-12T23:00:00Z", GameState.FUT, "TOR", "COL");

        writer.writeTeamGames("COL", SEASON_ID, List.of(second, first));
        assertThat(repository.findBySeasonId(SEASON_ID)).singleElement().satisfies(next -> {
            assertThat(next.getGameId()).isEqualTo(1L);
            assertThat(next.getOpponentTeamCode()).isEqualTo("MTL");
            assertThat(next.getHomeGame()).isTrue();
        });

        finish(first);
        writer.writeTeamGames("COL", SEASON_ID, List.of(first, second));
        assertThat(repository.findBySeasonId(SEASON_ID)).singleElement().satisfies(next -> {
            assertThat(next.getGameId()).isEqualTo(2L);
            assertThat(next.getOpponentTeamCode()).isEqualTo("TOR");
            assertThat(next.getHomeGame()).isFalse();
        });

        finish(second);
        writer.writeTeamGames("COL", SEASON_ID, List.of(first, second));
        assertThat(repository.findBySeasonId(SEASON_ID)).isEmpty();
    }

    @Test
    void scheduleWithoutUnfinishedGames_writesNothing() {
        GameDto played = game(1L, "2026-10-10T23:00:00Z", GameState.FUT, "COL", "MTL");
        finish(played);

        writer.writeTeamGames("COL", SEASON_ID, List.of(played));

        assertThat(repository.findAll()).extracting(TeamNextGame::getTeamCode).isEmpty();
    }

    private static GameDto game(long id, String startTimeUTC, GameState state, String home, String away) {
        GameDto dto = new GameDto();
        dto.setId(id);
        dto.setStartTimeUTC(startTimeUTC);
        dto.setGameDate(startTimeUTC.substring(0, 10));
        dto.setGameType(2);
        dto.setGameState(state);
        GameDto.TeamInfo homeTeam = new GameDto.TeamInfo();
        homeTeam.setAbbrev(home);
        dto.setHomeTeam(homeTeam);
        GameDto.TeamInfo awayTeam = new GameDto.TeamInfo();
        awayTeam.setAbbrev(away);
        dto.setAwayTeam(awayTeam);
        return dto;
    }

    private static void finish(GameDto game) {
        game.setGameState(GameState.OFF);
        game.getHomeTeam().setScore(3);
        game.getAwayTeam().setScore(2);
    }
}
