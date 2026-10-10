package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Response DTO for NHL API daily scores endpoint.
 * GET /v1/score/{date|now}
 * Only includes the fields we actually use.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ScoreResponseDto {
    /** Previous date with games ({@code yyyy-MM-dd}). */
    private String prevDate;
    /** Game day the response describes ({@code yyyy-MM-dd}). */
    private String currentDate;
    private List<ScoreGame> games;

    /**
     * A game of the day with its scoring summary. {@code goals} is absent before the game starts.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ScoreGame {
        private Long id;
        private Long season;
        private Integer gameType;
        private String gameDate;
        private String startTimeUTC;
        private GameState gameState;
        private GameDto.TeamInfo awayTeam;
        private GameDto.TeamInfo homeTeam;
        private GameDto.GameOutcome gameOutcome;
        private List<Goal> goals;
    }

    /**
     * One goal, in the order scored, with the score after it.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Goal {
        private Integer period;
        private PeriodDescriptor periodDescriptor;
        private String timeInPeriod;
        private Long playerId;
        private PlayerInfoDto.NameDto name;
        private String teamAbbrev;
        private Integer awayScore;
        private Integer homeScore;
        private String strength;
        private List<Assist> assists;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PeriodDescriptor {
        private String periodType;
    }

    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class Assist {
        private Long playerId;
        private PlayerInfoDto.NameDto name;
    }
}
