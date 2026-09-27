package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.dto.nhlapi.TeamStandingsDto;
import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.TeamGame;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates a single season's historical backfill (spec User Story 1 / 3): teams, then that
 * season's team games, then players, then their per-game logs — in that order, because resolving a
 * player's {@code gameWon} depends on the team games already having been written (research R-005).
 * <p>
 * Never touches {@code current_season} (FR-005): it resolves and validates the requested season
 * itself, entirely independent of {@link DataSyncService}'s notion of the active season, and only
 * reuses {@link DataSyncService#persistStandings} — the one piece of standings-persistence logic
 * worth not duplicating.
 * <p>
 * Per-record failures (a bad player, a missing team schedule) are caught, recorded in the
 * {@link BackfillSummary} skip list, and do not abort the run (FR-007). Failures fetching the
 * season-wide standings are fatal, since nothing useful can be attributed to the season without
 * them.
 */
@Slf4j
@Service
public class SeasonBackfillService {

    private static final int PROGRESS_INTERVAL = 25;

    private final NhlApiService nhlApiService;
    private final DataSyncService dataSyncService;
    private final PlayerRepository playerRepository;
    private final PlayerFactory playerFactory;
    private final GameLogWriter gameLogWriter;
    private final TeamGameRepository teamGameRepository;
    private final BackfillLockService backfillLockService;
    private final long requestDelayMs;

    @Autowired
    public SeasonBackfillService(NhlApiService nhlApiService,
                                  DataSyncService dataSyncService,
                                  PlayerRepository playerRepository,
                                  PlayerFactory playerFactory,
                                  GameLogWriter gameLogWriter,
                                  TeamGameRepository teamGameRepository,
                                  BackfillLockService backfillLockService,
                                  @Value("${backfill.request-delay-ms:100}") long requestDelayMs) {
        this.nhlApiService = nhlApiService;
        this.dataSyncService = dataSyncService;
        this.playerRepository = playerRepository;
        this.playerFactory = playerFactory;
        this.gameLogWriter = gameLogWriter;
        this.teamGameRepository = teamGameRepository;
        this.backfillLockService = backfillLockService;
        this.requestDelayMs = requestDelayMs;
    }

    /**
     * Thrown when another backfill of the same season is already running (FR-014).
     */
    public static class AlreadyRunningException extends RuntimeException {
        public AlreadyRunningException(String seasonId) {
            super("Backfill already running for season " + seasonId);
        }
    }

    /**
     * Runs (or, in dry-run mode, validates and estimates) a backfill for the given request.
     *
     * @param request the validated season to load
     * @param dryRun  if true, reports expected counts without writing anything
     * @return the completed run summary
     * @throws AlreadyRunningException if another run for this season holds the lock
     */
    public BackfillSummary run(BackfillRequest request, boolean dryRun) {
        String seasonId = request.seasonId();

        if (!backfillLockService.tryLock(seasonId)) {
            throw new AlreadyRunningException(seasonId);
        }
        try {
            return dryRun ? dryRun(request) : load(request);
        } finally {
            backfillLockService.unlock(seasonId);
        }
    }

    private BackfillSummary dryRun(BackfillRequest request) {
        BackfillSummary summary = new BackfillSummary(request.seasonId());
        try {
            List<TeamStandingsDto> standings = nhlApiService.getTeamStandings(request.standingsDate());
            List<PlayerStandingDto> players = nhlApiService.getPlayerStandingsOrder(
                    request.seasonId(), request.gameType());
            log.info("Dry run for season {}: would write {} teams and up to {} players",
                    request.seasonId(), standings.size(), players.size());
            summary.addTeamsWritten(standings.size());
        } catch (Exception e) {
            log.error("Dry run failed while validating upstream data for season {}: {}",
                    request.seasonId(), e.getMessage(), e);
            summary.markFailed();
        }
        return summary.finish();
    }

    private BackfillSummary load(BackfillRequest request) {
        String seasonId = request.seasonId();
        BackfillSummary summary = new BackfillSummary(seasonId);

        List<TeamStandingsDto> standings;
        try {
            standings = nhlApiService.getTeamStandings(request.standingsDate());
            if (standings.isEmpty()) {
                throw new ApiClientException("empty standings response for date " + request.standingsDate());
            }
        } catch (Exception e) {
            log.error("Fatal: could not load standings for season {}: {}", seasonId, e.getMessage(), e);
            summary.markFailed();
            return summary.finish();
        }

        int teamsWritten = dataSyncService.persistStandings(seasonId, null, standings);
        summary.addTeamsWritten(teamsWritten);

        List<String> teamCodes = standings.stream()
                .map(s -> s.getTeamAbbrev().getDefaultValue())
                .toList();

        int teamsDone = 0;
        for (String teamCode : teamCodes) {
            logProgress(summary, "team-games", teamsDone++, teamCodes.size());
            pace();
            try {
                List<GameDto> schedule = nhlApiService.getTeamSchedule(teamCode, seasonId);
                int written = gameLogWriter.writeTeamGames(teamCode, seasonId, schedule);
                summary.addTeamGamesWritten(written);
            } catch (Exception e) {
                log.warn("Skipping team games for {} in season {}: {}", teamCode, seasonId, e.getMessage());
                summary.skip("team", teamCode, e.getMessage());
            }
        }

        List<PlayerStandingDto> playerStandings;
        try {
            playerStandings = nhlApiService.getPlayerStandingsOrder(seasonId, request.gameType());
        } catch (Exception e) {
            log.error("Fatal: could not load player standings for season {}: {}", seasonId, e.getMessage(), e);
            summary.markFailed();
            return summary.finish();
        }

        List<TeamGame> teamGamesForSeason = teamGameRepository.findBySeasonId(seasonId);
        int processed = 0;

        int playersDone = 0;
        for (PlayerStandingDto playerStanding : playerStandings) {
            logProgress(summary, "players", playersDone++, playerStandings.size());
            Long playerId = playerStanding.getId();
            pace();
            try {
                PlayerInfoDto playerInfo = nhlApiService.getPlayerInfo(playerId);

                // Deliberately no isActive() filter (research R-002): a player who has since
                // retired or changed teams must still be loaded for a past season they played.
                if (!Objects.equals(playerInfo.getPlayerId(), playerId)) {
                    throw new PlayerStatisticsException(
                            "Player ID mismatch between standings and player info API");
                }

                List<PlayerGameLogDto> gameLogs = nhlApiService.getPlayerGameLogs(
                        playerId, seasonId, request.gameType());
                Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, seasonId);

                try {
                    playerRepository.save(player);
                    playerRepository.flush();
                } catch (DataIntegrityViolationException e) {
                    playerRepository.saveAndFlush(player);
                }
                summary.addPlayerWritten();
                processed++;

                int gameLogsWritten = gameLogWriter.writePlayerGameLogs(
                        playerId, seasonId, gameLogs, teamGamesForSeason);
                summary.addGameLogsWritten(gameLogsWritten);
            } catch (PlayerStatisticsException | ApiClientException e) {
                log.warn("Skipping player {} in season {}: {}", playerId, seasonId, e.getMessage());
                summary.skip("player", String.valueOf(playerId), e.getMessage());
            }
        }

        log.info("Backfill for season {} processed {} players", seasonId, processed);
        return summary.finish();
    }

    /**
     * Logs progress every {@value #PROGRESS_INTERVAL} records (FR-008), in the format
     * {@code Backfill {season}: phase={phase} {n}/{total} ({pct}%) elapsed={hh:mm:ss}}.
     */
    private static void logProgress(BackfillSummary summary, String phase, int done, int total) {
        if (done == 0 || done % PROGRESS_INTERVAL != 0) {
            return;
        }
        log.info("Backfill {}: phase={} {}/{} ({}%) elapsed={}", summary.seasonId(), phase, done, total,
                done * 100 / total, BackfillSummary.formatDuration(summary.duration()));
    }

    private void pace() {
        if (requestDelayMs <= 0) {
            return;
        }
        try {
            Thread.sleep(requestDelayMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
