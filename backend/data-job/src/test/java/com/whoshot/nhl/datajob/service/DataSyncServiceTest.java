package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DataSyncServiceTest {
    private final SeasonDto previous = season("20242025", "2024-10-04T13:00:00", "2025-04-17T21:30:00");
    private final SeasonDto current = season("20252026", "2025-10-07T17:00:00", "2026-04-17T00:00:00");
    private final SeasonDto upcoming = season("20262027", "2026-09-29T00:00:00", "2027-04-10T00:00:00");

    @Test
    void offseasonKeepsMostRecentStartedSeasonRegardlessOfApiOrder() {
        var now = LocalDateTime.parse("2026-09-07T18:05:00");
        assertSame(current, DataSyncService.selectSeason(List.of(previous, current, upcoming), now));
        assertSame(current, DataSyncService.selectSeason(List.of(upcoming, current, previous), now));
    }

    @Test
    void selectsSeasonDuringRegularSeasonAndPlayoffs() {
        for (String date : List.of("2026-01-01T00:00:00", "2026-04-17T00:00:00", "2026-06-01T00:00:00")) {
            assertSame(current, DataSyncService.selectSeason(
                    List.of(upcoming, previous, current), LocalDateTime.parse(date)));
        }
    }

    @Test
    void switchesAtSeasonStartInclusively() {
        var seasons = List.of(current, upcoming);
        assertSame(current, DataSyncService.selectSeason(seasons, upcoming.getStartDate().minusSeconds(1)));
        assertSame(upcoming, DataSyncService.selectSeason(seasons, upcoming.getStartDate()));
    }

    @Test
    void failsClearlyWhenNoSeasonHasStarted() {
        var now = LocalDateTime.parse("2026-09-07T18:05:00");
        assertThrows(IllegalStateException.class, () -> DataSyncService.selectSeason(List.of(), now));
        assertThrows(IllegalStateException.class, () -> DataSyncService.selectSeason(List.of(upcoming), now));
    }

    @Test
    void ignoresEntriesWithoutStartDates() {
        assertSame(current, DataSyncService.selectSeason(
                List.of(new SeasonDto(), current), LocalDateTime.parse("2026-09-07T18:05:00")));
    }

    private static SeasonDto season(String id, String start, String end) {
        var season = new SeasonDto();
        season.setId(id);
        season.setStartDate(LocalDateTime.parse(start));
        season.setRegularSeasonEndDate(LocalDateTime.parse(end));
        return season;
    }
}
