package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Response DTO for NHL API team roster endpoint.
 * GET /v1/roster/{teamCode}/{season}
 * Only includes the fields we actually use.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class TeamRosterResponseDto {
    private List<RosterPlayer> forwards;
    private List<RosterPlayer> defensemen;
    private List<RosterPlayer> goalies;

    /** Every rostered player's ID: forwards, defensemen and goalies. */
    public List<Long> playerIds() {
        return Stream.of(forwards, defensemen, goalies)
                .filter(Objects::nonNull)
                .flatMap(List::stream)
                .map(RosterPlayer::getId)
                .filter(Objects::nonNull)
                .toList();
    }

    /**
     * One player on the roster.
     */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class RosterPlayer {
        private Long id;
    }
}
