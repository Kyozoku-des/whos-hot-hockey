package com.nhl.whoshotbackend.controller;

import com.nhl.whoshotbackend.entity.Player;
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

    public PlayerController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * Get player point standings.
     */
    @GetMapping("/standings")
    @Operation(summary = "Get player standings", description = "Returns all players ordered by points")
    public ResponseEntity<List<Player>> getStandings() {
        log.info("GET /api/players/standings");
        List<Player> standings = statisticsService.getPlayerStandings();
        return ResponseEntity.ok(standings);
    }

    /**
     * Get players with active point streaks.
     */
    @GetMapping("/point-streaks")
    @Operation(summary = "Get point streaks", description = "Returns players with active point streaks")
    public ResponseEntity<List<Player>> getPointStreaks() {
        log.info("GET /api/players/point-streaks");
        List<Player> players = statisticsService.getPlayerPointStreaks();
        return ResponseEntity.ok(players);
    }

    /**
     * Get hot players based on recent performance.
     */
    @GetMapping("/hot")
    @Operation(summary = "Get hot players", description = "Returns players who are 'hot' based on recent performance")
    public ResponseEntity<List<Player>> getHotPlayers() {
        log.info("GET /api/players/hot");
        List<Player> players = statisticsService.getHotPlayers();
        return ResponseEntity.ok(players);
    }

    /**
     * Get specific player by ID.
     */
    @GetMapping("/{playerId}")
    @Operation(summary = "Get player details", description = "Returns details for a specific player")
    public ResponseEntity<Player> getPlayer(@PathVariable Long playerId) {
        log.info("GET /api/players/{}", playerId);
        return statisticsService.getPlayer(playerId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
