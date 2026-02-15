package com.whoshot.nhl.datajob.dto.nhlapi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum GameState {
    FUT,
    LIVE,
    FINAL;

    @JsonCreator
    public static GameState fromString(String value) {
        if (value == null) {
            return null;
        }
        return GameState.valueOf(value.toUpperCase());
    }

    @JsonValue
    public String toJson() {
        return name();
    }
}
