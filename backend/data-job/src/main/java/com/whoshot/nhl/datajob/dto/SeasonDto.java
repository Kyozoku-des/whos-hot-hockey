package com.whoshot.nhl.datajob.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO describing a single NHL season returned by the seasons endpoint.
 */
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
