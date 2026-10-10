package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * Response DTO for NHL API player standings endpoint.
 * GET /v1/skater-stats-leaders/{season}/{gameType}?categories=points&limit=-1
 * Only includes the fields we actually use. A rostered player missing from the leaders (no points
 * yet, or a goalie) has no expected total: {@code points} is null.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerStandingDto {
    private Long id;
    @JsonProperty("value")
    private Integer points;
}
