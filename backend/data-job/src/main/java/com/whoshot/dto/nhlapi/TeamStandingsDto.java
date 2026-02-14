package com.whoshot.dto.nhlapi;

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
