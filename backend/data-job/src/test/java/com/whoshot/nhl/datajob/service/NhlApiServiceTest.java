package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.ApiRequestProperties;
import com.whoshot.nhl.datajob.dto.nhlapi.GameState;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Checks the endpoint each {@link NhlApiService} method calls and how its response is mapped,
 * against a mocked server: no NHL requests and no database.
 */
class NhlApiServiceTest {

    private static final String WEB_URL = "https://api-web.nhle.com";
    private static final String STATS_URL = "https://api.nhle.com";

    private MockRestServiceServer server;
    private NhlApiService service;

    @BeforeEach
    void setUp() {
        var properties = new ApiRequestProperties(1, 1000, 10, 1, Duration.ofMillis(1),
                Duration.ofMillis(1), Duration.ofSeconds(10), 5, Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        service = new NhlApiService(new ApiClient(builder.build(), new RequestThrottle(properties),
                properties, IngestionMetrics.standalone()), WEB_URL, STATS_URL);
    }

    @AfterEach
    void verifyRequests() {
        server.verify();
    }

    @Test
    void seasons_useStatsHostAndMapDates() {
        respond(STATS_URL + "/stats/rest/en/season", """
                {"data":[{"id":20242025,"startDate":"2024-10-04T00:00:00",
                  "regularSeasonEndDate":"2025-04-17T00:00:00"}],"total":1}
                """);

        assertThat(service.getSeasons()).singleElement().satisfies(season -> {
            assertThat(season.getId()).isEqualTo("20242025");
            assertThat(season.getStartDate()).isEqualTo(LocalDateTime.parse("2024-10-04T00:00:00"));
            assertThat(season.getRegularSeasonEndDate()).isEqualTo(LocalDateTime.parse("2025-04-17T00:00:00"));
        });
    }

    @Test
    void playerStandingsOrder_requestsEveryPlayerForSeasonAndGameType() {
        respond(WEB_URL + "/v1/skater-stats-leaders/20252026/2?categories=points&limit=-1", """
                {"points":[{"id":8478402,"value":42}]}
                """);

        assertThat(service.getPlayerStandingsOrder("20252026", 2)).singleElement().satisfies(standing -> {
            assertThat(standing.getId()).isEqualTo(8478402L);
            assertThat(standing.getPoints()).isEqualTo(42);
        });
    }

    @Test
    void playerInfo_mapsLocalizedNamesAndProfile() {
        respond(WEB_URL + "/v1/player/8478402/landing", """
                {"playerId":8478402,"isActive":true,"headshot":"h.png","teamLogo":"t.svg",
                 "firstName":{"default":"Connor"},"lastName":{"default":"McDavid"},
                 "currentTeamAbbrev":"EDM","position":"C"}
                """);

        var info = service.getPlayerInfo(8478402L);

        assertThat(info.getPlayerId()).isEqualTo(8478402L);
        assertThat(info.isActive()).isTrue();
        assertThat(info.getFirstName().getName()).isEqualTo("Connor");
        assertThat(info.getLastName().getName()).isEqualTo("McDavid");
        assertThat(info.getHeadshotUrl()).isEqualTo("h.png");
        assertThat(info.getTeamLogoUrl()).isEqualTo("t.svg");
        assertThat(info.getCurrentTeamAbbrev()).isEqualTo("EDM");
    }

    @Test
    void playerGameLogs_requestSeasonAndGameType() {
        respond(WEB_URL + "/v1/player/8478402/game-log/20252026/2", """
                {"gameLog":[{"gameId":2025020001,"gameDate":"2025-10-08","opponentAbbrev":"CGY",
                  "homeRoadFlag":"H","goals":1,"assists":2,"points":3,"toi":"21:30"}]}
                """);

        assertThat(service.getPlayerGameLogs(8478402L, "20252026", 2)).singleElement().satisfies(log -> {
            assertThat(log.getGameId()).isEqualTo(2025020001L);
            assertThat(log.getPoints()).isEqualTo(3);
            assertThat(log.getToi()).isEqualTo("21:30");
        });
    }

    @Test
    void teamSchedule_requestsTeamAndSeason() {
        respond(WEB_URL + "/v1/club-schedule-season/EDM/20252026", """
                {"games":[{"id":2025020001,"gameType":2,"gameState":"OFF",
                  "startTimeUTC":"2025-10-09T02:00:00Z","gameDate":"2025-10-08",
                  "awayTeam":{"abbrev":"CGY","score":1},"homeTeam":{"abbrev":"EDM","score":3}}]}
                """);

        assertThat(service.getTeamSchedule("EDM", "20252026")).singleElement().satisfies(game -> {
            assertThat(game.getGameState()).isEqualTo(GameState.OFF);
            assertThat(game.getHomeTeam().getScore()).isEqualTo(3);
        });
    }

    @Test
    void teamStandings_withoutDate_requestsNow() {
        respond(WEB_URL + "/v1/standings/now", """
                {"standings":[{"teamAbbrev":{"default":"EDM"},"points":10}]}
                """);

        assertThat(service.getTeamStandings()).singleElement()
                .satisfies(team -> assertThat(team.getTeamAbbrev().getDefaultValue()).isEqualTo("EDM"));
    }

    @Test
    void teamStandings_withDate_requestsThatDatesFinalStandings() {
        respond(WEB_URL + "/v1/standings/2025-04-17", """
                {"standings":[{"teamAbbrev":{"default":"EDM"}}]}
                """);

        assertThat(service.getTeamStandings("2025-04-17")).hasSize(1);
    }

    @Test
    void leagueSchedule_flattensGameWeeksAndToleratesMissingGames() {
        respond(WEB_URL + "/v1/schedule/now", """
                {"gameWeek":[{"games":[{"id":1},{"id":2}]},{}]}
                """);

        assertThat(service.getLeagueSchedule()).hasSize(2);
    }

    @Test
    void scores_mapGamesWithGoalsInOrder() {
        respond(WEB_URL + "/v1/score/now", """
                {"prevDate":"2026-10-08","currentDate":"2026-10-09","games":[{
                  "id":2026020066,"season":20262027,"gameType":2,"gameDate":"2026-10-09",
                  "startTimeUTC":"2026-10-09T23:00:00Z","gameState":"OFF",
                  "awayTeam":{"abbrev":"SEA","score":6},"homeTeam":{"abbrev":"DET","score":3},
                  "gameOutcome":{"lastPeriodType":"REG"},
                  "goals":[{"period":1,"periodDescriptor":{"number":1,"periodType":"REG"},
                    "timeInPeriod":"09:19","playerId":8478042,"name":{"default":"V. Arvidsson"},
                    "teamAbbrev":"DET","awayScore":0,"homeScore":1,"strength":"pp",
                    "assists":[{"playerId":8482078,"name":{"default":"L. Raymond"}},
                               {"playerId":8481542,"name":{"default":"M. Seider"}}]}]}]}
                """);

        var scores = service.getScores("now");

        assertThat(scores.getPrevDate()).isEqualTo("2026-10-08");
        assertThat(scores.getCurrentDate()).isEqualTo("2026-10-09");
        assertThat(scores.getGames()).singleElement().satisfies(game -> {
            assertThat(game.getId()).isEqualTo(2026020066L);
            assertThat(game.getGameState()).isEqualTo(GameState.OFF);
            assertThat(game.getAwayTeam().getScore()).isEqualTo(6);
            assertThat(game.getGoals()).singleElement().satisfies(goal -> {
                assertThat(goal.getName().getName()).isEqualTo("V. Arvidsson");
                assertThat(goal.getPeriodDescriptor().getPeriodType()).isEqualTo("REG");
                assertThat(goal.getAssists()).extracting(assist -> assist.getPlayerId())
                        .containsExactly(8482078L, 8481542L);
            });
        });
    }

    private void respond(String url, String json) {
        server.expect(requestTo(url)).andRespond(withSuccess(json, MediaType.APPLICATION_JSON));
    }
}
