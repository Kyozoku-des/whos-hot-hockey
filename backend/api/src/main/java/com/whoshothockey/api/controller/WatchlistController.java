package com.whoshothockey.api.controller;

import com.whoshothockey.api.dto.WatchlistDto;
import com.whoshothockey.api.service.WatchlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for the watchlist of players and teams whose recent form stands out.
 */
@RestController
@RequestMapping("/api/watchlist")
@Tag(name = "Watchlist", description = "Players and teams whose recent form stands out")
@RequiredArgsConstructor
public class WatchlistController {

    private final WatchlistService watchlistService;

    @GetMapping
    @Operation(summary = "Get the watchlist",
               description = "Returns players and teams whose form over their last ten games differs most "
                       + "from last season, from earlier this season or from the league, once each, "
                       + "highest score first")
    public ResponseEntity<WatchlistDto> getWatchlist(@RequestParam(required = false) String season) {
        return ResponseEntity.ok(watchlistService.getWatchlist(season));
    }
}
