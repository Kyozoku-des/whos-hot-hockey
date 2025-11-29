package com.whoshot.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class SeasonDto {
    @JsonProperty("id")
    private String id;
    @JsonProperty("startDate")
    private LocalDateTime startDate;
    @JsonProperty("regularSeasonEndDate")
    private LocalDateTime regularSeasonEndDate;
    @JsonProperty("numberOfGames")
    private int numberOfGames;
}
