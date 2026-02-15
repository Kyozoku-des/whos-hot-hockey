package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import java.util.List;

/**
 * Response DTO for NHL API player game log endpoint.
 * GET /v1/player/{playerId}/game-log/{season}/{gameType}
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerGameLogsResponse {

    private Integer seasonId;
    private List<PlayerGameLogDto> gameLog;
}
