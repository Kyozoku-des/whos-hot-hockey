package com.whoshothockey.api.controller;

import com.whoshothockey.api.dto.MissedGameDto;
import com.whoshothockey.api.dto.PlayerDetailDto;
import com.whoshothockey.api.dto.PlayerGameLogDto;
import com.whoshothockey.api.dto.PlayerStandingsDto;
import com.whoshothockey.api.service.PlayerService;
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

    @GetMapping("/{playerId}/missed-games")
    @Operation(summary = "Get player missed games",
               description = "Returns the games the player's current team played without them since "
                       + "their first game for it, oldest first")
    public ResponseEntity<List<MissedGameDto>> getPlayerMissedGames(
            @PathVariable long playerId,
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(playerService.getPlayerMissedGames(playerId, season));
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
