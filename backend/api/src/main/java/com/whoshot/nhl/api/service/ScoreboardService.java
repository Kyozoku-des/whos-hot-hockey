package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.dto.ScoreboardDto;
import com.whoshot.nhl.domain.entity.GameLog;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.ScoreboardGame;
import com.whoshot.nhl.domain.entity.ScoreboardGoal;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.ScoreboardGameRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Service providing the homepage score ticker (issue #42).
 */
@Service
public class ScoreboardService {

    static final List<String> LIVE_STATES = List.of("LIVE", "CRIT");
    static final List<String> COMPLETED_STATES = List.of("OVER", "FINAL", "OFF");

    private final ScoreboardGameRepository scoreboardGameRepository;
    private final PlayerRepository playerRepository;
    private final GameLogRepository gameLogRepository;
    private final double hotThreshold;

    /**
     * @param hotThreshold points per game over the last ten games at or above which a player is hot
     */
    public ScoreboardService(ScoreboardGameRepository scoreboardGameRepository,
                             PlayerRepository playerRepository,
                             GameLogRepository gameLogRepository,
                             @Value("${scoreboard.hot-points-per-game:1.5}") double hotThreshold) {
        this.scoreboardGameRepository = scoreboardGameRepository;
        this.playerRepository = playerRepository;
        this.gameLogRepository = gameLogRepository;
        this.hotThreshold = hotThreshold;
    }

    /**
     * Today's games while any is in progress, otherwise the most recent day with finished games.
     *
     * @return that day's games with their goals, or an empty scoreboard when there are none
     */
    public ScoreboardDto getLatestScoreboard() {
        String liveDate = scoreboardGameRepository.findLatestGameDateWithState(LIVE_STATES);
        String gameDate = liveDate != null
                ? liveDate
                : scoreboardGameRepository.findLatestGameDateWithState(COMPLETED_STATES);
        if (gameDate == null) {
            return ScoreboardDto.empty();
        }

        List<ScoreboardGame> games = scoreboardGameRepository.findByGameDateOrderByStartTimeUtcAscGameIdAsc(gameDate);
        PlayerFlags flags = playerFlags(games, gameDate);
        return new ScoreboardDto(gameDate, liveDate != null, games.stream()
                .map(game -> toGameDto(game, flags))
                .toList());
    }

    /** Streak and hot flags of every player with a point on the day, looked up in two queries per season. */
    private PlayerFlags playerFlags(List<ScoreboardGame> games, String gameDate) {
        Map<String, Set<Long>> playerIdsBySeason = new HashMap<>();
        for (ScoreboardGame game : games) {
            Set<Long> ids = playerIdsBySeason.computeIfAbsent(game.getSeasonId(), season -> new HashSet<>());
            game.getGoals().stream().flatMap(ScoreboardService::pointIds).forEach(ids::add);
        }

        Set<Long> streakExtended = new HashSet<>();
        Set<Long> hot = new HashSet<>();
        playerIdsBySeason.forEach((seasonId, playerIds) -> {
            if (seasonId == null || playerIds.isEmpty()) {
                return;
            }
            gameLogRepository.findPreviousGames(playerIds, seasonId, gameDate).stream()
                    .filter(previous -> previous.getPoints() != null && previous.getPoints() > 0)
                    .map(GameLog::getPlayerId)
                    .forEach(streakExtended::add);
            playerRepository.findAllById(playerIds.stream()
                            .map(playerId -> new Player.PlayerId(playerId, seasonId))
                            .toList()).stream()
                    .filter(player -> player.getPointsPerLastNGames() != null
                            && player.getPointsPerLastNGames() >= hotThreshold)
                    .map(player -> player.getId().playerId())
                    .forEach(hot::add);
        });
        return new PlayerFlags(streakExtended, hot);
    }

    private static Stream<Long> pointIds(ScoreboardGoal goal) {
        return Stream.of(goal.getScorerId(), goal.getAssist1Id(), goal.getAssist2Id()).filter(id -> id != null);
    }

    private record PlayerFlags(Set<Long> streakExtended, Set<Long> hot) {
        ScoreboardDto.Point point(Long playerId, String name) {
            return new ScoreboardDto.Point(playerId, name, streakExtended.contains(playerId), hot.contains(playerId));
        }
    }

    private static ScoreboardDto.Game toGameDto(ScoreboardGame game, PlayerFlags flags) {
        return new ScoreboardDto.Game(
                game.getGameId(),
                game.getGameState(),
                game.getStartTimeUtc(),
                game.getAwayTeamCode(),
                game.getAwayScore(),
                game.getHomeTeamCode(),
                game.getHomeScore(),
                game.getLastPeriodType(),
                game.getGoals().stream().map(goal -> toGoalDto(goal, flags)).toList());
    }

    private static ScoreboardDto.Goal toGoalDto(ScoreboardGoal goal, PlayerFlags flags) {
        List<ScoreboardDto.Point> assists = new ArrayList<>(2);
        if (goal.getAssist1Id() != null) {
            assists.add(flags.point(goal.getAssist1Id(), goal.getAssist1Name()));
        }
        if (goal.getAssist2Id() != null) {
            assists.add(flags.point(goal.getAssist2Id(), goal.getAssist2Name()));
        }
        return new ScoreboardDto.Goal(
                goal.getGoalNumber(),
                goal.getPeriod(),
                goal.getPeriodType(),
                goal.getTimeInPeriod(),
                goal.getTeamCode(),
                goal.getAwayScore(),
                goal.getHomeScore(),
                goal.getStrength(),
                flags.point(goal.getScorerId(), goal.getScorerName()),
                assists);
    }
}
