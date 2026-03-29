package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.SearchResult;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SearchController.class)
@ContextConfiguration(classes = {SearchController.class})
class SearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerRepository playerRepository;

    @MockitoBean
    private TeamRepository teamRepository;

    @MockitoBean
    private CurrentSeasonRepository currentSeasonRepository;

    @Test
    void getAllSearchableItems_returnsPlayersAndTeams() throws Exception {
        String season = "20252026";

        when(playerRepository.findAllForSearch(season)).thenReturn(List.of(
                new SearchResult("PLAYER", "8478402", "Connor McDavid", "EDM", "EDM", "https://headshot.jpg", season)
        ));
        when(teamRepository.findAllForSearch(season)).thenReturn(List.of(
                new SearchResult("TEAM", "TOR", "Toronto Maple Leafs", "Eastern", "TOR", "https://logo.png", season)
        ));

        mockMvc.perform(get("/api/search/all").param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].type").value("TEAM"))
                .andExpect(jsonPath("$[1].type").value("PLAYER"));
    }

    @Test
    void getAllSearchableItems_autoDetectsSeasonWhenNull() throws Exception {
        String activeSeason = "20252026";

        CurrentSeason current = new CurrentSeason();
        current.setSeasonId(activeSeason);
        current.setIsActive(true);
        when(currentSeasonRepository.findByIsActiveTrue()).thenReturn(Optional.of(current));

        when(playerRepository.findAllForSearch(activeSeason)).thenReturn(List.of());
        when(teamRepository.findAllForSearch(activeSeason)).thenReturn(List.of());

        mockMvc.perform(get("/api/search/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
