package com.whoshot.nhl.datajob.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * DTO describing a single NHL season returned by the seasons endpoint.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeasonDto {
    private String id;
    private LocalDateTime startDate;
    private LocalDateTime regularSeasonEndDate;
}
