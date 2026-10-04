package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.dto.SearchIndexDto;
import com.whoshot.nhl.api.service.SearchService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for the autocomplete search index.
 */
@RestController
@RequestMapping("/api/search")
@Tag(name = "Search", description = "Search for players and teams")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    @GetMapping("/all")
    @Operation(summary = "Get the full search index",
               description = "Returns every player and team for one season as a single index. "
                       + "Clients cache this and filter it locally for autocomplete; the "
                       + "season on the envelope tells them when the cached copy is stale.")
    public ResponseEntity<SearchIndexDto> getAllSearchableItems(
            @RequestParam(required = false) String season) {
        return ResponseEntity.ok(searchService.getSearchIndex(season));
    }
}
