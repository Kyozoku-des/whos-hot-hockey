package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.config.GlobalExceptionHandler;
import com.whoshot.nhl.api.dto.PlayerDetailDto;
import com.whoshot.nhl.api.dto.PlayerGameLogDto;
import com.whoshot.nhl.api.dto.PlayerStandingsDto;
import com.whoshot.nhl.api.service.PlayerService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PlayerController.class)
@ContextConfiguration(classes = {PlayerController.class, GlobalExceptionHandler.class})
class PlayerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PlayerService playerService;

    @Test
    void getPlayerStandings_returns200WithPlayers() throws Exception {
        String season = "20252026";

        PlayerStandingsDto player = new PlayerStandingsDto(
                8478402L, "Connor", "McDavid", "Connor McDavid",
                "C", "EDM", "https://logo.png", "https://headshot.jpg",
                50, 30, 45, 75, 1.5, 15, 2.0,
                true, false, 5, 0
        );

        when(playerService.getPlayerStandings(season)).thenReturn(List.of(player));

        mockMvc.perform(get("/api/players/standings").param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].playerId").value(8478402))
                .andExpect(jsonPath("$[0].firstName").value("Connor"))
                .andExpect(jsonPath("$[0].lastName").value("McDavid"))
                .andExpect(jsonPath("$[0].fullName").value("Connor McDavid"))
                .andExpect(jsonPath("$[0].positionCode").value("C"))
                .andExpect(jsonPath("$[0].teamCode").value("EDM"))
                .andExpect(jsonPath("$[0].points").value(75))
                .andExpect(jsonPath("$[0].hot").value(true))
                .andExpect(jsonPath("$[0].currentPointStreak").value(5));
    }

    @Test
    void getPlayerDetail_returns200WithPlayer() throws Exception {
        long playerId = 8478402L;
        String season = "20252026";

        PlayerDetailDto detail = new PlayerDetailDto(
                playerId, "Connor", "McDavid", "Connor McDavid",
                "C", "EDM", "https://logo.png", "https://headshot.jpg",
                50, 30, 45, 75, 1.5, 15,
                5, 0, 2.0, true, false,
                new PlayerDetailDto.NextGameDto("2026-03-28", "CGY", "H")
        );

        when(playerService.getPlayerDetail(playerId, season)).thenReturn(detail);

        mockMvc.perform(get("/api/players/{playerId}", playerId).param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.playerId").value(8478402))
                .andExpect(jsonPath("$.firstName").value("Connor"))
                .andExpect(jsonPath("$.fullName").value("Connor McDavid"))
                .andExpect(jsonPath("$.points").value(75))
                .andExpect(jsonPath("$.hot").value(true))
                .andExpect(jsonPath("$.currentPointStreak").value(5))
                .andExpect(jsonPath("$.nextGame.date").value("2026-03-28"))
                .andExpect(jsonPath("$.nextGame.opponentAbbrev").value("CGY"))
                .andExpect(jsonPath("$.nextGame.homeRoadFlag").value("H"));
    }

    @Test
    void getPlayerDetail_returns404ForUnknownPlayer() throws Exception {
        long playerId = 9999999L;
        String season = "20252026";

        when(playerService.getPlayerDetail(playerId, season))
                .thenThrow(new GlobalExceptionHandler.PlayerNotFoundException(playerId));

        mockMvc.perform(get("/api/players/{playerId}", playerId).param("season", season))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Player not found"))
                .andExpect(jsonPath("$.playerId").value(9999999));
    }

    @Test
    void getPlayerGameLog_returns200WithGameLog() throws Exception {
        long playerId = 8478402L;
        String season = "20252026";

        PlayerGameLogDto gameLog = new PlayerGameLogDto(
                2025020100L, "2025-10-15", "CGY", true,
                2, 1, 3, 2, 5, 1200, true, 1
        );

        when(playerService.getPlayerGameLog(playerId, season)).thenReturn(List.of(gameLog));

        mockMvc.perform(get("/api/players/{playerId}/game-log", playerId).param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].gameId").value(2025020100L))
                .andExpect(jsonPath("$[0].gameDate").value("2025-10-15"))
                .andExpect(jsonPath("$[0].opponentTeamCode").value("CGY"))
                .andExpect(jsonPath("$[0].homeGame").value(true))
                .andExpect(jsonPath("$[0].goals").value(2))
                .andExpect(jsonPath("$[0].assists").value(1))
                .andExpect(jsonPath("$[0].points").value(3))
                .andExpect(jsonPath("$[0].gameWon").value(true))
                .andExpect(jsonPath("$[0].gameNumber").value(1));
    }
}
