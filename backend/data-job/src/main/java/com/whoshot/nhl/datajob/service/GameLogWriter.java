package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.TeamGameIndex;
import com.whoshot.nhl.domain.entity.GameLog;
import com.whoshot.nhl.domain.entity.TeamGame;
import com.whoshot.nhl.domain.entity.TeamNextGame;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import com.whoshot.nhl.domain.repository.TeamNextGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Maps and upserts per-game records for players and teams into {@code game_logs} and
 * {@code team_games}. Shared by the live sync and the season backfill (research R-003) so that
 * both current and historical seasons populate the game-log graphs.
 * <p>
 * All writes are upserts on each table's natural key — {@code (playerId, gameId)} and
 * {@code (teamCode, gameId)} — so re-running a load converges without duplicates (FR-006).
 * <p>
 * Each write loads the existing rows for that player or team and season in one query and saves
 * them together, rather than looking up every game separately (issue #29). A game id encodes its
 * season, so the season-scoped preload sees every row the per-game lookup would have found.
 */
@Service
@RequiredArgsConstructor
public class GameLogWriter {

    private final GameLogRepository gameLogRepository;
    private final TeamGameRepository teamGameRepository;
    private final TeamNextGameRepository teamNextGameRepository;

    static final int PLAYOFF_GAME_TYPE = 3;

    /**
     * Writes one team's completed regular-season games for a season, in chronological order with a
     * dense {@code gameNumber}.
     *
     * @param teamCode team abbreviation whose schedule this is
     * @param seasonId season the games belong to
     * @param games    that team's full season schedule as returned by the upstream API
     * @return number of games written
     */
    @Transactional
    public int writeTeamGames(String teamCode, String seasonId, List<GameDto> games) {
        List<GameDto> ordered = chronologicalCompletedRegularSeasonGames(games);
        Map<Long, TeamGame> existing = byGameId(
                teamGameRepository.findByTeamCodeAndSeasonIdOrderByGameDateDesc(teamCode, seasonId),
                TeamGame::getGameId);

        List<TeamGame> rows = new ArrayList<>(ordered.size());
        int gameNumber = 0;
        for (GameDto game : ordered) {
            gameNumber++;
            TeamGame teamGame = existing.getOrDefault(game.getId(), new TeamGame());
            rows.add(toTeamGame(teamGame, teamCode, seasonId, game, gameNumber));
        }
        teamGameRepository.saveAll(rows);
        teamGameRepository.flush();
        writeNextGame(teamCode, seasonId, games);
        return ordered.size();
    }

    /**
     * Stores the team's next unfinished regular-season or playoff game, or removes the stored one
     * when the schedule has none left (season over, or a historical season being backfilled).
     */
    private void writeNextGame(String teamCode, String seasonId, List<GameDto> games) {
        var key = new TeamNextGame.TeamNextGameKey(teamCode, seasonId);
        nextGame(games).ifPresentOrElse(
                game -> teamNextGameRepository.save(toTeamNextGame(
                        teamNextGameRepository.findById(key).orElse(new TeamNextGame()), teamCode, seasonId, game)),
                () -> teamNextGameRepository.deleteById(key));
    }

    /**
     * Records games in pre-game or in progress as both teams' next game, so their state follows the
     * live poll: schedules are only rewritten once a team's game has finished.
     *
     * @param seasonId    season the games belong to
     * @param activeGames games from the league schedule currently in pre-game or in progress
     */
    @Transactional
    public void writeActiveNextGames(String seasonId, List<GameDto> activeGames) {
        for (GameDto game : activeGames) {
            // Same eligibility as a schedule's next game: regular season or playoffs, not finished
            if (nextGame(List.of(game)).isEmpty()) {
                continue;
            }
            for (String teamCode : List.of(game.getHomeTeam().getAbbrev(), game.getAwayTeam().getAbbrev())) {
                var key = new TeamNextGame.TeamNextGameKey(teamCode, seasonId);
                teamNextGameRepository.save(toTeamNextGame(
                        teamNextGameRepository.findById(key).orElse(new TeamNextGame()), teamCode, seasonId, game));
            }
        }
    }

    /**
     * Earliest regular-season or playoff game that has not finished. A game in progress counts, so
     * a team playing right now shows its current opponent.
     */
    static Optional<GameDto> nextGame(List<GameDto> games) {
        return games.stream()
                .filter(g -> g.getGameType() != null
                        && (g.getGameType() == BackfillRequest.REGULAR_SEASON_GAME_TYPE || g.getGameType() == PLAYOFF_GAME_TYPE))
                .filter(g -> g.getGameState() != null && !g.getGameState().isCompleted())
                .filter(g -> g.getStartTimeUTC() != null && g.getHomeTeam() != null && g.getAwayTeam() != null)
                .min(Comparator.comparing(GameDto::getStartTimeUTC));
    }

    /**
     * Writes one player's per-game logs for a season, in chronological order with a dense
     * {@code gameNumber}, resolving {@code gameWon} against the team games already loaded for the
     * season.
     *
     * @param playerId          NHL player identifier
     * @param seasonId          season the game logs belong to
     * @param gameLogs          upstream game log, most-recent-first
     * @param teamGames          outcomes of every team game already written for this season, used
     *                          to resolve {@code gameWon} without an extra API call
     * @return number of game logs written
     */
    @Transactional
    public int writePlayerGameLogs(Long playerId, String seasonId, List<PlayerGameLogDto> gameLogs,
                                    TeamGameIndex teamGames) {
        List<PlayerGameLogDto> ordered = chronological(gameLogs);
        Map<Long, GameLog> existing = byGameId(
                gameLogRepository.findByPlayerIdAndSeasonId(playerId, seasonId), GameLog::getGameId);

        List<GameLog> rows = new ArrayList<>(ordered.size());
        int gameNumber = 0;
        for (PlayerGameLogDto dto : ordered) {
            gameNumber++;
            Boolean gameWon = teamGames.wonAgainst(dto.getGameId(), dto.getOpponentAbbrev());
            GameLog gameLog = existing.getOrDefault(dto.getGameId(), new GameLog());
            rows.add(toGameLog(gameLog, playerId, seasonId, dto, gameNumber, gameWon));
        }
        gameLogRepository.saveAll(rows);
        gameLogRepository.flush();
        return ordered.size();
    }

    /** Indexes existing rows by game id; a duplicate row for the same game keeps the first one. */
    private static <T> Map<Long, T> byGameId(List<T> rows, Function<T, Long> gameId) {
        Map<Long, T> index = new HashMap<>();
        for (T row : rows) {
            index.putIfAbsent(gameId.apply(row), row);
        }
        return index;
    }

    /**
     * Filters a team's raw schedule down to completed regular-season games and sorts them
     * chronologically. A game counts only once its state is final: an in-progress game already
     * carries (running) scores, so scores alone would record a live 0-0 as a loss for both teams.
     */
    static List<GameDto> chronologicalCompletedRegularSeasonGames(List<GameDto> games) {
        return games.stream()
                .filter(g -> g.getGameType() != null && g.getGameType() == BackfillRequest.REGULAR_SEASON_GAME_TYPE)
                .filter(g -> g.getGameState() != null && g.getGameState().isCompleted())
                .filter(g -> g.getHomeTeam() != null && g.getHomeTeam().getScore() != null)
                .filter(g -> g.getAwayTeam() != null && g.getAwayTeam().getScore() != null)
                .sorted(Comparator.comparing(GameDto::getStartTimeUTC))
                .toList();
    }

    /**
     * Reverses the upstream player game log, which is returned most-recent-first, into
     * chronological (oldest-first) order for dense game-number assignment.
     */
    static List<PlayerGameLogDto> chronological(List<PlayerGameLogDto> mostRecentFirst) {
        List<PlayerGameLogDto> copy = new ArrayList<>(mostRecentFirst);
        Collections.reverse(copy);
        return copy;
    }

    /**
     * Parses a {@code "mm:ss"} time-on-ice string into whole seconds.
     */
    static int toiSeconds(String toi) {
        if (toi == null || toi.isBlank()) {
            return 0;
        }
        String[] parts = toi.split(":");
        try {
            int minutes = Integer.parseInt(parts[0].trim());
            int seconds = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
            return minutes * 60 + seconds;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("invalid time on ice '" + toi + "'", e);
        }
    }

    /**
     * Checks that every game log can be mapped, so a malformed record is rejected before anything
     * for that player is written.
     *
     * @param gameLogs upstream game logs for one player
     * @throws IllegalArgumentException naming the first unmappable game
     */
    static void validatePlayerGameLogs(List<PlayerGameLogDto> gameLogs) {
        for (PlayerGameLogDto dto : gameLogs) {
            if (dto.getGameId() == null || dto.getGameDate() == null) {
                throw new IllegalArgumentException("game log missing game id or date");
            }
            try {
                toiSeconds(dto.getToi());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException(e.getMessage() + " in game " + dto.getGameId(), e);
            }
        }
    }

    static TeamGame toTeamGame(TeamGame target, String teamCode, String seasonId, GameDto game, int gameNumber) {
        boolean homeGame = teamCode.equals(game.getHomeTeam().getAbbrev());
        int goalsFor = homeGame ? game.getHomeTeam().getScore() : game.getAwayTeam().getScore();
        int goalsAgainst = homeGame ? game.getAwayTeam().getScore() : game.getHomeTeam().getScore();
        String opponent = homeGame ? game.getAwayTeam().getAbbrev() : game.getHomeTeam().getAbbrev();
        boolean won = goalsFor > goalsAgainst;
        String lastPeriodType = game.getGameOutcome() != null ? game.getGameOutcome().getLastPeriodType() : null;
        boolean overtimeLoss = !won && ("OT".equals(lastPeriodType) || "SO".equals(lastPeriodType));

        target.setGameId(game.getId());
        target.setTeamCode(teamCode);
        // Upstream's local game date, matching game_logs.game_date; the UTC start time of an
        // evening game falls on the next calendar day.
        target.setGameDate(game.getGameDate() != null ? game.getGameDate() : datePart(game.getStartTimeUTC()));
        target.setOpponentTeamCode(opponent);
        target.setHomeGame(homeGame);
        target.setGoalsFor(goalsFor);
        target.setGoalsAgainst(goalsAgainst);
        target.setWon(won);
        target.setOvertimeLoss(overtimeLoss);
        target.setGameType(String.valueOf(game.getGameType()));
        target.setSeasonId(seasonId);
        target.setGameNumber(gameNumber);
        return target;
    }

    static TeamNextGame toTeamNextGame(TeamNextGame target, String teamCode, String seasonId, GameDto game) {
        boolean homeGame = teamCode.equals(game.getHomeTeam().getAbbrev());
        target.setTeamCode(teamCode);
        target.setSeasonId(seasonId);
        target.setGameId(game.getId());
        target.setGameDate(game.getGameDate() != null ? game.getGameDate() : datePart(game.getStartTimeUTC()));
        target.setStartTimeUtc(game.getStartTimeUTC());
        target.setGameState(game.getGameState().name());
        target.setOpponentTeamCode(homeGame ? game.getAwayTeam().getAbbrev() : game.getHomeTeam().getAbbrev());
        target.setHomeGame(homeGame);
        target.setLastUpdated(LocalDateTime.now());
        return target;
    }

    static GameLog toGameLog(GameLog target, Long playerId, String seasonId, PlayerGameLogDto dto,
                              int gameNumber, Boolean gameWon) {
        target.setPlayerId(playerId);
        target.setGameId(dto.getGameId());
        target.setGameDate(dto.getGameDate());
        target.setOpponentTeamCode(dto.getOpponentAbbrev());
        target.setHomeGame("H".equals(dto.getHomeRoadFlag()));
        target.setGoals(dto.getGoals());
        target.setAssists(dto.getAssists());
        target.setPoints(StatisticsCalculationService.gamePoints(dto));
        target.setPlusMinus(dto.getPlusMinus());
        target.setShots(dto.getShots());
        target.setTimeOnIce(toiSeconds(dto.getToi()));
        target.setGameWon(gameWon);
        target.setSeasonId(seasonId);
        target.setGameNumber(gameNumber);
        return target;
    }

    private static String datePart(String startTimeUTC) {
        return startTimeUTC.length() >= 10 ? startTimeUTC.substring(0, 10) : startTimeUTC;
    }
}
