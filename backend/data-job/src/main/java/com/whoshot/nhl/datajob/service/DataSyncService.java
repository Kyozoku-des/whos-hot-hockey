package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.*;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.datajob.model.TeamGameIndex;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.stream.Collectors;

/**
 * Coordinates retrieval, transformation, and persistence of player season data.
 * <p>
 * Holds no transaction across network calls: each write goes through a short unit in
 * {@link SeasonDataWriter} or {@link GameLogWriter}, so a sync commits per record and readers may
 * see a partially refreshed season while it runs. A failed record keeps its previous valid row.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private static final int REGULAR_SEASON_GAME_TYPE = 2;

    private final NhlApiService nhlApiService;
    private final PlayerRepository playerRepository;
    private final PlayerFactory playerFactory;
    private final SeasonDataWriter seasonDataWriter;
    private final TeamGameRepository teamGameRepository;
    private final GameLogWriter gameLogWriter;
    private final FetchPipeline fetchPipeline;
    private final PlayerInfoCache playerInfoCache;

    private IngestionMetrics metrics = IngestionMetrics.standalone();

    @Autowired(required = false)
    void setMetrics(IngestionMetrics metrics) {
        this.metrics = metrics;
    }
    private SeasonDto season;
    @Getter
    private LocalDateTime firstGameTimeForToday;
    @Getter
    private LocalDateTime lastGameTimeForToday;

    /**
     * Refreshes the active season and daily game window for live sync only.
     * Called explicitly by the scheduler; constructing this bean for backfill has no side effects.
     */
    public void initialize() {
        checkInterrupted();
        setSeason();
        checkInterrupted();
        setFirstAndLastGameTimesForToday();
    }

    /** Retains the most recently started season during playoffs and the offseason. */
    private void setSeason() {
        this.season = selectSeason(nhlApiService.getSeasons(), LocalDateTime.now(ZoneOffset.UTC));
        seasonDataWriter.activateSeason(this.season);
    }

    /** The season resolved by the last {@link #initialize()}. */
    public String getSeasonId() {
        return season.getId();
    }

    static SeasonDto selectSeason(List<SeasonDto> seasons, LocalDateTime now) {
        return seasons.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.getStartDate() != null)
                .filter(candidate -> !candidate.getStartDate().isAfter(now))
                .max(Comparator.comparing(SeasonDto::getStartDate))
                .orElseThrow(() -> new IllegalStateException("NHL API returned no season that has started"));
    }

    /**
     * Fetches today's game schedule and determines the earliest and latest game
     * start times.
     */
    public void setFirstAndLastGameTimesForToday() {
        List<GameDto> games = nhlApiService.getLeagueSchedule();

        LocalDateTime todayStart = LocalDateTime.now(ZoneOffset.UTC).toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);

        List<LocalDateTime> todayGameTimes = games.stream()
                .map(game -> LocalDateTime.parse(game.getStartTimeUTC(), DateTimeFormatter.ISO_DATE_TIME))
                .filter(time -> !time.isBefore(todayStart) && time.isBefore(todayEnd))
                .toList();

        firstGameTimeForToday = todayGameTimes.stream()
                .min(LocalDateTime::compareTo)
                .orElse(null);

        lastGameTimeForToday = todayGameTimes.stream()
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /**
     * Synchronizes active-player statistics for the resolved season, fetching players in parallel
     * through the {@link FetchPipeline} and writing each one (with their game logs) as it arrives.
     * A player that cannot be fetched or validated is skipped and keeps their previous row; only a
     * stop request or a lost database ends the sync early.
     *
     * @return what the sync wrote and skipped
     */
    public SyncResult syncPlayers() {
        checkInterrupted();
        String seasonId = season.getId();
        log.info("Starting player sync for season {}", seasonId);

        var players = nhlApiService.getPlayerStandingsOrder(seasonId, REGULAR_SEASON_GAME_TYPE);
        SyncResult result = syncPlayers(seasonId, players, null);
        log.info("Player sync completed: {}", result);
        return result;
    }

    /**
     * Synchronizes only players belonging to the specified teams, including their game logs.
     * Used during game-time sync: players are picked from the roster stored by the last full sync,
     * so players on teams that are not playing cost no API calls. Players in the standings with no
     * stored row yet (season debuts, opening night) are also checked, since their team is unknown.
     * Players traded to a playing team since the last full sync are picked up by the full sync that
     * ends the game window.
     *
     * @return number of players written
     */
    public int syncPlayersForTeams(Set<String> teamCodes) {
        checkInterrupted();
        if (teamCodes.isEmpty()) {
            return 0;
        }
        String seasonId = season.getId();
        log.info("Starting scoped player sync for teams {} in season {}", teamCodes, seasonId);

        Set<Long> rosterIds = teamCodes.stream()
                .flatMap(teamCode -> playerRepository.findByTeamCodeAndIdSeasonId(teamCode, seasonId).stream())
                .map(player -> player.getId().playerId())
                .collect(Collectors.toSet());
        Set<Long> storedIds = playerRepository.findPlayerIdsBySeasonId(seasonId);
        var players = nhlApiService.getPlayerStandingsOrder(seasonId, REGULAR_SEASON_GAME_TYPE).stream()
                .filter(standing -> rosterIds.contains(standing.getId()) || !storedIds.contains(standing.getId()))
                .toList();
        SyncResult result = syncPlayers(seasonId, players, teamCodes);

        log.info("Scoped player sync completed for teams {}: {}", teamCodes, result);
        return result.written();
    }

    /**
     * Counts from one player sync. {@code filtered} players were fetched but intentionally not
     * written (inactive, or no longer on a playing team); {@code skipped} ones failed and kept
     * their previous row, {@code deferred} of them because their upstream totals did not agree.
     */
    public record SyncResult(int written, int filtered, int skipped, int deferred) {
        @Override
        public String toString() {
            return "%d written, %d filtered, %d skipped (%d deferred)".formatted(written, filtered, skipped, deferred);
        }
    }

    /** A fetched player ready to write, or the reason it is filtered out. */
    private record FetchedPlayer(Player player, List<PlayerGameLogDto> gameLogs, String filteredReason) {
        static FetchedPlayer filtered(String reason) {
            return new FetchedPlayer(null, null, reason);
        }
    }

    private SyncResult syncPlayers(String seasonId, List<PlayerStandingDto> players, Set<String> onlyTeamCodes) {
        // Team games already written for the season resolve gameWon for each player's game log
        // (research R-005). Loaded once, after the team-game barrier, and shared read-only.
        TeamGameIndex teamGames = TeamGameIndex.of(teamGameRepository.findBySeasonId(seasonId));
        int[] counts = new int[4]; // written, filtered, skipped, deferred
        IngestionMetrics.Run run = metrics.startRun(onlyTeamCodes == null ? "players-full" : "players-scoped");

        fetchPipeline.<PlayerStandingDto, FetchedPlayer>run(players,
                standing -> fetchPlayer(standing, seasonId, onlyTeamCodes),
                (standing, fetched) -> {
                    if (!fetched.succeeded()) {
                        skipPlayer(standing.getId(), seasonId, fetched.failure(), counts);
                    } else if (fetched.value().filteredReason() != null) {
                        log.debug("Not writing player {}: {}", standing.getId(), fetched.value().filteredReason());
                        counts[1]++;
                    } else {
                        long persistStarted = System.nanoTime();
                        try {
                            // One transaction for the player and their game logs (research R-003:
                            // nothing else writes the current-season game_logs line of the graphs).
                            seasonDataWriter.writePlayer(fetched.value().player(), fetched.value().gameLogs(), teamGames);
                            run.recordPersist(persistStarted);
                            counts[0]++;
                        } catch (RuntimeException e) {
                            skipPlayer(standing.getId(), seasonId, e, counts);
                        }
                    }
                });
        run.finish(counts[0], counts[1], counts[2], counts[3]);
        return new SyncResult(counts[0], counts[1], counts[2], counts[3]);
    }

    /**
     * Fetches and validates one player on a fetch worker. Builds plain values only: no database
     * access and no shared mutable state.
     *
     * @param onlyTeamCodes when non-null, a player whose current team is not in this set is filtered
     */
    private FetchedPlayer fetchPlayer(PlayerStandingDto playerStanding, String seasonId, Set<String> onlyTeamCodes)
            throws PlayerStatisticsException {
        Long playerId = playerStanding.getId();

        // Profile first: it filters out players whose game logs would be wasted requests. A
        // game-time poll may reuse a recent profile; a full sync always refreshes it.
        PlayerInfoDto playerInfo = onlyTeamCodes == null
                ? playerInfoCache.put(playerId, nhlApiService.getPlayerInfo(playerId))
                : playerInfoCache.get(playerId, () -> nhlApiService.getPlayerInfo(playerId));

        if (!playerInfo.isActive()) {
            return FetchedPlayer.filtered("inactive");
        }

        // Guards against a stored roster gone stale through a trade since the last full sync.
        if (onlyTeamCodes != null && !onlyTeamCodes.contains(playerInfo.getCurrentTeamAbbrev())) {
            return FetchedPlayer.filtered("not on a playing team");
        }

        if (!Objects.equals(playerInfo.getPlayerId(), playerId)) {
            playerInfoCache.invalidate(playerId);
            throw new PlayerStatisticsException("Player ID mismatch between standings and player info API");
        }

        var gameLogs = nhlApiService.getPlayerGameLogs(playerId, seasonId, REGULAR_SEASON_GAME_TYPE);
        GameLogWriter.validatePlayerGameLogs(gameLogs);
        // Recalculates statistics and checks them against the standings totals.
        Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, seasonId);
        return new FetchedPlayer(player, gameLogs, null);
    }

    /**
     * Records a player-level failure. Totals that disagree between the standings and the game log
     * (upstream updating between the two requests during a live game) defer the player to the next
     * sync: validation is never relaxed and the previous valid row stays in place.
     */
    private static void skipPlayer(Long playerId, String seasonId, Exception failure, int[] counts) {
        IngestionFailures.rethrowIfFatal(failure);
        if (failure instanceof PlayerStatisticsException) {
            log.warn("Deferring player {} in season {} to the next sync: {}", playerId, seasonId, failure.getMessage());
            counts[3]++;
        } else {
            log.warn("Skipping player {} in season {}: {}", playerId, seasonId, failure.getMessage());
        }
        counts[2]++;
    }

    /**
     * Synchronizes team standings from the NHL API for the resolved season.
     * Fetches standings (which returns teams in official rank order) and persists them.
     */
    public void syncTeams() {
        checkInterrupted();
        String seasonId = season.getId();
        log.info("Starting team sync for season {}", seasonId);

        var standings = nhlApiService.getTeamStandings();
        int processedCount = seasonDataWriter.persistStandings(seasonId, null, standings);
        Set<String> written = writeTeamGames(seasonId, standings.stream()
                .map(standing -> standing.getTeamAbbrev().getDefaultValue())
                .toList());

        log.info("Team sync completed: {} teams processed, {} of {} team schedules written",
                processedCount, written.size(), standings.size());
    }

    /**
     * Writes completed games for the given teams. Used during game time for teams whose game has
     * just ended, so the game-log graphs and player {@code gameWon} pick up the result without
     * waiting for the post-game full sync.
     *
     * @return teams whose games were written; the rest failed and are logged
     */
    public Set<String> syncTeamGamesForCodes(Set<String> teamCodes) {
        checkInterrupted();
        if (teamCodes.isEmpty()) {
            return Set.of();
        }
        return writeTeamGames(season.getId(), teamCodes.stream().sorted().toList());
    }

    /**
     * Fetches team schedules in parallel and writes each team's completed games in its own short
     * transaction. Returns only once every team has been written or skipped: this is the barrier
     * that lets player game logs resolve {@code gameWon}. A missing team stays missing (its
     * players' {@code gameWon} stays unknown), never filled with a fabricated result.
     */
    private Set<String> writeTeamGames(String seasonId, List<String> teamCodes) {
        Set<String> written = new HashSet<>();
        IngestionMetrics.Run run = metrics.startRun("team-schedules");
        fetchPipeline.<String, List<GameDto>>run(teamCodes,
                teamCode -> nhlApiService.getTeamSchedule(teamCode, seasonId),
                (teamCode, schedule) -> {
                    try {
                        if (!schedule.succeeded()) {
                            throw schedule.failure();
                        }
                        long persistStarted = System.nanoTime();
                        gameLogWriter.writeTeamGames(teamCode, seasonId, schedule.value());
                        run.recordPersist(persistStarted);
                        written.add(teamCode);
                    } catch (Exception e) {
                        IngestionFailures.rethrowIfFatal(e);
                        log.warn("Could not write team games for {} in season {}: {}", teamCode, seasonId, e.getMessage());
                    }
                });
        run.finish(written.size(), 0, teamCodes.size() - written.size(), 0);
        return written;
    }

    /**
     * Synchronizes only the specified teams from standings.
     * Used during game-time sync to limit updates to active game participants.
     *
     * @return number of teams written
     */
    public int syncTeamsForCodes(Set<String> teamCodes) {
        checkInterrupted();
        if (teamCodes.isEmpty()) {
            return 0;
        }
        String seasonId = season.getId();
        log.info("Starting scoped team sync for teams {} in season {}", teamCodes, seasonId);

        int processedCount = seasonDataWriter.persistStandings(seasonId, teamCodes, nhlApiService.getTeamStandings());

        log.info("Scoped team sync completed: {} teams processed", processedCount);
        return processedCount;
    }

    /**
     * Records the given pre-game or in-progress games as their teams' next game, keeping its state
     * current between schedule writes.
     */
    public void syncActiveNextGames(List<GameDto> activeGames) {
        checkInterrupted();
        gameLogWriter.writeActiveNextGames(season.getId(), activeGames);
    }

    /**
     * Returns the games currently in pre-game or in progress.
     */
    public List<GameDto> getActiveGames() {
        return nhlApiService.getLeagueSchedule().stream()
                .filter(game -> game.getGameState() == GameState.LIVE || game.getGameState() == GameState.CRIT || game.getGameState() == GameState.PRE)
                .toList();
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Synchronization was stopped");
        }
    }
}
