package com.whoshot.nhl.api.controller;

import com.whoshot.nhl.api.config.GlobalExceptionHandler;
import com.whoshot.nhl.api.service.TeamService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(TeamController.class)
@ContextConfiguration(classes = {TeamController.class, GlobalExceptionHandler.class})
@ExtendWith(OutputCaptureExtension.class)
class HttpErrorSecurityTest {
    @Autowired MockMvc mvc;
    @MockitoBean TeamService teams;

    @Test
    void unsupportedMethodsReturn405WithoutEchoingOrLoggingInput(CapturedOutput output) throws Exception {
        String marker = "UNTRUSTED_METHOD_MARKER";
        for (String method : new String[]{"POST", marker}) {
            mvc.perform(request(HttpMethod.valueOf(method), "/api/teams/standings"))
                    .andExpect(status().isMethodNotAllowed())
                    .andExpect(header().string("Allow", containsString("GET")))
                    .andExpect(jsonPath("$.error").value("Method Not Allowed"))
                    .andExpect(content().string(org.hamcrest.Matchers.not(containsString(marker))));
        }
        assertThat(output.getAll()).doesNotContain("Unhandled exception", marker, "HttpRequestMethodNotSupportedException");
    }

    @Test
    void unexpectedServerFailuresStillReturn500AndAreLogged(CapturedOutput output) throws Exception {
        when(teams.getTeamStandings(null)).thenThrow(new IllegalStateException("test server failure"));
        mvc.perform(get("/api/teams/standings"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal server error"));
        assertThat(output.getAll()).contains("Unhandled exception");
    }
}
