package com.whoshot.nhl.api.service;

import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.SearchResult;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SearchServiceTest {

    private final PlayerRepository playerRepository = mock(PlayerRepository.class);
    private final TeamRepository teamRepository = mock(TeamRepository.class);
    private final CurrentSeasonRepository currentSeasonRepository = mock(CurrentSeasonRepository.class);
    private SearchService searchService;

    @BeforeEach
    void setUp() {
        searchService = new SearchService(playerRepository, teamRepository,
                new SeasonResolver(currentSeasonRepository));
    }

    @Test
    void listsTeamsBeforePlayers() {
        String season = "20252026";
        when(teamRepository.findAllForSearch(season)).thenReturn(List.of(
                new SearchResult("TEAM", "TOR", "Toronto Maple Leafs", "TOR", "TOR", "https://logo.png")));
        when(playerRepository.findAllForSearch(season)).thenReturn(List.of(
                new SearchResult("PLAYER", "8478402", "Connor McDavid", "C", "EDM", "https://headshot.jpg")));

        var index = searchService.getSearchIndex(season);

        assertThat(index.season()).isEqualTo(season);
        assertThat(index.count()).isEqualTo(2);
        assertThat(index.results()).extracting("type").containsExactly("TEAM", "PLAYER");
        assertThat(index.results().get(1).name()).isEqualTo("Connor McDavid");
    }

    @Test
    void defaultsToActiveSeason() {
        CurrentSeason current = new CurrentSeason();
        current.setSeasonId("20252026");
        when(currentSeasonRepository.findByIsActiveTrue()).thenReturn(Optional.of(current));
        when(teamRepository.findAllForSearch("20252026")).thenReturn(List.of());
        when(playerRepository.findAllForSearch("20252026")).thenReturn(List.of());

        var index = searchService.getSearchIndex(null);

        assertThat(index.season()).isEqualTo("20252026");
        assertThat(index.results()).isEmpty();
    }
}
