package com.whoshot.nhl.datajob.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Metric labels stay low-cardinality and run counts land where dashboards expect them (issue #29).
 */
class IngestionMetricsTest {

    @ParameterizedTest
    @CsvSource({
            "https://api-web.nhle.com/v1/player/8478402/landing, player-landing",
            "https://api-web.nhle.com/v1/player/8478402/game-log/20252026/2, player-game-log",
            "https://api-web.nhle.com/v1/club-schedule-season/EDM/20252026, team-schedule",
            "https://api-web.nhle.com/v1/skater-stats-leaders/20252026/2?categories=points&limit=-1, player-standings",
            "https://api-web.nhle.com/v1/standings/now, team-standings",
            "https://api-web.nhle.com/v1/schedule/now, league-schedule",
            "https://api.nhle.com/stats/rest/en/season, seasons",
            "https://api-web.nhle.com/v1/unknown, other"
    })
    void urlsMapToFixedEndpointCategories_withoutIds(String url, String endpoint) {
        assertThat(IngestionMetrics.endpoint(url)).isEqualTo(endpoint);
    }

    @Test
    void finishedRun_publishesRecordCountsAndFreshness() {
        IngestionMetrics metrics = IngestionMetrics.standalone();
        metrics.recordRequest("https://api-web.nhle.com/v1/player/1/landing", "success", null, Duration.ofMillis(5));

        metrics.startRun("players-full").finish(3, 1, 0, 0);

        var registry = metrics.registry();
        assertThat(registry.get("ingestion.records").tag("mode", "players-full").tag("result", "written")
                .counter().count()).isEqualTo(3);
        assertThat(registry.get("ingestion.last.success").tag("mode", "players-full").gauge().value()).isPositive();
        assertThat(registry.get("nhl.api.requests").tag("endpoint", "player-landing").tag("status", "2xx")
                .timer().count()).isEqualTo(1);
    }

    @Test
    void runWithSkippedRecords_doesNotAdvanceFreshness() {
        IngestionMetrics metrics = IngestionMetrics.standalone();

        metrics.startRun("players-full").finish(3, 0, 1, 0);

        assertThat(metrics.registry().get("ingestion.last.success").tag("mode", "players-full").gauge().value())
                .isZero();
    }
}
