package com.whoshot.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Wrapper DTO for NHL API standings response.
 * The API returns an object with a "standings" array.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class StandingsResponseDto {
    private List<TeamStandingsDto> standings;
}
