package com.whoshot.controller;

import com.whoshot.dto.SearchResultDto;
import com.whoshot.repository.PlayerRepository;
import com.whoshot.repository.TeamRepository;
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

    /**
     * Creates the controller with repository dependencies.
     *
     * @param playerRepository repository for player-backed search entries
     * @param teamRepository repository for team-backed search entries
     */
    public SearchController(
            PlayerRepository playerRepository,
            TeamRepository teamRepository
            ) {
        this.playerRepository = playerRepository;
        this.teamRepository = teamRepository;
    }

    /**
     * Get all searchable items (players and teams) for a season.
     * Used for client-side search/autocomplete functionality.
     *
     * @param season optional season identifier used to scope results
     * @return HTTP 200 response containing merged player and team search results
     */
    @GetMapping("/all")
    @Operation(summary = "Get all searchable items",
               description = "Returns all players and teams for autocomplete search functionality")
    public ResponseEntity<List<SearchResultDto>> getAllSearchableItems(
            @RequestParam(required = false) String season) {

        String actualSeason = season != null ? season : null;
        log.info("GET /api/search/all?season={}", actualSeason);

        // Combine players and teams into a single list
        List<SearchResultDto> results = new ArrayList<>();

        // Add all teams
        List<SearchResultDto> teams = teamRepository.findAllForSearch(actualSeason);
        results.addAll(teams);

        // Add all players
        List<SearchResultDto> players = playerRepository.findAllForSearch(actualSeason);
        results.addAll(players);

        log.info("Returning {} searchable items ({} teams, {} players)",
                results.size(), teams.size(), players.size());

        return ResponseEntity.ok(results);
    }
}
