package com.whoshot.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PlayerGameLogDto {
    private Long gameId;
    private String gameDate;
    private String opponentAbbrev;
    private String homeRoadFlag;
    private Integer goals;
    private Integer assists;
    private Integer points;
    private Integer plusMinus;
    private Integer shots;
    private String toi;
}
