package com.whoshot.nhl.datajob.dto.nhlapi;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

import lombok.Getter;

@Getter
public enum GameState {
    FUT,
    LIVE,
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

    @JsonValue
    public String toJson() {
        return name();
    }
}
