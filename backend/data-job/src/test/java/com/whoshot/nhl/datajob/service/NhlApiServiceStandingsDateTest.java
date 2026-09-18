package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.StandingsResponseDto;
import com.whoshot.nhl.datajob.dto.nhlapi.TeamStandingsDto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for the historical-standings decision in research.md (R-001): a backfill of a
 * completed season must read that season's final standings, not today's, so it must hit
 * {@code /v1/standings/{date}} rather than {@code /v1/standings/now}.
 */
@ExtendWith(MockitoExtension.class)
class NhlApiServiceStandingsDateTest {

    private static final String BASE_URL = "https://api-web.nhle.com";

    @Mock
    private ApiClient apiClient;

    private NhlApiService nhlApiService;

    private void setUp() {
        nhlApiService = new NhlApiService(apiClient);
        ReflectionTestUtils.setField(nhlApiService, "baseUrl", BASE_URL);
        ReflectionTestUtils.setField(nhlApiService, "alternateBaseUrl", "https://api.nhle.com");
    }

    @Test
    void getTeamStandings_withDate_requestsDatedEndpoint() {
        setUp();
        StandingsResponseDto response = new StandingsResponseDto();
        response.setStandings(java.util.List.of(new TeamStandingsDto()));
        when(apiClient.get(any(), any(ParameterizedTypeReference.class))).thenReturn(response);

        nhlApiService.getTeamStandings("2025-04-17");

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).get(urlCaptor.capture(), any(ParameterizedTypeReference.class));
        assertEquals(BASE_URL + "/v1/standings/2025-04-17", urlCaptor.getValue());
    }

    @Test
    void getTeamStandings_noArgs_stillRequestsNow() {
        setUp();
        StandingsResponseDto response = new StandingsResponseDto();
        response.setStandings(java.util.List.of(new TeamStandingsDto()));
        when(apiClient.get(any(), any(ParameterizedTypeReference.class))).thenReturn(response);

        nhlApiService.getTeamStandings();

        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(apiClient).get(urlCaptor.capture(), any(ParameterizedTypeReference.class));
        assertEquals(BASE_URL + "/v1/standings/now", urlCaptor.getValue());
    }
}
