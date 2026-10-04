package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.dto.SearchIndexDto;
import com.whoshot.nhl.api.dto.SearchResultDto;
import com.whoshot.nhl.api.service.SearchService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

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
    private SearchService searchService;

    @Test
    void getAllSearchableItems_returnsIndexEnvelope() throws Exception {
        String season = "20252026";
        when(searchService.getSearchIndex(season)).thenReturn(new SearchIndexDto(season, 2, List.of(
                new SearchResultDto("TEAM", "TOR", "Toronto Maple Leafs", "TOR", "TOR", "https://logo.png"),
                new SearchResultDto("PLAYER", "8478402", "Connor McDavid", "C", "EDM", "https://headshot.jpg")
        )));

        mockMvc.perform(get("/api/search/all").param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value(season))
                .andExpect(jsonPath("$.count").value(2))
                .andExpect(jsonPath("$.results", hasSize(2)))
                .andExpect(jsonPath("$.results[0].type").value("TEAM"))
                .andExpect(jsonPath("$.results[1].type").value("PLAYER"))
                .andExpect(jsonPath("$.results[1].secondaryInfo").value("C"))
                // The season lives on the envelope only, not repeated per row.
                .andExpect(jsonPath("$.results[0].season").doesNotExist());
    }

    @Test
    void getAllSearchableItems_passesMissingSeasonThrough() throws Exception {
        when(searchService.getSearchIndex(null)).thenReturn(new SearchIndexDto("20252026", 0, List.of()));

        mockMvc.perform(get("/api/search/all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value("20252026"))
                .andExpect(jsonPath("$.results", hasSize(0)));
    }
}
