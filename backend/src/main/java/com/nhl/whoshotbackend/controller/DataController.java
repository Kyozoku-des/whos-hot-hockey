package com.nhl.whoshotbackend.controller;

import com.nhl.whoshotbackend.service.DataIntegrationService;
import com.nhl.whoshotbackend.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

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

    public DataController(
            DataIntegrationService dataIntegrationService,
            StatisticsService statisticsService) {
        this.dataIntegrationService = dataIntegrationService;
        this.statisticsService = statisticsService;
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
