package com.whoshot.service;

import com.whoshot.dto.SeasonDto;
import com.whoshot.dto.nhlapi.PlayerInfoDto;
import com.whoshot.dto.nhlapi.PlayerStandingDto;
import com.whoshot.entity.Player;
import com.whoshot.exception.PlayerStatisticsException;
import com.whoshot.factory.PlayerFactory;
import com.whoshot.repository.GameLogRepository;
import com.whoshot.repository.PlayerRepository;
import com.whoshot.repository.TeamRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

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
    private final int gameType = 2; // Regular season

    /**
     * Initializes the service by resolving the active season once at startup.
     */
    @PostConstruct
    private void init() {
        setSeason();
    }

    /**
     * Resolves and stores the best season to synchronize based on current date boundaries.
     * Falls back to the latest available season when the current date does not fall within any regular season range.
     */
    private void setSeason() {
        List<SeasonDto> seasons = nhlApiService.getSeasons();

        for (SeasonDto season : seasons) {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime startDate = season.getStartDate();
            LocalDateTime endDate = season.getRegularSeasonEndDate();

            String seasonId = season.getId();
            if (now.isAfter(startDate) && now.isBefore(endDate)) {
                log.info("Current season determined: {}", seasonId);
                this.season = season;
                return;
            }
        }

        log.info("No current season found, using latest season: {}", seasons.getLast().getId());
        this.season = seasons.getLast();
    }

    /**
     * Synchronizes active-player statistics for the resolved season.
     * <p>
     * Side effects: performs external API calls and writes player records to the database.
     *
     * @throws PlayerStatisticsException if player identity or point-total validation fails
     */
    @Transactional
    public void syncPlayer() throws PlayerStatisticsException {
        String seasonId = season.getId();
        var players = nhlApiService.getPlayerStandingsOrder(seasonId, gameType);

        // Get order from standings API, calculate new statistics and verify points
        for (PlayerStandingDto playerStanding : players) {
            Long playerId = playerStanding.getId();

            PlayerInfoDto playerInfo = nhlApiService.getPlayerInfo(playerId);

            if (!playerInfo.isActive()) {
                log.info("Skipping inactive player ID: {}", playerId);
                continue;
            }

            if (!Objects.equals(playerInfo.getPlayerId(), playerId)) {
                throw new PlayerStatisticsException("Player ID mismatch between standings and player info API");
            }

            // Fetch game logs for the season
            var gameLogs = nhlApiService.getPlayerGameLogs(playerId, seasonId, gameType);

            // Create player using factory (handles all construction and statistics calculation)
            Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, seasonId);

            playerRepository.save(player);
            playerRepository.flush();
        }
    }
}
