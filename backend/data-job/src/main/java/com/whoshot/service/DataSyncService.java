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
    private final int n = 10; // Number of recent games to consider for certain calculations

    @PostConstruct
    private void init() {
        setSeason();
    }

    /**
     * Get seasons from NHL API and determines the current season ID. If no current season is found, returns latest season.
     *
     * @return The current season ID.
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
     * Synchronizes player statistics from the NHL API and recalculates derived statistics.
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
            Player player = playerFactory.createFromApiData(playerInfo, playerStanding, gameLogs, seasonId, n);

            playerRepository.save(player);
            playerRepository.flush();
        }
    }
}
