package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.dto.ScoreboardDto;
import com.whoshot.nhl.api.service.ScoreboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for game score endpoints.
 */
@RestController
@RequestMapping("/api/games")
@Tag(name = "Games", description = "Game scores and scoring summaries")
@RequiredArgsConstructor
public class GameController {

    private final ScoreboardService scoreboardService;

    @GetMapping("/latest")
    @Operation(summary = "Get latest game day scores",
               description = "Returns today's games while any is in progress, otherwise the most recent day "
                       + "with finished games, each with its goals in the order scored")
    public ResponseEntity<ScoreboardDto> getLatestScoreboard() {
        return ResponseEntity.ok(scoreboardService.getLatestScoreboard());
    }
}
