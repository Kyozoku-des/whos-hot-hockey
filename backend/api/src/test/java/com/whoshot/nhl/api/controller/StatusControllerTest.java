package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.config.GlobalExceptionHandler;
import com.whoshot.nhl.api.dto.StatusDto;
import com.whoshot.nhl.api.service.StatusService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(StatusController.class)
@ContextConfiguration(classes = {StatusController.class, GlobalExceptionHandler.class})
class StatusControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StatusService statusService;

    @Test
    void getStatus_returnsLastSuccessfulSync() throws Exception {
        when(statusService.getStatus()).thenReturn(new StatusDto("2026-10-14T18:05:12.345Z"));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statsUpdatedAt").value("2026-10-14T18:05:12.345Z"));
    }

    @Test
    void getStatus_beforeFirstSync_returnsNull() throws Exception {
        when(statusService.getStatus()).thenReturn(new StatusDto(null));

        mockMvc.perform(get("/api/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.statsUpdatedAt").isEmpty());
    }
}
