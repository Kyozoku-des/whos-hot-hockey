package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameDto;
import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.dto.nhlapi.TeamStandingsDto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Small, internally-consistent upstream API fixtures shared by backfill integration tests: two
 * teams, two games between them, and one player whose game-log points sum to the standings total
 * (required for {@code StatisticsCalculationService} validation to pass).
 */
final class BackfillFixtures {

    static final String SEASON_ID = "20232024";
    static final long PLAYER_ID = 8478402L;

    private BackfillFixtures() {
    }

    static SeasonDto season(String seasonId, LocalDateTime start, LocalDateTime end) {
        SeasonDto dto = new SeasonDto();
        dto.setId(seasonId);
        dto.setStartDate(start);
        dto.setRegularSeasonEndDate(end);
        return dto;
    }

    static TeamStandingsDto teamStanding(String code) {
        TeamStandingsDto dto = new TeamStandingsDto();
        TeamStandingsDto.LocalizedField field = new TeamStandingsDto.LocalizedField();
        field.setDefaultValue(code);
        dto.setTeamAbbrev(field);
        TeamStandingsDto.LocalizedField name = new TeamStandingsDto.LocalizedField();
        name.setDefaultValue(code + " Team");
        dto.setTeamName(name);
        dto.setGamesPlayed(2);
        dto.setWins(1);
        dto.setLosses(1);
        dto.setOtLosses(0);
        dto.setPoints(2);
        return dto;
    }

    static GameDto game(long id, String startTimeUTC, String homeAbbrev, int homeScore,
                         String awayAbbrev, int awayScore) {
        GameDto dto = new GameDto();
        dto.setId(id);
        dto.setStartTimeUTC(startTimeUTC);
        dto.setGameDate(startTimeUTC.substring(0, 10));
        dto.setGameType(2);
        dto.setGameState(GameState.OFF);

        GameDto.TeamInfo home = new GameDto.TeamInfo();
        home.setAbbrev(homeAbbrev);
        home.setScore(homeScore);
        dto.setHomeTeam(home);

        GameDto.TeamInfo away = new GameDto.TeamInfo();
        away.setAbbrev(awayAbbrev);
        away.setScore(awayScore);
        dto.setAwayTeam(away);

        GameDto.GameOutcome outcome = new GameDto.GameOutcome();
        outcome.setLastPeriodType("REG");
        dto.setGameOutcome(outcome);
        return dto;
    }

    /** Two completed regular-season games between COL and MTL. */
    static List<GameDto> twoGameSchedule() {
        return List.of(
                game(1001L, "2023-10-15T00:00:00Z", "COL", 3, "MTL", 2),
                game(1002L, "2023-11-01T00:00:00Z", "MTL", 4, "COL", 1)
        );
    }

    static PlayerStandingDto playerStanding(long id, int points) {
        PlayerStandingDto dto = new PlayerStandingDto();
        dto.setId(id);
        dto.setPoints(points);
        return dto;
    }

    static PlayerInfoDto playerInfo(long id, boolean active, String teamCode) {
        PlayerInfoDto dto = new PlayerInfoDto();
        dto.setPlayerId(id);
        dto.setActive(active);
        dto.setCurrentTeamAbbrev(teamCode);
        dto.setPosition("C");
        dto.setHeadshotUrl("https://example.test/headshot.png");
        dto.setFirstName(name("Connor"));
        dto.setLastName(name("McDavid"));
        return dto;
    }

    private static PlayerInfoDto.NameDto name(String value) {
        PlayerInfoDto.NameDto dto = new PlayerInfoDto.NameDto();
        dto.setName(value);
        return dto;
    }

    static PlayerGameLogDto gameLog(long gameId, String date, String opponent, String homeRoadFlag,
                                     int goals, int assists) {
        PlayerGameLogDto dto = new PlayerGameLogDto();
        dto.setGameId(gameId);
        dto.setGameDate(date);
        dto.setOpponentAbbrev(opponent);
        dto.setHomeRoadFlag(homeRoadFlag);
        dto.setGoals(goals);
        dto.setAssists(assists);
        dto.setPoints(goals + assists);
        dto.setPlusMinus(0);
        dto.setShots(2);
        dto.setToi("18:00");
        return dto;
    }

    /**
     * Two game logs for {@link #PLAYER_ID} on COL, matching {@link #twoGameSchedule()}, with points
     * summing to 3 (matches {@link #playerStanding} totals of 3 used in the tests below).
     * Returned most-recent-first, as the upstream API does.
     */
    static List<PlayerGameLogDto> twoGameLogsMostRecentFirst() {
        return List.of(
                gameLog(1002L, "2023-11-01", "MTL", "R", 0, 1),
                gameLog(1001L, "2023-10-15", "MTL", "H", 1, 1)
        );
    }
}
