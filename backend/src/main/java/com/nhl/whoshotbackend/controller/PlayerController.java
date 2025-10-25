package com.nhl.whoshotbackend.controller;

import com.nhl.whoshotbackend.dto.GameLogDTO;
import com.nhl.whoshotbackend.entity.Player;
import com.nhl.whoshotbackend.service.NhlApiService;
import com.nhl.whoshotbackend.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for player-related endpoints.
 */
@RestController
@RequestMapping("/api/players")
@Tag(name = "Players", description = "Player statistics and information")
@Slf4j
@CrossOrigin(origins = "*")
public class PlayerController {

    private final StatisticsService statisticsService;
    private final NhlApiService nhlApiService;

    public PlayerController(StatisticsService statisticsService, NhlApiService nhlApiService) {
        this.statisticsService = statisticsService;
        this.nhlApiService = nhlApiService;
    }

    /**
     * Get player point standings for a season.
     */
    @GetMapping("/standings")
    @Operation(summary = "Get player standings", description = "Returns all players ordered by points for a given season")
    public ResponseEntity<List<Player>> getStandings(
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/players/standings?season={}", actualSeason);
        List<Player> standings = statisticsService.getPlayerStandings(actualSeason);
        return ResponseEntity.ok(standings);
    }

    /**
     * Get players with active point streaks for a season.
     */
    @GetMapping("/point-streaks")
    @Operation(summary = "Get point streaks", description = "Returns players with active point streaks for a given season")
    public ResponseEntity<List<Player>> getPointStreaks(
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/players/point-streaks?season={}", actualSeason);
        List<Player> players = statisticsService.getPlayerPointStreaks(actualSeason);
        return ResponseEntity.ok(players);
    }

    /**
     * Get hot players based on recent performance for a season.
     */
    @GetMapping("/hot")
    @Operation(summary = "Get hot players", description = "Returns players who are 'hot' based on recent performance for a given season")
    public ResponseEntity<List<Player>> getHotPlayers(
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/players/hot?season={}", actualSeason);
        List<Player> players = statisticsService.getHotPlayers(actualSeason);
        return ResponseEntity.ok(players);
    }

    /**
     * Get specific player by ID and season.
     */
    @GetMapping("/{playerId}")
    @Operation(summary = "Get player details", description = "Returns details for a specific player in a given season")
    public ResponseEntity<Player> getPlayer(
            @PathVariable Long playerId,
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/players/{}?season={}", playerId, actualSeason);
        return statisticsService.getPlayer(playerId, actualSeason)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get game log for a specific player and season.
     */
    @GetMapping("/{playerId}/game-log")
    @Operation(summary = "Get player game log", description = "Returns game-by-game statistics for a specific player in a given season")
    public ResponseEntity<List<GameLogDTO>> getPlayerGameLog(
            @PathVariable Long playerId,
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/players/{}/game-log?season={}", playerId, actualSeason);
        List<GameLogDTO> gameLog = statisticsService.getPlayerGameLog(playerId, actualSeason);
        return ResponseEntity.ok(gameLog);
    }
}
