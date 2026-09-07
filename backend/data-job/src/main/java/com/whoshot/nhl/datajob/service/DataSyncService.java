package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Comparator;
import java.util.Objects;
import java.util.concurrent.CancellationException;

/**
 * Coordinates retrieval, transformation, and persistence of player season data.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataSyncService {

    private final NhlApiService nhlApiService;
    private final PlayerRepository playerRepository;
    private final PlayerFactory playerFactory;
    private final TeamRepository teamRepository;
    private final GameLogRepository gameLogRepository;
    private SeasonDto season;
    @Getter
    private LocalDateTime firstGameTimeForToday;
    @Getter
    private LocalDateTime lastGameTimeForToday;
    private final int gameType = 2; // Regular season

    /**
     * Initializes the service by resolving the active season once at startup.
     */
    public void initialize() {
        checkInterrupted();
        setSeason();
        checkInterrupted();
        setFirstAndLastGameTimesForToday();
    }

    /**
     * Resolves and stores the best season to synchronize based on current date
     * boundaries.
     * Retains the most recently started season during playoffs and the offseason.
     */
    private void setSeason() {
        this.season = selectSeason(nhlApiService.getSeasons(), LocalDateTime.now(ZoneOffset.UTC));
        log.info("Season selected for synchronization: {}", season.getId());
    }

    static SeasonDto selectSeason(List<SeasonDto> seasons, LocalDateTime now) {
        return seasons.stream()
                .filter(Objects::nonNull)
                .filter(candidate -> candidate.getStartDate() != null)
                .filter(candidate -> !candidate.getStartDate().isAfter(now))
                .max(Comparator.comparing((@NonNull SeasonDto candidate) -> candidate.getStartDate()))
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
    @Transactional
    public void syncPlayers() throws PlayerStatisticsException {
        checkInterrupted();
        String seasonId = season.getId();
        var players = nhlApiService.getPlayerStandingsOrder(seasonId, gameType);

        // Get order from standings API, calculate new statistics and verify points
        for (PlayerStandingDto playerStanding : players) {
            checkInterrupted();
            Long playerId = playerStanding.getId();

            PlayerInfoDto playerInfo = nhlApiService.getPlayerInfo(playerId);
            checkInterrupted();

            if (!playerInfo.isActive()) {
                log.warn("Skipping inactive player ID: {}. Name: {} {}", playerId, playerInfo.getFirstName(),
                        playerInfo.getLastName());
                continue;
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

            playerRepository.save(player);
            playerRepository.flush();
        }
        checkInterrupted();
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Player synchronization was stopped");
        }
    }

    /**
     * Checks if any game from today's schedule is currently in progress.
     *
     * @return true if at least one game has gameState "LIVE"
     */
    public boolean isAnyGameActive() {
        List<GameDto> games = nhlApiService.getLeagueSchedule();

        return games.stream()
                .anyMatch(game -> game.getGameState() == GameState.LIVE);
    }
}
