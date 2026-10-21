package com.whoshothockey.datajob.service;

import com.whoshothockey.datajob.backfill.PostgresIntegrationTestBase;
import com.whoshothockey.domain.entity.SyncStatus;
import com.whoshothockey.domain.repository.SyncStatusRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SyncStatusRecorder} against PostgreSQL: one row per sync, moved forward by each finished sync.
 */
class SyncStatusRecorderIT extends PostgresIntegrationTestBase {

    @Autowired
    private SyncStatusRecorder recorder;

    @Autowired
    private SyncStatusRepository repository;

    @Test
    void statsSynced_storesAndAdvancesTheLastSuccess() {
        Instant before = Instant.now().minusSeconds(1);
        recorder.statsSynced();
        Instant first = repository.findById(SyncStatus.STATS).orElseThrow().getLastSuccessAt();
        assertThat(first).isAfter(before);

        recorder.statsSynced();
        assertThat(repository.findAll()).singleElement()
                .satisfies(status -> assertThat(status.getLastSuccessAt()).isAfterOrEqualTo(first));
    }
}
