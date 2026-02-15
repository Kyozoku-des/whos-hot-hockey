package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

/**
 * Wrapper DTO for NHL API player standings response.
 * The API returns an object with a "points" array.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerStandingsResponse {
    @JsonProperty("points")
    private List<PlayerStandingDto> players;
}
