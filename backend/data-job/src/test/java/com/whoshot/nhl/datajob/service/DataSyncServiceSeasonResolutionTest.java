package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DataSyncService}'s off-season fallback in setSeason().
 *
 * Regression coverage for a bug where the off-season fallback picked the newest
 * season record (which may not have started yet and has no game data) instead of
 * the most recently completed season, and for a related bug where the previously
 * active {@link CurrentSeason} row was never deactivated, leaving multiple rows
 * marked active and breaking every API endpoint that resolves the default season.
 */
@ExtendWith(MockitoExtension.class)
class DataSyncServiceSeasonResolutionTest {

    @Mock
    private NhlApiService nhlApiService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerFactory playerFactory;
    @Mock
    private TeamRepository teamRepository;
    @Mock
    private CurrentSeasonRepository currentSeasonRepository;

    @InjectMocks
    private DataSyncService dataSyncService;

    private SeasonDto season(String id, LocalDateTime start, LocalDateTime end) {
        SeasonDto dto = new SeasonDto();
        dto.setId(id);
        dto.setStartDate(start);
        dto.setRegularSeasonEndDate(end);
        return dto;
    }

    @Test
    void setSeason_offSeason_picksMostRecentlyCompletedSeason_notNewestRecord() throws Exception {
        // Today falls between the end of last season and the start of next season (off-season).
        SeasonDto completed = season("20252026",
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusYears(1),
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusMonths(1));
        SeasonDto notYetStarted = season("20262027",
                LocalDateTime.now(java.time.ZoneOffset.UTC).plusMonths(1),
                LocalDateTime.now(java.time.ZoneOffset.UTC).plusYears(1));

        when(nhlApiService.getSeasons()).thenReturn(List.of(completed, notYetStarted));
        when(currentSeasonRepository.findAllByIsActiveTrue()).thenReturn(List.of());
        when(currentSeasonRepository.findBySeasonId(any())).thenReturn(Optional.empty());

        invokeSetSeason();

        ArgumentCaptor<CurrentSeason> captor = ArgumentCaptor.forClass(CurrentSeason.class);
        verify(currentSeasonRepository, times(1)).save(captor.capture());
        assertEquals("20252026", captor.getValue().getSeasonId());
    }

    @Test
    void setSeason_deactivatesPreviouslyActiveSeason_whenSeasonChanges() throws Exception {
        SeasonDto completed = season("20252026",
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusYears(1),
                LocalDateTime.now(java.time.ZoneOffset.UTC).minusMonths(1));
        SeasonDto notYetStarted = season("20262027",
                LocalDateTime.now(java.time.ZoneOffset.UTC).plusMonths(1),
                LocalDateTime.now(java.time.ZoneOffset.UTC).plusYears(1));

        CurrentSeason stalePreviouslyActive = new CurrentSeason();
        stalePreviouslyActive.setSeasonId("20262027");
        stalePreviouslyActive.setIsActive(true);

        when(nhlApiService.getSeasons()).thenReturn(List.of(completed, notYetStarted));
        when(currentSeasonRepository.findAllByIsActiveTrue()).thenReturn(List.of(stalePreviouslyActive));
        when(currentSeasonRepository.findBySeasonId(any())).thenReturn(Optional.empty());

        invokeSetSeason();

        // The stale "20262027" row must be flipped to inactive...
        assertEquals(false, stalePreviouslyActive.getIsActive());
        // ...and the resolved "20252026" row must be saved as the new active season,
        // leaving at most one active row for findByIsActiveTrue() to find.
        ArgumentCaptor<CurrentSeason> captor = ArgumentCaptor.forClass(CurrentSeason.class);
        verify(currentSeasonRepository, times(2)).save(captor.capture());
        assertEquals("20252026",
                captor.getAllValues().stream()
                        .filter(cs -> Boolean.TRUE.equals(cs.getIsActive()))
                        .findFirst()
                        .orElseThrow()
                        .getSeasonId());
    }

    private void invokeSetSeason() throws Exception {
        Method setSeason = DataSyncService.class.getDeclaredMethod("setSeason");
        setSeason.setAccessible(true);
        setSeason.invoke(dataSyncService);
    }
}
