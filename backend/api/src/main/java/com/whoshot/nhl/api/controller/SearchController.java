package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.dto.SearchResultDto;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.SearchResult;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Search for players and teams")
@Slf4j
@RequiredArgsConstructor
public class SearchController {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final CurrentSeasonRepository currentSeasonRepository;

    @GetMapping("/all")
    @Operation(summary = "Get all searchable items",
               description = "Returns all players and teams for autocomplete search functionality")
    public ResponseEntity<List<SearchResultDto>> getAllSearchableItems(
            @RequestParam(required = false) String season) {

        String resolvedSeason = resolveSeason(season);
        log.info("GET /api/search/all?season={} (resolved: {})", season, resolvedSeason);

        List<SearchResult> teams = teamRepository.findAllForSearch(resolvedSeason);
        List<SearchResultDto> results = new ArrayList<>(teams.stream().map(this::toDto).toList());

        List<SearchResult> players = playerRepository.findAllForSearch(resolvedSeason);
        results.addAll(players.stream().map(this::toDto).toList());

        log.info("Returning {} searchable items ({} teams, {} players)",
                results.size(), teams.size(), players.size());

        return ResponseEntity.ok(results);
    }

    private String resolveSeason(String season) {
        if (season != null && !season.isBlank()) {
            return season;
        }
        return currentSeasonRepository.findByIsActiveTrue()
                .map(CurrentSeason::getSeasonId)
                .orElse(null);
    }

    private SearchResultDto toDto(SearchResult sr) {
        return new SearchResultDto(
                sr.getType(), sr.getId(), sr.getName(),
                sr.getSecondaryInfo(), sr.getTeamCode(),
                sr.getImageUrl(), sr.getSeason()
        );
    }
}
