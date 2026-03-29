package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.dto.PlayerDetailDto;
import com.whoshot.nhl.api.dto.PlayerGameLogDto;
import com.whoshot.nhl.api.dto.PlayerStandingsDto;
import com.whoshot.nhl.api.service.PlayerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller for player statistics endpoints.
 */
@RestController
@RequestMapping("/api/players")
@Tag(name = "Players", description = "Player statistics and standings")
@RequiredArgsConstructor
public class PlayerController {

    private final PlayerService playerService;

    @GetMapping("/{playerId}")
    @Operation(summary = "Get player detail",
               description = "Returns detailed information for a specific player")
    public ResponseEntity<PlayerDetailDto> getPlayerDetail(
            @PathVariable long playerId,
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(playerService.getPlayerDetail(playerId, season));
    }

    @GetMapping("/{playerId}/game-log")
    @Operation(summary = "Get player game log",
               description = "Returns the game-by-game log for a specific player in a season")
    public ResponseEntity<List<PlayerGameLogDto>> getPlayerGameLog(
            @PathVariable long playerId,
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(playerService.getPlayerGameLog(playerId, season));
    }

    @GetMapping("/standings")
    @Operation(summary = "Get player standings",
               description = "Returns all players for a season ordered by points descending")
    public ResponseEntity<List<PlayerStandingsDto>> getPlayerStandings(
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(playerService.getPlayerStandings(season));
    }

    @GetMapping("/point-streaks")
    @Operation(summary = "Get players with point streaks",
               description = "Returns players currently on a point streak")
    public ResponseEntity<List<PlayerStandingsDto>> getPlayerPointStreaks(
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(playerService.getPlayerStreaks(season));
    }

    @GetMapping("/hot")
    @Operation(summary = "Get hot players",
               description = "Returns players flagged as hot based on recent performance")
    public ResponseEntity<List<PlayerStandingsDto>> getHotPlayers(
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(playerService.getHotPlayers(season));
    }
}
