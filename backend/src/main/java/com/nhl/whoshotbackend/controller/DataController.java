package com.nhl.whoshotbackend.controller;

import com.nhl.whoshotbackend.entity.CurrentSeason;
import com.nhl.whoshotbackend.repository.CurrentSeasonRepository;
import com.nhl.whoshotbackend.service.DataIntegrationService;
import com.nhl.whoshotbackend.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

/**
 * REST controller for data synchronization endpoints.
 */
@RestController
@RequestMapping("/api/data")
@Tag(name = "Data", description = "Data synchronization endpoints")
@Slf4j
@CrossOrigin(origins = "*")
public class DataController {

    private final DataIntegrationService dataIntegrationService;
    private final StatisticsService statisticsService;
    private final CurrentSeasonRepository currentSeasonRepository;

    public DataController(
            DataIntegrationService dataIntegrationService,
            StatisticsService statisticsService,
            CurrentSeasonRepository currentSeasonRepository) {
        this.dataIntegrationService = dataIntegrationService;
        this.statisticsService = statisticsService;
        this.currentSeasonRepository = currentSeasonRepository;
    }

    /**
     * Get current active NHL season.
     */
    @GetMapping("/current-season")
    @Operation(summary = "Get current season", description = "Get the currently active NHL season")
    public ResponseEntity<CurrentSeason> getCurrentSeason() {
        log.info("GET /api/data/current-season");
        Optional<CurrentSeason> currentSeason = currentSeasonRepository.findByIsActiveTrue();

        if (currentSeason.isPresent()) {
            return ResponseEntity.ok(currentSeason.get());
        } else {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Manually trigger data synchronization.
     */
    @PostMapping("/sync")
    @Operation(summary = "Sync data", description = "Manually trigger data synchronization from NHL API")
    public ResponseEntity<Map<String, String>> syncData() {
        log.info("POST /api/data/sync - Manual sync triggered");
        try {
            dataIntegrationService.syncAllData();
            statisticsService.calculateHotRatings();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Data synchronization completed successfully"
            ));
        } catch (Exception e) {
            log.error("Error during manual sync", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Data synchronization failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync standings only.
     */
    @PostMapping("/sync/standings")
    @Operation(summary = "Sync standings", description = "Sync only team standings data")
    public ResponseEntity<Map<String, String>> syncStandings() {
        log.info("POST /api/data/sync/standings");
        try {
            dataIntegrationService.syncStandings();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Standings synchronized successfully"
            ));
        } catch (Exception e) {
            log.error("Error syncing standings", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Standings sync failed: " + e.getMessage()
            ));
        }
    }

    /**
     * Sync player stats only.
     */
    @PostMapping("/sync/players")
    @Operation(summary = "Sync player stats", description = "Sync only player statistics data")
    public ResponseEntity<Map<String, String>> syncPlayers() {
        log.info("POST /api/data/sync/players");
        try {
            dataIntegrationService.syncPlayerStats();
            statisticsService.calculateHotRatings();
            return ResponseEntity.ok(Map.of(
                    "status", "success",
                    "message", "Player stats synchronized successfully"
            ));
        } catch (Exception e) {
            log.error("Error syncing player stats", e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Player stats sync failed: " + e.getMessage()
            ));
        }
    }
}
