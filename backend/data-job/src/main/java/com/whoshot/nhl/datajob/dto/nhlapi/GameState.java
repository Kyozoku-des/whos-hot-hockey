package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GameState {
    FUT,
    PRE,
    LIVE,
    CRIT,
    OVER,
    FINAL,
    OFF;

    @JsonCreator
    public static GameState fromString(String value) {
        if (value == null) {
            return null;
        }
        try {
            return GameState.valueOf(value.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OFF;
        }
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
