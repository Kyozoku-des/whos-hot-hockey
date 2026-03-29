package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.dto.TeamDetailDto;
import com.whoshot.nhl.api.dto.TeamGameLogDto;
import com.whoshot.nhl.api.dto.TeamStandingsDto;
import com.whoshot.nhl.api.service.TeamService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for team statistics endpoints.
 */
@RestController
@RequestMapping("/api/teams")
@Tag(name = "Teams", description = "Team statistics and standings")
@RequiredArgsConstructor
public class TeamController {

    private final TeamService teamService;

    @GetMapping("/standings")
    @Operation(summary = "Get team standings",
               description = "Returns all teams for a season ordered by points descending")
    public ResponseEntity<List<TeamStandingsDto>> getTeamStandings(
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(teamService.getTeamStandings(season));
    }

    @GetMapping("/win-streaks")
    @Operation(summary = "Get teams with win streaks",
               description = "Returns teams currently on a win streak")
    public ResponseEntity<List<TeamStandingsDto>> getTeamWinStreaks(
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(teamService.getTeamWinStreaks(season));
    }

    @GetMapping("/loss-streaks")
    @Operation(summary = "Get teams with loss streaks",
               description = "Returns teams currently on a loss streak")
    public ResponseEntity<List<TeamStandingsDto>> getTeamLossStreaks(
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(teamService.getTeamLossStreaks(season));
    }

    @GetMapping("/{teamCode}")
    @Operation(summary = "Get team detail",
               description = "Returns detailed team information including roster")
    public ResponseEntity<TeamDetailDto> getTeamDetail(
            @PathVariable String teamCode,
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(teamService.getTeamDetail(teamCode, season));
    }

    @GetMapping("/{teamCode}/game-log")
    @Operation(summary = "Get team game log",
               description = "Returns the team's game log for a season ordered by date descending")
    public ResponseEntity<List<TeamGameLogDto>> getTeamGameLog(
            @PathVariable String teamCode,
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(teamService.getTeamGameLog(teamCode, season));
    }
}
