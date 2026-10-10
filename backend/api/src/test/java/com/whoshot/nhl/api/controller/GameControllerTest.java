package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.config.GlobalExceptionHandler;
import com.whoshot.nhl.api.dto.ScoreboardDto;
import com.whoshot.nhl.api.service.ScoreboardService;
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

@WebMvcTest(GameController.class)
@ContextConfiguration(classes = {GameController.class, GlobalExceptionHandler.class})
class GameControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ScoreboardService scoreboardService;

    @Test
    void getLatestScoreboard_returns200WithGamesAndGoals() throws Exception {
        var goal = new ScoreboardDto.Goal(1, 1, "REG", "09:19", "DET", 0, 1, "pp",
                new ScoreboardDto.Point(8478042L, "V. Arvidsson", true, false),
                List.of(new ScoreboardDto.Point(8482078L, "L. Raymond", false, true)));
        var game = new ScoreboardDto.Game(2026020066L, "OFF", "2026-10-09T23:00:00Z",
                "SEA", 6, "DET", 3, "REG", List.of(goal));
        when(scoreboardService.getLatestScoreboard())
                .thenReturn(new ScoreboardDto("2026-10-09", false, List.of(game)));

        mockMvc.perform(get("/api/games/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameDate").value("2026-10-09"))
                .andExpect(jsonPath("$.live").value(false))
                .andExpect(jsonPath("$.games", hasSize(1)))
                .andExpect(jsonPath("$.games[0].awayTeamCode").value("SEA"))
                .andExpect(jsonPath("$.games[0].awayScore").value(6))
                .andExpect(jsonPath("$.games[0].goals[0].scorer.name").value("V. Arvidsson"))
                .andExpect(jsonPath("$.games[0].goals[0].scorer.streakExtended").value(true))
                .andExpect(jsonPath("$.games[0].goals[0].assists[0].hot").value(true));
    }

    @Test
    void getLatestScoreboard_withNoGames_returnsEmptyScoreboard() throws Exception {
        when(scoreboardService.getLatestScoreboard()).thenReturn(ScoreboardDto.empty());

        mockMvc.perform(get("/api/games/latest"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gameDate").isEmpty())
                .andExpect(jsonPath("$.games", hasSize(0)));
    }
}
