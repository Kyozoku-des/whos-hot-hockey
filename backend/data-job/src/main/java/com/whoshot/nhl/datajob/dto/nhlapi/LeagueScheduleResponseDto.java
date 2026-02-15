package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Wrapper DTO for NHL API league schedule response.
 * The API returns an object with a "gameWeek" array.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LeagueScheduleResponseDto {
    private List<GameWeek> gameWeek;

    /**
     * Sub-object representing the "gameWeek" array in the API response.
     * Contains a list of GameDto objects.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GameWeek {
        private List<GameDto> games;
    }
}
