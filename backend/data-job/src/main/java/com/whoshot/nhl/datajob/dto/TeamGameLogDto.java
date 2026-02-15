package com.whoshot.nhl.datajob.dto;

import lombok.Data;

/**
 * DTO for team game log data.
 * Used to return game-by-game results for graphs.
 */
@Data
public class TeamGameLogDto {

    private Integer gameNumber;
    private String gameDate;
    private Boolean won;
    private Integer goalsFor;
    private Integer goalsAgainst;
    private String opponentTeamCode;
    private Boolean homeGame;
}
