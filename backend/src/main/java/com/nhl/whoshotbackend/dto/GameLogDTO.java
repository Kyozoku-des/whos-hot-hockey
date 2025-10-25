package com.nhl.whoshotbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for player game log data.
 * Used to return game-by-game statistics for graphs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GameLogDTO {

    private Integer gameNumber;
    private String gameDate;
    private Integer points;
    private Integer goals;
    private Integer assists;
    private String opponentTeamCode;
    private Boolean homeGame;
}
