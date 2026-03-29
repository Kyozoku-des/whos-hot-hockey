package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link InitialDataLoadService}.
 */
@ExtendWith(MockitoExtension.class)
class InitialDataLoadServiceTest {

    @Mock
    private DataSyncService dataSyncService;

    @InjectMocks
    private InitialDataLoadService initialDataLoadService;

    @Test
    void loadFullSeason_callsSyncPlayers() throws PlayerStatisticsException {
        initialDataLoadService.loadFullSeason();

        verify(dataSyncService).syncPlayers();
    }

    @Test
    void loadFullSeason_handlesApiFailureGracefully() throws PlayerStatisticsException {
        doThrow(new PlayerStatisticsException("API failure"))
                .when(dataSyncService).syncPlayers();

        assertDoesNotThrow(() -> initialDataLoadService.loadFullSeason());
    }
}
