package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.TeamStandingsDto;
import com.whoshot.nhl.domain.entity.Team;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit tests for the derived team values computed in {@link SeasonDataWriter#applyStandings}:
 * win/loss streaks and last-10-games point percentage and points per game.
 */
class TeamStandingsCalculationTest {

    private static final double DELTA = 1e-9;

    private TeamStandingsDto standing(String streakCode, Integer streakCount,
                                      Integer l10Wins, Integer l10Losses, Integer l10OtLosses) {
        TeamStandingsDto.LocalizedField abbrev = new TeamStandingsDto.LocalizedField();
        abbrev.setDefaultValue("TOR");
        TeamStandingsDto.LocalizedField name = new TeamStandingsDto.LocalizedField();
        name.setDefaultValue("Toronto Maple Leafs");

        TeamStandingsDto dto = new TeamStandingsDto();
        dto.setTeamAbbrev(abbrev);
        dto.setTeamName(name);
        dto.setStreakCode(streakCode);
        dto.setStreakCount(streakCount);
        dto.setL10Wins(l10Wins);
        dto.setL10Losses(l10Losses);
        dto.setL10OtLosses(l10OtLosses);
        return dto;
    }

    private Team apply(TeamStandingsDto standing) {
        Team team = new Team();
        SeasonDataWriter.applyStandings(team, standing, "20252026");
        return team;
    }

    @Test
    void winStreak_setsWinsAndClearsLosses() {
        Team team = new Team();
        team.setCurrentLossStreak(4);

        SeasonDataWriter.applyStandings(team, standing("W", 3, 5, 5, 0), "20252026");

        assertEquals(3, team.getCurrentWinStreak());
        assertEquals(0, team.getCurrentLossStreak());
    }

    @Test
    void lossStreak_setsLossesAndClearsWins() {
        Team team = new Team();
        team.setCurrentWinStreak(6);

        SeasonDataWriter.applyStandings(team, standing("L", 2, 5, 5, 0), "20252026");

        assertEquals(0, team.getCurrentWinStreak());
        assertEquals(2, team.getCurrentLossStreak());
    }

    @Test
    void overtimeLossStreak_countsAsLossStreak() {
        Team team = apply(standing("OT", 1, 5, 4, 1));

        assertEquals(0, team.getCurrentWinStreak());
        assertEquals(1, team.getCurrentLossStreak());
    }

    @Test
    void last10_pointPercentageAndPpg_countOtLossesAsOnePoint() {
        // 6 W (12 pts) + 3 L (0 pts) + 1 OTL (1 pt) = 13 points over 10 games.
        Team team = apply(standing("W", 1, 6, 3, 1));

        assertEquals(13.0 / 20, team.getLast10GamesPointPercentage(), DELTA);
        assertEquals(1.3, team.getLast10GamesPPG(), DELTA);
    }

    @Test
    void last10_usesActualGameCountWhenFewerThanTen() {
        // Early season: 2 W + 1 OTL = 5 points over 3 games.
        Team team = apply(standing("W", 2, 2, 0, 1));

        assertEquals(5.0 / 6, team.getLast10GamesPointPercentage(), DELTA);
        assertEquals(5.0 / 3, team.getLast10GamesPPG(), DELTA);
    }

    @Test
    void last10_perfectAndWinlessRecords_hitTheBounds() {
        Team perfect = apply(standing("W", 10, 10, 0, 0));
        Team winless = apply(standing("L", 10, 0, 10, 0));

        assertEquals(1.0, perfect.getLast10GamesPointPercentage(), DELTA);
        assertEquals(2.0, perfect.getLast10GamesPPG(), DELTA);
        assertEquals(0.0, winless.getLast10GamesPointPercentage(), DELTA);
        assertEquals(0.0, winless.getLast10GamesPPG(), DELTA);
    }

    @Test
    void last10_noGamesPlayed_leavesValuesUnset() {
        Team team = apply(standing(null, null, 0, 0, 0));

        assertNull(team.getLast10GamesPointPercentage());
        assertNull(team.getLast10GamesPPG());
    }

    @Test
    void last10_missingComponent_leavesValuesUnset() {
        Team team = apply(standing("W", 1, 6, null, 1));

        assertNull(team.getLast10GamesPointPercentage());
        assertNull(team.getLast10GamesPPG());
    }
}
