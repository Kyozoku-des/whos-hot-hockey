package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link InitialDataLoadService}.
 */
@ExtendWith(MockitoExtension.class)
class InitialDataLoadServiceTest {

    @Mock
    private DataSyncService dataSyncService;

    private InitialDataLoadService initialDataLoadService;

    @BeforeEach
    void setUp() {
        when(dataSyncService.getSeasonId()).thenReturn("20252026");
        initialDataLoadService = new InitialDataLoadService(dataSyncService, TestSeasonLocks.available());
    }

    @Test
    void loadFullSeason_callsSyncPlayers() throws PlayerStatisticsException {
        initialDataLoadService.loadFullSeason();

        var order = org.mockito.Mockito.inOrder(dataSyncService);
        order.verify(dataSyncService).initialize();
        order.verify(dataSyncService).syncTeams();
        order.verify(dataSyncService).syncPlayers();
    }

    @Test
    void loadFullSeason_handlesApiFailureGracefully() throws PlayerStatisticsException {
        doThrow(new PlayerStatisticsException("API failure"))
                .when(dataSyncService).syncPlayers();

        assertDoesNotThrow(() -> initialDataLoadService.loadFullSeason());
    }

    @Test
    void loadFullSeason_writesNothingWhileAnotherJobWritesTheSeason() throws PlayerStatisticsException {
        initialDataLoadService = new InitialDataLoadService(dataSyncService, TestSeasonLocks.heldElsewhere());

        initialDataLoadService.loadFullSeason();

        verify(dataSyncService, never()).syncTeams();
        verify(dataSyncService, never()).syncPlayers();
    }
}
