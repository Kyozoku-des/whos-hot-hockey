package com.whoshot.nhl.datajob.dto.nhlapi;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum GameState {
    FUT,
    PRE,
    LIVE,
    CRIT,
    OVER,
    OFF,
    FINAL,
    UNKNOWN;

    @JsonCreator
    public static GameState fromString(String value) {
        if (value == null) return UNKNOWN;
        return Arrays.stream(values())
                .filter(state -> state.name().equalsIgnoreCase(value))
                .findFirst()
                .orElse(UNKNOWN);
    }

    /**
     * Whether the game has ended and its score is final. {@code OVER} is set at the final horn,
     * before the NHL marks the result {@code FINAL}/{@code OFF}; the score no longer changes.
     *
     * @return true for a finished game
     */
    public boolean isCompleted() {
        return this == OVER || this == FINAL || this == OFF;
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
