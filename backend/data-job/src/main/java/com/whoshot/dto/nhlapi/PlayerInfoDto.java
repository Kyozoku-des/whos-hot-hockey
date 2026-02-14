package com.whoshot.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response DTO for NHL API player info endpoint.
 * GET /v1/player/{playerId}/landing
 * Only includes the fields we actually use.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerInfoDto {
    private Long playerId;

    @JsonProperty("isActive")
    private boolean isActive;

    @JsonProperty("headshot")
    private String headshotUrl;
    // Unused
    private String heroImage;

    /*"firstName": {
        "default": "Connor"
    },
            "lastName": {
        "default": "Bedard"
    },*/

    @JsonProperty("firstName")
    private NameDto firstName;
    @JsonProperty("lastName")
    private NameDto lastName;
    private String currentTeamAbbrev;
    private String position;

    /**
     * Localized string wrapper used by NHL API name fields.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class NameDto {
        @JsonProperty("default")
        private String name;
    }
}
