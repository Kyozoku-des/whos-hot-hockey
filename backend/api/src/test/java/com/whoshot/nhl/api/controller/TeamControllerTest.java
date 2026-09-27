package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.config.GlobalExceptionHandler;
import com.whoshot.nhl.api.dto.RosterPlayerDto;
import com.whoshot.nhl.api.dto.TeamDetailDto;
import com.whoshot.nhl.api.dto.TeamGameLogDto;
import com.whoshot.nhl.api.dto.TeamStandingsDto;
import com.whoshot.nhl.api.service.TeamService;
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

@WebMvcTest(TeamController.class)
@ContextConfiguration(classes = {TeamController.class, GlobalExceptionHandler.class})
class TeamControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TeamService teamService;

    @Test
    void getTeamStandings_returns200WithTeams() throws Exception {
        String season = "20252026";

        TeamStandingsDto team = new TeamStandingsDto(
                "TOR", "Toronto Maple Leafs", "Maple Leafs",
                "https://logo.png",
                50, 30, 15, 5, 65, 0.65,
                180, 150, 30,
                "Eastern", "Atlantic",
                3, 0,
                0.7, 0.8, 2.2,
                true, false, true,
                "BOS", "2026-03-28", true
        );

        when(teamService.getTeamStandings(season)).thenReturn(List.of(team));

        mockMvc.perform(get("/api/teams/standings").param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].teamCode").value("TOR"))
                .andExpect(jsonPath("$[0].teamName").value("Toronto Maple Leafs"))
                .andExpect(jsonPath("$[0].wins").value(30))
                .andExpect(jsonPath("$[0].points").value(65))
                .andExpect(jsonPath("$[0].hot").value(true))
                .andExpect(jsonPath("$[0].currentWinStreak").value(3))
                .andExpect(jsonPath("$[0].divisionName").value("Atlantic"))
                .andExpect(jsonPath("$[0].nextOpponentCode").value("BOS"));
    }

    @Test
    void getTeamDetail_returns200WithTeamAndRoster() throws Exception {
        String season = "20252026";

        RosterPlayerDto player = new RosterPlayerDto(
                34L, "Auston Matthews", "C", "TOR", "https://headshot.png"
        );

        TeamDetailDto detail = new TeamDetailDto(
                "TOR", "Toronto Maple Leafs", "Maple Leafs",
                "https://logo.png",
                50, 30, 15, 5, 65, 0.65,
                180, 150, 30,
                "Eastern", "Atlantic",
                3, 0,
                0.7, 0.8, 2.2,
                true, false, true,
                "BOS", "2026-03-28", true,
                List.of(player)
        );

        when(teamService.getTeamDetail("TOR", season)).thenReturn(detail);

        mockMvc.perform(get("/api/teams/TOR").param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.teamCode").value("TOR"))
                .andExpect(jsonPath("$.teamName").value("Toronto Maple Leafs"))
                .andExpect(jsonPath("$.wins").value(30))
                .andExpect(jsonPath("$.roster", hasSize(1)))
                .andExpect(jsonPath("$.roster[0].playerId").value(34))
                .andExpect(jsonPath("$.roster[0].fullName").value("Auston Matthews"))
                .andExpect(jsonPath("$.roster[0].positionCode").value("C"));
    }

    @Test
    void getTeamDetail_returns404ForUnknownTeam() throws Exception {
        when(teamService.getTeamDetail("XYZ", null))
                .thenThrow(new GlobalExceptionHandler.TeamNotFoundException("XYZ"));

        mockMvc.perform(get("/api/teams/XYZ"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Team not found"))
                .andExpect(jsonPath("$.teamCode").value("XYZ"));
    }

    @Test
    void getTeamGameLog_returns200WithGameLog() throws Exception {
        String season = "20252026";

        TeamGameLogDto game = new TeamGameLogDto(
                2025020001L, "2025-10-10", "BOS", true,
                4, 2, true, false, "Regular", 1
        );

        when(teamService.getTeamGameLog("TOR", season)).thenReturn(List.of(game));

        mockMvc.perform(get("/api/teams/TOR/game-log").param("season", season))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].gameId").value(2025020001L))
                .andExpect(jsonPath("$[0].opponentTeamCode").value("BOS"))
                .andExpect(jsonPath("$[0].goalsFor").value(4))
                .andExpect(jsonPath("$[0].won").value(true))
                .andExpect(jsonPath("$[0].gameNumber").value(1));
    }
}
