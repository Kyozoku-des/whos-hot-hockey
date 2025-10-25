package com.nhl.whoshotbackend.controller;

import com.nhl.whoshotbackend.dto.SearchResultDTO;
import com.nhl.whoshotbackend.repository.PlayerRepository;
import com.nhl.whoshotbackend.repository.TeamRepository;
import com.nhl.whoshotbackend.service.NhlApiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * REST controller for search functionality.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Search for players and teams")
@Slf4j
@CrossOrigin(origins = "*")
public class SearchController {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final NhlApiService nhlApiService;

    public SearchController(
            PlayerRepository playerRepository,
            TeamRepository teamRepository,
            NhlApiService nhlApiService) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
        this.nhlApiService = nhlApiService;
    }

    /**
     * Get all searchable items (players and teams) for a season.
     * Used for client-side search/autocomplete functionality.
     */
    @GetMapping("/all")
    @Operation(summary = "Get all searchable items",
               description = "Returns all players and teams for autocomplete search functionality")
    public ResponseEntity<List<SearchResultDTO>> getAllSearchableItems(
            @RequestParam(required = false) String season) {

        String actualSeason = season != null ? season : nhlApiService.getCurrentSeason();
        log.info("GET /api/search/all?season={}", actualSeason);

        // Combine players and teams into a single list
        List<SearchResultDTO> results = new ArrayList<>();

        // Add all teams
        List<SearchResultDTO> teams = teamRepository.findAllForSearch(actualSeason);
        results.addAll(teams);

        // Add all players
        List<SearchResultDTO> players = playerRepository.findAllForSearch(actualSeason);
        results.addAll(players);

        log.info("Returning {} searchable items ({} teams, {} players)",
                results.size(), teams.size(), players.size());

        return ResponseEntity.ok(results);
    }
}
