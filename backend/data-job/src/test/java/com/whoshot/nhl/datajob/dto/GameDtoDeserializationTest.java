package com.whoshot.nhl.datajob.dto;

import tools.jackson.databind.ObjectMapper;
import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Regression coverage for research.md R-005: {@code GameDto} must carry final scores and the
 * period-type of the game's outcome so a backfill can derive {@code TeamGame.goalsFor/goalsAgainst}
 * and {@code TeamGame.overtimeLoss} without an extra API call per game.
 */
class GameDtoDeserializationTest {

    private static final String CLUB_SCHEDULE_GAME_JSON = """
            {
              "id": 2024020500,
              "season": 20242025,
              "gameType": 2,
              "startTimeUTC": "2025-01-15T00:00:00Z",
              "gameDate": "2025-01-14",
              "gameState": "OFF",
              "gameOutcome": { "lastPeriodType": "OT" },
              "homeTeam": { "abbrev": "COL", "score": 3 },
              "awayTeam": { "abbrev": "MTL", "score": 2 }
            }
            """;

    @Test
    void deserializesScoresAndGameOutcome() throws Exception {
        ObjectMapper mapper = new ObjectMapper();

        GameDto game = mapper.readValue(CLUB_SCHEDULE_GAME_JSON, GameDto.class);

        assertEquals(3, game.getHomeTeam().getScore());
        assertEquals(2, game.getAwayTeam().getScore());
        assertNotNull(game.getGameOutcome());
        assertEquals("OT", game.getGameOutcome().getLastPeriodType());
        assertEquals("2025-01-14", game.getGameDate());
    }
}
