package com.whoshot.nhl.datajob.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for player rows showing a placeholder instead of a team logo:
 * {@code PlayerInfoDto} must carry the landing endpoint's {@code teamLogo} so the player
 * entity's {@code teamLogoUrl} gets populated.
 */
class PlayerInfoDtoDeserializationTest {

    private static final String PLAYER_LANDING_JSON = """
            {
              "playerId": 8478402,
              "isActive": true,
              "currentTeamAbbrev": "EDM",
              "teamLogo": "https://assets.nhle.com/logos/nhl/svg/EDM_light.svg",
              "headshot": "https://assets.nhle.com/mugs/nhl/20252026/EDM/8478402.png",
              "firstName": { "default": "Connor" },
              "lastName": { "default": "McDavid" },
              "position": "C"
            }
            """;

    @Test
    void deserializesTeamLogo() throws Exception {
        PlayerInfoDto info = new ObjectMapper().readValue(PLAYER_LANDING_JSON, PlayerInfoDto.class);

        assertEquals("https://assets.nhle.com/logos/nhl/svg/EDM_light.svg", info.getTeamLogoUrl());
        assertEquals("EDM", info.getCurrentTeamAbbrev());
    }
}
