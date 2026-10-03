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
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.Objects;
import java.util.Set;
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

    private final NhlApiService nhlApiService;
    private final PlayerRepository playerRepository;
    private final PlayerFactory playerFactory;
    private final SeasonDataWriter seasonDataWriter;
    private final TeamGameRepository teamGameRepository;
    private final GameLogWriter gameLogWriter;
    private SeasonDto season;
    @Getter
    private LocalDateTime firstGameTimeForToday;
    @Getter
    private LocalDateTime lastGameTimeForToday;
    private final int gameType = 2; // Regular season

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
     * Synchronizes active-player statistics for the resolved season.
     * Side effects: performs external API calls and writes player records to the
     * database.
     *
     * @throws PlayerStatisticsException if player identity or point-total
     *                                   validation fails
     */
    public void syncPlayers() throws PlayerStatisticsException {
        checkInterrupted();
        String seasonId = season.getId();
        log.info("Starting player sync for season {}", seasonId);

        var players = nhlApiService.getPlayerStandingsOrder(seasonId, gameType);
        int processedCount = 0;
        // Game logs already written for the season, used to resolve gameWon for each player's
        // game log (research R-005). Fetched once, up front, rather than per player.
        TeamGameIndex teamGames = TeamGameIndex.of(teamGameRepository.findBySeasonId(seasonId));

        // Get order from standings API, calculate new statistics and verify points
        for (PlayerStandingDto playerStanding : players) {
            if (syncPlayer(playerStanding, seasonId, null, teamGames)) {
                processedCount++;
            }
        }

        log.info("Player sync completed: {} players processed", processedCount);
    }

    /**
     * Fetches, recalculates and upserts one player, plus their per-game logs.
     *
     * @param onlyTeamCodes when non-null, a player whose current team is not in this set is skipped
     * @return whether the player was written
     */
    private boolean syncPlayer(PlayerStandingDto playerStanding, String seasonId, Set<String> onlyTeamCodes,
                               TeamGameIndex teamGames) throws PlayerStatisticsException {
        checkInterrupted();
        Long playerId = playerStanding.getId();

        PlayerInfoDto playerInfo = nhlApiService.getPlayerInfo(playerId);
        checkInterrupted();

        if (!playerInfo.isActive()) {
            log.warn("Skipping inactive player ID: {}. Name: {} {}", playerId, playerInfo.getFirstName(),
                    playerInfo.getLastName());
            return false;
        }

        // Guards against a stored roster gone stale through a trade since the last full sync.
        if (onlyTeamCodes != null && !onlyTeamCodes.contains(playerInfo.getCurrentTeamAbbrev())) {
            return false;
        }

        if (!Objects.equals(playerInfo.getPlayerId(), playerId)) {
            throw new PlayerStatisticsException("Player ID mismatch between standings and player info API");
        }

        // Fetch game logs for the season
        var gameLogs = nhlApiService.getPlayerGameLogs(playerId, seasonId, gameType);
        checkInterrupted();

        // Create player using factory (handles all construction and statistics
        // calculation)
        Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, seasonId);

        // One transaction for the player and their game logs (research R-003: nothing else writes
        // the current-season game_logs line of the graphs), so neither is ever committed alone.
        seasonDataWriter.writePlayer(player, gameLogs, teamGames);
        return true;
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
        writeTeamGamesForStandings(seasonId, standings);

        log.info("Team sync completed: {} teams processed", processedCount);
    }

    /**
     * Populates the game-log graph's current-season line for each team (research R-003), the
     * team-side counterpart of the write in {@link #syncPlayers()}.
     */
    private void writeTeamGamesForStandings(String seasonId, List<TeamStandingsDto> standings) {
        writeTeamGames(seasonId, standings.stream()
                .map(standing -> standing.getTeamAbbrev().getDefaultValue())
                .toList());
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
        return writeTeamGames(season.getId(), teamCodes);
    }

    private Set<String> writeTeamGames(String seasonId, Collection<String> teamCodes) {
        Set<String> written = new HashSet<>();
        for (String teamCode : teamCodes) {
            checkInterrupted();
            try {
                var schedule = nhlApiService.getTeamSchedule(teamCode, seasonId);
                gameLogWriter.writeTeamGames(teamCode, seasonId, schedule);
                written.add(teamCode);
            } catch (CancellationException e) {
                throw e;
            } catch (Exception e) {
                log.warn("Could not write team games for {} in season {}: {}", teamCode, seasonId, e.getMessage());
            }
        }
        return written;
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
    public int syncPlayersForTeams(Set<String> teamCodes) throws PlayerStatisticsException {
        checkInterrupted();
        String seasonId = season.getId();
        log.info("Starting scoped player sync for teams {} in season {}", teamCodes, seasonId);

        Set<Long> rosterIds = teamCodes.stream()
                .flatMap(teamCode -> playerRepository.findByTeamCodeAndIdSeason(teamCode, seasonId).stream())
                .map(player -> player.getId().playerId())
                .collect(Collectors.toSet());
        Set<Long> storedIds = playerRepository.findPlayerIdsBySeason(seasonId);
        var players = nhlApiService.getPlayerStandingsOrder(seasonId, gameType).stream()
                .filter(standing -> rosterIds.contains(standing.getId()) || !storedIds.contains(standing.getId()))
                .toList();
        TeamGameIndex teamGames = TeamGameIndex.of(teamGameRepository.findBySeasonId(seasonId));
        int processedCount = 0;

        for (PlayerStandingDto playerStanding : players) {
            if (syncPlayer(playerStanding, seasonId, teamCodes, teamGames)) {
                processedCount++;
            }
        }

        log.info("Scoped player sync completed: {} players processed for teams {}", processedCount, teamCodes);
        return processedCount;
    }

    /**
     * Synchronizes only the specified teams from standings.
     * Used during game-time sync to limit updates to active game participants.
     *
     * @return number of teams written
     */
    public int syncTeamsForCodes(Set<String> teamCodes) {
        checkInterrupted();
        String seasonId = season.getId();
        log.info("Starting scoped team sync for teams {} in season {}", teamCodes, seasonId);

        int processedCount = seasonDataWriter.persistStandings(seasonId, teamCodes, nhlApiService.getTeamStandings());

        log.info("Scoped team sync completed: {} teams processed", processedCount);
        return processedCount;
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

    /** Checks for live, critical, or pre-game entries in the current schedule. */
    public boolean isAnyGameActive() {
        List<GameDto> games = nhlApiService.getLeagueSchedule();

        return games.stream()
                .anyMatch(game -> (game.getGameState() == GameState.LIVE || game.getGameState() == GameState.CRIT || game.getGameState() == GameState.PRE));
    }
}
