package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Response DTO for NHL API team schedule endpoint.
 * GET /v1/club-schedule-season/{teamCode}/{seasonId}
 * Only includes the fields we actually use.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GameDto {
    private Long id;
    private Integer season;
    private String startTimeUTC;
    private TeamInfo awayTeam;
    private TeamInfo homeTeam;

    /**
     * Team identifier subset included in schedule game objects.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private String abbrev;
    }
}
