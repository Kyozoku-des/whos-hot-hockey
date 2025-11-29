package com.whoshot.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Wrapper DTO for NHL API team schedule response.
 * The API returns an object with a "games" array.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamScheduleResponseDto {
    private List<GameDto> games;
}
