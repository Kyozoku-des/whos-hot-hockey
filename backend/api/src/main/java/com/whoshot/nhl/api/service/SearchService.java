package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.dto.SearchIndexDto;
import com.whoshot.nhl.api.dto.SearchResultDto;
import com.whoshot.nhl.domain.entity.SearchResult;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Stream;

/**
 * Service building the autocomplete search index for the API layer.
 */
@Service
@RequiredArgsConstructor
public class SearchService {

    private final PlayerRepository playerRepository;
    private final TeamRepository teamRepository;
    private final SeasonResolver seasonResolver;

    /**
     * Get every team followed by every player for a season.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return the search index, tagged with the season it belongs to
     */
    public SearchIndexDto getSearchIndex(String season) {
        String resolved = seasonResolver.resolve(season);
        List<SearchResultDto> results = Stream.concat(
                        teamRepository.findAllForSearch(resolved).stream(),
                        playerRepository.findAllForSearch(resolved).stream())
                .map(SearchService::toDto)
                .toList();
        return new SearchIndexDto(resolved, results.size(), results);
    }

    private static SearchResultDto toDto(SearchResult result) {
        return new SearchResultDto(
                result.type(),
                result.id(),
                result.name(),
                result.secondaryInfo(),
                result.teamCode(),
                result.imageUrl()
        );
    }
}
