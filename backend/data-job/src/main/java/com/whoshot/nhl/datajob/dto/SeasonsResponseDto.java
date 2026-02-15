package com.whoshot.nhl.datajob.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

/**
 * Wrapper DTO for the seasons API response.
 * The API returns an object with a 'data' array, not a direct array.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SeasonsResponseDto {
    private List<SeasonDto> data;
    private Integer total;
}
