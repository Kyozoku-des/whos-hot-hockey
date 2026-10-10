package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.backfill.PostgresIntegrationTestBase;
import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.ScoreResponseDto;
import com.whoshot.nhl.domain.entity.ScoreboardGame;
import com.whoshot.nhl.domain.entity.ScoreboardGoal;
import com.whoshot.nhl.domain.repository.ScoreboardGameRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ScoreboardWriter} against PostgreSQL: started games only, goals in order without the
 * shootout, goals replaced on rewrite, and older game days dropped.
 */
class ScoreboardWriterIT extends PostgresIntegrationTestBase {

    @Autowired
    private ScoreboardWriter writer;

    @Autowired
    private ScoreboardGameRepository repository;

    @Test
    void writesStartedGamesWithGoalsInOrderExcludingShootout() {
        var live = game(1L, "2026-10-09", GameState.LIVE,
                goal("REG", 11L, "A. One", 0, 1, 12L, 13L),
                goal("REG", 21L, "B. Two", 1, 1),
                goal("SO", 31L, "C. Three", 1, 2));
        var future = game(2L, "2026-10-09", GameState.FUT);

        assertThat(writer.writeGames(List.of(live, future), null)).isEqualTo(1);

        List<ScoreboardGame> games = repository.findByGameDateOrderByStartTimeUtcAscGameIdAsc("2026-10-09");
        assertThat(games).singleElement().satisfies(game -> {
            assertThat(game.getGameState()).isEqualTo("LIVE");
            assertThat(game.getGoals()).extracting(ScoreboardGoal::getGoalNumber).containsExactly(1, 2);
            ScoreboardGoal first = game.getGoals().getFirst();
            assertThat(first.getScorerName()).isEqualTo("A. One");
            assertThat(first.getAssist1Id()).isEqualTo(12L);
            assertThat(first.getAssist2Id()).isEqualTo(13L);
            assertThat(game.getGoals().get(1).getAssist1Id()).isNull();
        });
    }

    @Test
    void rewriteReplacesGoalsAndDropsOlderDays() {
        writer.writeGames(List.of(
                game(1L, "2026-10-07", GameState.OFF, goal("REG", 11L, "A. One", 1, 0)),
                game(2L, "2026-10-08", GameState.LIVE, goal("REG", 21L, "B. Two", 1, 0))), null);

        // The 2026-10-08 goal was overturned on review and a new one scored.
        writer.writeGames(List.of(
                game(2L, "2026-10-08", GameState.OFF, goal("REG", 22L, "D. Four", 0, 1))), "2026-10-08");

        assertThat(repository.findByGameDateOrderByStartTimeUtcAscGameIdAsc("2026-10-07")).isEmpty();
        assertThat(repository.findByGameDateOrderByStartTimeUtcAscGameIdAsc("2026-10-08"))
                .singleElement().satisfies(game -> {
                    assertThat(game.getGameState()).isEqualTo("OFF");
                    assertThat(game.getGoals()).extracting(ScoreboardGoal::getScorerId).containsExactly(22L);
                });
        assertThat(repository.findLatestGameDateWithState(List.of("OFF"))).isEqualTo("2026-10-08");
    }

    private static ScoreResponseDto.ScoreGame game(Long id, String date, GameState state, ScoreResponseDto.Goal... goals) {
        var game = new ScoreResponseDto.ScoreGame();
        game.setId(id);
        game.setSeason(20262027L);
        game.setGameDate(date);
        game.setStartTimeUTC(date + "T23:00:00Z");
        game.setGameState(state);
        game.setAwayTeam(team("SEA", 0));
        game.setHomeTeam(team("DET", 0));
        game.setGoals(Arrays.asList(goals));
        return game;
    }

    private static GameDto.TeamInfo team(String abbrev, int score) {
        var team = new GameDto.TeamInfo();
        team.setAbbrev(abbrev);
        team.setScore(score);
        return team;
    }

    private static ScoreResponseDto.Goal goal(String periodType, Long scorerId, String scorer,
                                              int awayScore, int homeScore, Long... assistIds) {
        var goal = new ScoreResponseDto.Goal();
        var period = new ScoreResponseDto.PeriodDescriptor();
        period.setPeriodType(periodType);
        goal.setPeriodDescriptor(period);
        goal.setPeriod(1);
        goal.setTimeInPeriod("10:00");
        goal.setPlayerId(scorerId);
        goal.setName(name(scorer));
        goal.setTeamAbbrev("DET");
        goal.setAwayScore(awayScore);
        goal.setHomeScore(homeScore);
        goal.setStrength("ev");
        goal.setAssists(Arrays.stream(assistIds).map(id -> {
            var assist = new ScoreResponseDto.Assist();
            assist.setPlayerId(id);
            assist.setName(name("Assist " + id));
            return assist;
        }).toList());
        return goal;
    }

    private static PlayerInfoDto.NameDto name(String value) {
        var name = new PlayerInfoDto.NameDto();
        name.setName(value);
        return name;
    }
}
