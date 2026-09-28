package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.domain.entity.GameLog;
import com.whoshot.nhl.domain.entity.TeamGame;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Maps and upserts per-game records for players and teams into {@code game_logs} and
 * {@code team_games}. Shared by the live sync and the season backfill (research R-003) so that
 * both current and historical seasons populate the game-log graphs.
 * <p>
 * All writes are upserts on each table's natural key — {@code (playerId, gameId)} and
 * {@code (teamCode, gameId)} — so re-running a load converges without duplicates (FR-006).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GameLogWriter {

    private final GameLogRepository gameLogRepository;
    private final TeamGameRepository teamGameRepository;

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

        int gameNumber = 0;
        for (GameDto game : ordered) {
            gameNumber++;
            TeamGame teamGame = teamGameRepository.findByTeamCodeAndGameId(teamCode, game.getId())
                    .orElse(new TeamGame());
            toTeamGame(teamGame, teamCode, seasonId, game, gameNumber);
            teamGameRepository.save(teamGame);
        }
        teamGameRepository.flush();
        return ordered.size();
    }

    /**
     * Writes one player's per-game logs for a season, in chronological order with a dense
     * {@code gameNumber}, resolving {@code gameWon} against the team games already loaded for the
     * season.
     *
     * @param playerId          NHL player identifier
     * @param seasonId          season the game logs belong to
     * @param gameLogs          upstream game log, most-recent-first
     * @param teamGamesForSeason every team game already written for this season, used to resolve
     *                          {@code gameWon} without an extra API call
     * @return number of game logs written
     */
    @Transactional
    public int writePlayerGameLogs(Long playerId, String seasonId, List<PlayerGameLogDto> gameLogs,
                                    List<TeamGame> teamGamesForSeason) {
        List<PlayerGameLogDto> ordered = chronological(gameLogs);

        int gameNumber = 0;
        for (PlayerGameLogDto dto : ordered) {
            gameNumber++;
            Boolean gameWon = resolveGameWon(dto.getGameId(), dto.getOpponentAbbrev(), teamGamesForSeason);
            GameLog gameLog = gameLogRepository.findByPlayerIdAndGameId(playerId, dto.getGameId())
                    .orElse(new GameLog());
            toGameLog(gameLog, playerId, seasonId, dto, gameNumber, gameWon);
            gameLogRepository.save(gameLog);
        }
        gameLogRepository.flush();
        return ordered.size();
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
        java.util.Collections.reverse(copy);
        return copy;
    }

    /**
     * Resolves whether the player's team won a given game by finding the {@code team_games} row
     * belonging to the player's own team for that game: among the (up to) two rows recorded for a
     * game, the row whose {@code opponentTeamCode} equals the player's recorded opponent is the
     * player's own team's row.
     *
     * @return the result, or {@code null} if no matching team game has been loaded yet
     */
    static Boolean resolveGameWon(Long gameId, String opponentTeamCode, List<TeamGame> teamGamesForSeason) {
        return teamGamesForSeason.stream()
                .filter(tg -> tg.getGameId().equals(gameId) && opponentTeamCode.equals(tg.getOpponentTeamCode()))
                .findFirst()
                .map(TeamGame::getWon)
                .orElse(null);
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

    static GameLog toGameLog(GameLog target, Long playerId, String seasonId, PlayerGameLogDto dto,
                              int gameNumber, Boolean gameWon) {
        target.setPlayerId(playerId);
        target.setGameId(dto.getGameId());
        target.setGameDate(dto.getGameDate());
        target.setOpponentTeamCode(dto.getOpponentAbbrev());
        target.setHomeGame("H".equals(dto.getHomeRoadFlag()));
        target.setGoals(dto.getGoals());
        target.setAssists(dto.getAssists());
        target.setPoints(dto.getPoints());
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
