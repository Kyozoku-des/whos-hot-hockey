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
    private Integer gameType;
    private GameState gameState;
    private TeamInfo awayTeam;
    private TeamInfo homeTeam;
    private GameOutcome gameOutcome;

    /**
     * Team identifier subset included in schedule game objects.
     * {@code score} is present once the game has been played.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamInfo {
        private String abbrev;
        private Integer score;
    }

    /**
     * Final-result subset of a completed game, used to distinguish a regulation result from an
     * overtime/shootout result (needed for {@code TeamGame.overtimeLoss}).
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class GameOutcome {
        private String lastPeriodType;
    }
}
