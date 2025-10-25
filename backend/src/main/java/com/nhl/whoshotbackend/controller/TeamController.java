package com.nhl.whoshotbackend.controller;

import com.nhl.whoshotbackend.dto.TeamGameLogDTO;
import com.nhl.whoshotbackend.entity.Player;
import com.nhl.whoshotbackend.entity.Team;
import com.nhl.whoshotbackend.service.NhlApiService;
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
    private final NhlApiService nhlApiService;

    public TeamController(StatisticsService statisticsService, NhlApiService nhlApiService) {
        this.statisticsService = statisticsService;
        this.nhlApiService = nhlApiService;
    }

    /**
     * Get team standings for a season.
     */
    @GetMapping("/standings")
    @Operation(summary = "Get team standings", description = "Returns all teams ordered by points for a given season")
    public ResponseEntity<List<Team>> getStandings(
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/teams/standings?season={}", actualSeason);
        List<Team> standings = statisticsService.getStandings(actualSeason);
        return ResponseEntity.ok(standings);
    }

    /**
     * Get teams with current win streaks for a season.
     */
    @GetMapping("/win-streaks")
    @Operation(summary = "Get team win streaks", description = "Returns teams with active win streaks for a given season")
    public ResponseEntity<List<Team>> getWinStreaks(
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/teams/win-streaks?season={}", actualSeason);
        List<Team> teams = statisticsService.getTeamWinStreaks(actualSeason);
        return ResponseEntity.ok(teams);
    }

    /**
     * Get teams with current loss streaks for a season.
     */
    @GetMapping("/loss-streaks")
    @Operation(summary = "Get team loss streaks", description = "Returns teams with active loss streaks for a given season")
    public ResponseEntity<List<Team>> getLossStreaks(
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/teams/loss-streaks?season={}", actualSeason);
        List<Team> teams = statisticsService.getTeamLossStreaks(actualSeason);
        return ResponseEntity.ok(teams);
    }

    /**
     * Get specific team by code and season.
     */
    @GetMapping("/{teamCode}")
    @Operation(summary = "Get team details", description = "Returns details for a specific team in a given season")
    public ResponseEntity<Team> getTeam(
            @PathVariable String teamCode,
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/teams/{}?season={}", teamCode, actualSeason);
        return statisticsService.getTeam(teamCode, actualSeason)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Get players on a team for a season.
     */
    @GetMapping("/{teamCode}/players")
    @Operation(summary = "Get team roster", description = "Returns all players on a specific team for a given season")
    public ResponseEntity<List<Player>> getTeamPlayers(
            @PathVariable String teamCode,
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/teams/{}/players?season={}", teamCode, actualSeason);
        List<Player> players = statisticsService.getTeamPlayers(teamCode, actualSeason);
        return ResponseEntity.ok(players);
    }

    /**
     * Get game log for a specific team and season.
     */
    @GetMapping("/{teamCode}/game-log")
    @Operation(summary = "Get team game log", description = "Returns game-by-game results for a specific team in a given season")
    public ResponseEntity<List<TeamGameLogDTO>> getTeamGameLog(
            @PathVariable String teamCode,
            @RequestParam(required = false) String season) {
        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/teams/{}/game-log?season={}", teamCode, actualSeason);
        List<TeamGameLogDTO> gameLog = statisticsService.getTeamGameLog(teamCode, actualSeason);
        return ResponseEntity.ok(gameLog);
    }
}
