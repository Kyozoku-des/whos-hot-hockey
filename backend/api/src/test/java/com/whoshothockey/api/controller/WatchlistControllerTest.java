package com.whoshothockey.api.controller;

import com.whoshothockey.api.config.GlobalExceptionHandler;
import com.whoshothockey.api.dto.WatchlistDto;
import com.whoshothockey.api.dto.WatchlistDto.Direction;
import com.whoshothockey.api.dto.WatchlistDto.Rule;
import com.whoshothockey.api.dto.WatchlistDto.Type;
import com.whoshothockey.api.service.WatchlistService;
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

@WebMvcTest(WatchlistController.class)
@ContextConfiguration(classes = {WatchlistController.class, GlobalExceptionHandler.class})
class WatchlistControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private WatchlistService watchlistService;

    @Test
    void getWatchlist_returns200WithItems() throws Exception {
        var item = new WatchlistDto.Item(Type.PLAYER, "8478402", "Connor McDavid", "EDM",
                Direction.UP, Rule.LAST_SEASON, 2.1, 1.36, 10, 3.2);
        when(watchlistService.getWatchlist("20262027"))
                .thenReturn(new WatchlistDto("20262027", List.of(item)));

        mockMvc.perform(get("/api/watchlist").param("season", "20262027"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.season").value("20262027"))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].type").value("PLAYER"))
                .andExpect(jsonPath("$.items[0].id").value("8478402"))
                .andExpect(jsonPath("$.items[0].direction").value("UP"))
                .andExpect(jsonPath("$.items[0].rule").value("LAST_SEASON"))
                .andExpect(jsonPath("$.items[0].recent").value(2.1))
                .andExpect(jsonPath("$.items[0].baseline").value(1.36))
                .andExpect(jsonPath("$.items[0].recentGames").value(10))
                .andExpect(jsonPath("$.items[0].score").value(3.2));
    }

    @Test
    void getWatchlist_withoutSeason_usesTheActiveSeason() throws Exception {
        when(watchlistService.getWatchlist(null)).thenReturn(WatchlistDto.empty(null));

        mockMvc.perform(get("/api/watchlist"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)));
    }
}
