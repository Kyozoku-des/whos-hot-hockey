package com.nhl.whoshotbackend.controller;

import com.nhl.whoshotbackend.entity.Player;
import com.nhl.whoshotbackend.entity.Team;
import com.nhl.whoshotbackend.service.StatisticsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for team-related endpoints.
 */
@RestController
@RequestMapping("/api/teams")
@Tag(name = "Teams", description = "Team statistics and information")
@Slf4j
@CrossOrigin(origins = "*")
public class TeamController {

    private final StatisticsService statisticsService;

    public TeamController(StatisticsService statisticsService) {
        this.statisticsService = statisticsService;
    }

    /**
     * Get team standings.
     */
    @GetMapping("/standings")
    @Operation(summary = "Get team standings", description = "Returns all teams ordered by points")
    public ResponseEntity<List<Team>> getStandings() {
        log.info("GET /api/teams/standings");
        List<Team> standings = statisticsService.getStandings();
        return ResponseEntity.ok(standings);
    }

    /**
     * Get teams with current win streaks.
     */
    @GetMapping("/win-streaks")
    @Operation(summary = "Get team win streaks", description = "Returns teams with active win streaks")
    public ResponseEntity<List<Team>> getWinStreaks() {
        log.info("GET /api/teams/win-streaks");
        List<Team> teams = statisticsService.getTeamWinStreaks();
        return ResponseEntity.ok(teams);
    }

    /**
     * Get teams with current loss streaks.
     */
    @GetMapping("/loss-streaks")
    @Operation(summary = "Get team loss streaks", description = "Returns teams with active loss streaks")
    public ResponseEntity<List<Team>> getLossStreaks() {
        log.info("GET /api/teams/loss-streaks");
        List<Team> teams = statisticsService.getTeamLossStreaks();
        return ResponseEntity.ok(teams);
    }

    /**
     * Get specific team by code.
     */
    @GetMapping("/{teamCode}")
    @Operation(summary = "Get team details", description = "Returns details for a specific team")
    public ResponseEntity<Team> getTeam(@PathVariable String teamCode) {
        log.info("GET /api/teams/{}", teamCode);
        return statisticsService.getTeam(teamCode)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get players on a team.
     */
    @GetMapping("/{teamCode}/players")
    @Operation(summary = "Get team roster", description = "Returns all players on a specific team")
    public ResponseEntity<List<Player>> getTeamPlayers(@PathVariable String teamCode) {
        log.info("GET /api/teams/{}/players", teamCode);
        List<Player> players = statisticsService.getTeamPlayers(teamCode);
        return ResponseEntity.ok(players);
    }
}
