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
import com.whoshot.nhl.datajob.model.TeamGameIndex;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Objects;

/**
 * Orchestrates a single season's historical backfill (spec User Story 1 / 3): teams, then that
 * season's team games, then players, then their per-game logs — in that order, because resolving a
 * player's {@code gameWon} depends on the team games already having been written (research R-005).
 * <p>
 * Never touches {@code current_season} (FR-005): it resolves and validates the requested season
 * itself, entirely independent of {@link DataSyncService}'s notion of the active season, and
 * persists through the same atomic units as live sync ({@link SeasonDataWriter}).
 * <p>
 * Team schedules and players are fetched in parallel through the {@link FetchPipeline}; this
 * thread alone writes them, in standings order, and updates the summary.
 * <p>
 * Per-record failures (a bad player, a missing team schedule, malformed upstream data) are caught,
 * recorded in the {@link BackfillSummary} skip list, and do not abort the run (FR-007). Losing the
 * database does abort it. Failures fetching the
 * season-wide standings are fatal, since nothing useful can be attributed to the season without
 * them.
 */
@Slf4j
@Service
public class SeasonBackfillService {

    private static final int PROGRESS_INTERVAL = 25;

    private final NhlApiService nhlApiService;
    private final SeasonDataWriter seasonDataWriter;
    private final PlayerFactory playerFactory;
    private final GameLogWriter gameLogWriter;
    private final TeamGameRepository teamGameRepository;
    private final BackfillLockService backfillLockService;
    private final FetchPipeline fetchPipeline;

    private IngestionMetrics metrics = IngestionMetrics.standalone();

    @Autowired(required = false)
    void setMetrics(IngestionMetrics metrics) {
        this.metrics = metrics;
    }

    @Autowired
    public SeasonBackfillService(NhlApiService nhlApiService,
                                  SeasonDataWriter seasonDataWriter,
                                  PlayerFactory playerFactory,
                                  GameLogWriter gameLogWriter,
                                  TeamGameRepository teamGameRepository,
                                  BackfillLockService backfillLockService,
                                  FetchPipeline fetchPipeline) {
        this.nhlApiService = nhlApiService;
        this.seasonDataWriter = seasonDataWriter;
        this.playerFactory = playerFactory;
        this.gameLogWriter = gameLogWriter;
        this.teamGameRepository = teamGameRepository;
        this.backfillLockService = backfillLockService;
        this.fetchPipeline = fetchPipeline;
    }

