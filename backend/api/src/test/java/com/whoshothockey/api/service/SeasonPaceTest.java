package com.whoshothockey.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonPaceTest {

    @Test
    void regularSeasonGames_is84FromThe2026To2027Season() {
        assertThat(SeasonPace.regularSeasonGames("20262027")).isEqualTo(84);
        assertThat(SeasonPace.regularSeasonGames("20272028")).isEqualTo(84);
    }

    @Test
    void regularSeasonGames_coversShortenedAndStandardSeasons() {
        assertThat(SeasonPace.regularSeasonGames("20252026")).isEqualTo(82);
        assertThat(SeasonPace.regularSeasonGames("20242025")).isEqualTo(82);
        assertThat(SeasonPace.regularSeasonGames("20202021")).isEqualTo(56);
        assertThat(SeasonPace.regularSeasonGames("20122013")).isEqualTo(48);
    }

    @Test
    void regularSeasonGames_withoutSeason_defaultsTo82() {
        assertThat(SeasonPace.regularSeasonGames(null)).isEqualTo(82);
        assertThat(SeasonPace.regularSeasonGames("x")).isEqualTo(82);
    }

    @Test
    void projectedPoints_extendsThePointsPerGameRateToTheFullSeason() {
        assertThat(SeasonPace.projectedPoints(30, 20, 84)).isEqualTo(126);
        assertThat(SeasonPace.projectedPoints(10, 3, 84)).isEqualTo(280);
    }

    @Test
    void projectedPoints_afterACompleted2025To2026Season_isTheActualTotal() {
        int seasonGames = SeasonPace.regularSeasonGames("20252026");
        assertThat(SeasonPace.projectedPoints(100, 82, seasonGames)).isEqualTo(100);
    }

    @Test
    void projectedPoints_beforeTheFirstGame_isNull() {
        assertThat(SeasonPace.projectedPoints(0, 0, 84)).isNull();
        assertThat(SeasonPace.projectedPoints(null, null, 84)).isNull();
    }

    @Test
    void projectedPointsAtRate_addsTheRateTimesTheGamesLeft() {
        assertThat(SeasonPace.projectedPointsAtRate(20, 1.2, 50)).isEqualTo(80);
        assertThat(SeasonPace.projectedPointsAtRate(30, 1.25, 63)).isEqualTo(109);
    }

    @Test
    void projectedPointsAtRate_withNoGamesLeft_isThePointsSoFar() {
        assertThat(SeasonPace.projectedPointsAtRate(100, 2.0, 0)).isEqualTo(100);
        assertThat(SeasonPace.projectedPointsAtRate(100, 2.0, -3)).isEqualTo(100);
    }

    @Test
    void projectedPointsAtRate_withoutPointsOrRate_isNull() {
        assertThat(SeasonPace.projectedPointsAtRate(null, 1.0, 10)).isNull();
        assertThat(SeasonPace.projectedPointsAtRate(10, null, 10)).isNull();
    }
}
