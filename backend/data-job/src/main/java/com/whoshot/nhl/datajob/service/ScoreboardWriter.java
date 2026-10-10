package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.ScoreResponseDto;
import com.whoshot.nhl.domain.entity.ScoreboardGame;
import com.whoshot.nhl.domain.entity.ScoreboardGoal;
import com.whoshot.nhl.domain.repository.ScoreboardGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Maps and upserts the score ticker's games into {@code scoreboard_games} and
 * {@code scoreboard_goals}. Each write replaces a game's goals with the upstream list, so a goal
 * overturned on review disappears on the next sync.
 */
@Service
@RequiredArgsConstructor
public class ScoreboardWriter {

    /** Shootout goals are not points and not part of the scoring summary. */
    private static final String SHOOTOUT = "SO";

    private final ScoreboardGameRepository scoreboardGameRepository;

    /**
     * Writes the given games that have started, and optionally drops game days older than
     * {@code keepFromDate}.
     *
     * @param games        upstream games of one or more game days
     * @param keepFromDate oldest game date to keep ({@code yyyy-MM-dd}), or {@code null} to keep all
     * @return number of games written
     */
    @Transactional
    public int writeGames(List<ScoreResponseDto.ScoreGame> games, String keepFromDate) {
        LocalDateTime now = LocalDateTime.now();
        List<ScoreboardGame> rows = new ArrayList<>();
        for (ScoreResponseDto.ScoreGame game : games) {
            if (!hasStarted(game.getGameState())) {
                continue;
            }
            ScoreboardGame row = scoreboardGameRepository.findById(game.getId()).orElseGet(ScoreboardGame::new);
            rows.add(toScoreboardGame(row, game, now));
        }
        scoreboardGameRepository.saveAll(rows);
        if (keepFromDate != null) {
            scoreboardGameRepository.deleteByGameDateBefore(keepFromDate);
        }
        return rows.size();
    }

    static boolean hasStarted(GameState state) {
        return state == GameState.LIVE || state == GameState.CRIT || (state != null && state.isCompleted());
    }

    static ScoreboardGame toScoreboardGame(ScoreboardGame row, ScoreResponseDto.ScoreGame game, LocalDateTime now) {
        row.setGameId(game.getId());
        row.setGameDate(game.getGameDate());
        row.setSeasonId(game.getSeason() != null ? game.getSeason().toString() : null);
        row.setStartTimeUtc(game.getStartTimeUTC());
        row.setGameState(game.getGameState().name());
        row.setAwayTeamCode(game.getAwayTeam().getAbbrev());
        row.setAwayScore(game.getAwayTeam().getScore());
        row.setHomeTeamCode(game.getHomeTeam().getAbbrev());
        row.setHomeScore(game.getHomeTeam().getScore());
        row.setLastPeriodType(game.getGameOutcome() != null ? game.getGameOutcome().getLastPeriodType() : null);
        row.setLastUpdated(now);

        row.getGoals().clear();
        if (game.getGoals() != null) {
            int goalNumber = 0;
            for (ScoreResponseDto.Goal goal : game.getGoals()) {
                if (goal.getPeriodDescriptor() != null && SHOOTOUT.equals(goal.getPeriodDescriptor().getPeriodType())) {
                    continue;
                }
                row.getGoals().add(toScoreboardGoal(goal, ++goalNumber));
            }
        }
        return row;
    }

    static ScoreboardGoal toScoreboardGoal(ScoreResponseDto.Goal goal, int goalNumber) {
        ScoreboardGoal row = new ScoreboardGoal();
        row.setGoalNumber(goalNumber);
        row.setPeriod(goal.getPeriod());
        row.setPeriodType(goal.getPeriodDescriptor() != null ? goal.getPeriodDescriptor().getPeriodType() : null);
        row.setTimeInPeriod(goal.getTimeInPeriod());
        row.setTeamCode(goal.getTeamAbbrev());
        row.setScorerId(goal.getPlayerId());
        row.setScorerName(name(goal.getName()));
        List<ScoreResponseDto.Assist> assists = goal.getAssists() != null ? goal.getAssists() : List.of();
        if (!assists.isEmpty()) {
            row.setAssist1Id(assists.get(0).getPlayerId());
            row.setAssist1Name(name(assists.get(0).getName()));
        }
        if (assists.size() > 1) {
            row.setAssist2Id(assists.get(1).getPlayerId());
            row.setAssist2Name(name(assists.get(1).getName()));
        }
        row.setAwayScore(goal.getAwayScore());
        row.setHomeScore(goal.getHomeScore());
        row.setStrength(goal.getStrength());
        return row;
    }

    private static String name(PlayerInfoDto.NameDto name) {
        return name != null ? name.getName() : null;
    }
}