    /**
     * The per-record {@code backfill.request-delay-ms} pacing is replaced by the per-attempt
     * request budget ({@code nhle.api.requests.*}), which also covers retries and other jobs.
     */
    @Autowired
    void warnIfLegacyDelayConfigured(@Value("${backfill.request-delay-ms:-1}") long legacyRequestDelayMs) {
        if (legacyRequestDelayMs >= 0) {
            log.warn("backfill.request-delay-ms is deprecated and ignored; pace requests with "
                    + "nhle.api.requests.requests-per-second instead");
        }
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
        IngestionMetrics.Run run = metrics.startRun("backfill");

        List<TeamStandingsDto> standings;
        try {
            standings = nhlApiService.getTeamStandings(request.standingsDate());
            if (standings.isEmpty()) {
                throw new ApiClientException("empty standings response for date " + request.standingsDate());
            }
        } catch (Exception e) {
            IngestionFailures.rethrowIfFatal(e);
            log.error("Fatal: could not load standings for season {}: {}", seasonId, e.getMessage(), e);
            summary.markFailed();
            return summary.finish();
        }

        int teamsWritten = seasonDataWriter.persistStandings(seasonId, null, standings);
        summary.addTeamsWritten(teamsWritten);

        List<String> teamCodes = standings.stream()
                .map(s -> s.getTeamAbbrev().getDefaultValue())
                .toList();

        int[] teamsDone = {0};
        fetchPipeline.<String, List<GameDto>>run(teamCodes,
                teamCode -> nhlApiService.getTeamSchedule(teamCode, seasonId),
                (teamCode, schedule) -> {
                    logProgress(summary, "team-games", teamsDone[0]++, teamCodes.size());
                    try {
                        if (!schedule.succeeded()) {
                            throw schedule.failure();
                        }
                        summary.addTeamGamesWritten(gameLogWriter.writeTeamGames(teamCode, seasonId, schedule.value()));
                    } catch (Exception e) {
                        IngestionFailures.rethrowIfFatal(e);
                        log.warn("Skipping team games for {} in season {}: {}", teamCode, seasonId, e.getMessage());
                        summary.skip("team", teamCode, e.getMessage());
                    }
                });

        List<PlayerStandingDto> playerStandings;
        try {
            playerStandings = nhlApiService.getPlayerStandingsOrder(seasonId, request.gameType());
        } catch (Exception e) {
            IngestionFailures.rethrowIfFatal(e);
            log.error("Fatal: could not load player standings for season {}: {}", seasonId, e.getMessage(), e);
            summary.markFailed();
            return summary.finish();
        }

        // Built after every team schedule has been written or skipped: the barrier gameWon needs.
        TeamGameIndex teamGames = TeamGameIndex.of(teamGameRepository.findBySeasonId(seasonId));
        int[] playersDone = {0};

        fetchPipeline.<PlayerStandingDto, FetchedPlayer>run(playerStandings,
                playerStanding -> fetchPlayer(playerStanding, request),
                (playerStanding, fetched) -> {
                    logProgress(summary, "players", playersDone[0]++, playerStandings.size());
                    Long playerId = playerStanding.getId();
                    try {
                        if (!fetched.succeeded()) {
                            throw fetched.failure();
                        }
                        // Player and game logs commit or roll back together, so a skip never
                        // leaves a player row without its game logs.
                        long persistStarted = System.nanoTime();
                        int gameLogsWritten = seasonDataWriter.writePlayer(
                                fetched.value().player(), fetched.value().gameLogs(), teamGames);
                        run.recordPersist(persistStarted);
                        summary.addPlayerWritten();
                        summary.addGameLogsWritten(gameLogsWritten);
                    } catch (Exception e) {
                        // Any record-level fault (points mismatch, exhausted retries, malformed
                        // upstream data) skips only this player (FR-007); losing the database or
                        // a stop request ends the run.
                        IngestionFailures.rethrowIfFatal(e);
                        log.warn("Skipping player {} in season {}: {}", playerId, seasonId, e.getMessage());
                        summary.skip("player", String.valueOf(playerId), e.getMessage());
                    }
                });

        log.info("Backfill for season {} processed {} players", seasonId, summary.playersWritten());
        run.finish(summary.playersWritten(), 0, summary.skipped().size(), 0);
        return summary.finish();
    }

    /** A fetched, validated player and their game logs, ready to write. */
    private record FetchedPlayer(Player player, List<PlayerGameLogDto> gameLogs) {
    }

    /**
     * Fetches and validates one player on a fetch worker: plain values only, no database access.
     */
    private FetchedPlayer fetchPlayer(PlayerStandingDto playerStanding, BackfillRequest request)
            throws PlayerStatisticsException {
        Long playerId = playerStanding.getId();
        PlayerInfoDto playerInfo = nhlApiService.getPlayerInfo(playerId);

        // Deliberately no isActive() filter (research R-002): a player who has since
        // retired or changed teams must still be loaded for a past season they played.
        if (!Objects.equals(playerInfo.getPlayerId(), playerId)) {
            throw new PlayerStatisticsException("Player ID mismatch between standings and player info API");
        }

        List<PlayerGameLogDto> gameLogs = nhlApiService.getPlayerGameLogs(
                playerId, request.seasonId(), request.gameType());
        // Reject a malformed record before building or writing anything for this player.
        GameLogWriter.validatePlayerGameLogs(gameLogs);
        Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, request.seasonId());
        return new FetchedPlayer(player, gameLogs);
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
}
