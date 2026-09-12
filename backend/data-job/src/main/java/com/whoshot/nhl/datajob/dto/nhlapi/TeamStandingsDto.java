package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response DTO for NHL API team standings endpoint.
 * GET /v1/standings/now
 * Only includes the fields we actually use.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamStandingsDto {
    private LocalizedField teamAbbrev;
    private LocalizedField teamName;
    private String teamLogo;
    private Integer points;
    private Integer wins;
    private Integer losses;
    private Integer otLosses;
    private Integer gamesPlayed;
    private String streakCode;       // "W", "L", "OT"
    private Integer streakCount;
    private Integer l10Wins;
    private Integer l10Losses;
    private Integer l10OtLosses;
    private Double pointPctg;
    private String conferenceName;
    private String divisionName;
    private Integer goalFor;
    private Integer goalAgainst;
    private Integer goalDifferential;

    /**
     * Localized NHL API field that stores its primary value under the "default" key.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class LocalizedField {
        @JsonProperty("default")
        private String defaultValue;
    }
}
