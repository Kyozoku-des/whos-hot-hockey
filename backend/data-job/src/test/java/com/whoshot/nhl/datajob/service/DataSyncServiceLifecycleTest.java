package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Regression coverage for FR-005: merely constructing {@link DataSyncService} (as happens whenever
 * any process, including a backfill, boots a Spring context containing it) must not resolve or
 * persist the active season. That side effect must only happen when the live-sync startup path
 * explicitly asks for it.
 */
@ExtendWith(MockitoExtension.class)
class DataSyncServiceLifecycleTest {

    @Mock
    private NhlApiService nhlApiService;
    @Mock
    private PlayerRepository playerRepository;
    @Mock
    private PlayerFactory playerFactory;
    @Mock
    private SeasonDataWriter seasonDataWriter;
    @Mock
    private TeamGameRepository teamGameRepository;
    @Mock
    private GameLogWriter gameLogWriter;

    @Test
    void constructingDataSyncService_performsNoApiCallsOrWrites() {
        new DataSyncService(nhlApiService, playerRepository, playerFactory, seasonDataWriter,
                teamGameRepository, gameLogWriter, FetchPipeline.sequential(),
                new PlayerInfoCache(java.time.Duration.ofMinutes(30), 100));

        verifyNoInteractions(nhlApiService, playerRepository, playerFactory, seasonDataWriter,
                teamGameRepository, gameLogWriter);
    }
}
