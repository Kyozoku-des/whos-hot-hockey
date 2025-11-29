package com.whoshot.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for NHL API game boxscore endpoint.
 * Only includes the fields we actually use: player points, goals, assists.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class BoxScoreDto {
    private PlayerByGameStats playerByGameStats;

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerByGameStats {
        private TeamPlayerStats awayTeam;
        private TeamPlayerStats homeTeam;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TeamPlayerStats {
        private List<PlayerStats> forwards;
        private List<PlayerStats> defense;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PlayerStats {
        private Long playerId;
        private Integer goals;
        private Integer assists;
        private Integer points;
    }
}
