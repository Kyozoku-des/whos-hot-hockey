package com.whoshot.nhl.api.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SeasonPaceTest {

    @Test
    void regularSeasonGames_is84FromThe2025To2026Season() {
        assertThat(SeasonPace.regularSeasonGames("20252026")).isEqualTo(84);
        assertThat(SeasonPace.regularSeasonGames("20262027")).isEqualTo(84);
    }

    @Test
    void regularSeasonGames_coversShortenedAndStandardSeasons() {
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
    void projectedPoints_beforeTheFirstGame_isNull() {
        assertThat(SeasonPace.projectedPoints(0, 0, 84)).isNull();
        assertThat(SeasonPace.projectedPoints(null, null, 84)).isNull();
    }
}
