package com.nhl.whoshotbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO for team game log data.
 * Used to return game-by-game results for graphs.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TeamGameLogDTO {

    private Integer gameNumber;
    private String gameDate;
    private Boolean won;
    private Integer goalsFor;
    private Integer goalsAgainst;
    private String opponentTeamCode;
    private Boolean homeGame;
}
