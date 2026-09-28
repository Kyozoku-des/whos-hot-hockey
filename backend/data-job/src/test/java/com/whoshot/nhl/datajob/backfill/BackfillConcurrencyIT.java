package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.service.BackfillLockService;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Integration coverage for FR-014 / research R-011: a second backfill of a season that is already
 * running is rejected before touching upstream or the database ({@code BackfillRunner} maps the
 * rejection to exit code 3), and the advisory lock is released both on normal completion and when
 * the holding session's connection dies.
 * <p>
 * The competing run is simulated by a separate raw JDBC session holding the same advisory lock,
 * which is exactly what a second data-job process would look like to Postgres.
 */
class BackfillConcurrencyIT extends PostgresIntegrationTestBase {

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private SeasonBackfillService seasonBackfillService;
    @Autowired
    private BackfillLockService backfillLockService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private GameLogRepository gameLogRepository;
    @Autowired
    private TeamGameRepository teamGameRepository;

    private BackfillRequest request() {
        return BackfillRequest.validate(SEASON_ID, List.of(
                BackfillFixtures.season(SEASON_ID,
                        LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0))));
    }

    @Test
    void secondRun_whileAnotherProcessHoldsTheLock_isRejected_andWritesNothing() throws SQLException {
        try (Connection otherProcess = openSession()) {
            assertTrue(tryAdvisoryLock(otherProcess), "the competing session must win the lock first");

            assertThrows(SeasonBackfillService.AlreadyRunningException.class,
                    () -> seasonBackfillService.run(request(), false));
        }

        verifyNoInteractions(nhlApiService);
        assertEquals(0, playerRepository.count());
        assertEquals(0, teamRepository.count());
        assertEquals(0, gameLogRepository.count());
        assertEquals(0, teamGameRepository.count());
    }

    @Test
    void secondRun_whileThisProcessHoldsTheLock_isRejected_thenSucceedsAfterUnlock() {
        BackfillTestSupport.stubUpstream(nhlApiService);
        assertTrue(backfillLockService.tryLock(SEASON_ID));
        try {
            assertThrows(SeasonBackfillService.AlreadyRunningException.class,
                    () -> seasonBackfillService.run(request(), false));
        } finally {
            backfillLockService.unlock(SEASON_ID);
        }

        assertTrue(seasonBackfillService.run(request(), false).success());
    }

    @Test
    void lockReleases_whenTheHoldingSessionsConnectionCloses() throws SQLException {
        BackfillTestSupport.stubUpstream(nhlApiService);
        Connection crashedRun = openSession();
        assertTrue(tryAdvisoryLock(crashedRun));

        crashedRun.close(); // the process died: Postgres drops its session-level locks

        assertTrue(seasonBackfillService.run(request(), false).success());
    }

    @Test
    void completedRun_releasesTheLock_forOtherSessions() throws SQLException {
        BackfillTestSupport.stubUpstream(nhlApiService);

        // Several runs, so a lock stranded on any pooled connection would surface here.
        for (int i = 0; i < 3; i++) {
            assertTrue(seasonBackfillService.run(request(), false).success());
        }

        try (Connection otherProcess = openSession()) {
            assertTrue(tryAdvisoryLock(otherProcess),
                    "a finished run must not leave its advisory lock held on any connection");
        }
    }

    private static Connection openSession() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static boolean tryAdvisoryLock(Connection connection) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT pg_try_advisory_lock(?, ?)")) {
            statement.setInt(1, BackfillLockService.LOCK_NAMESPACE);
            statement.setInt(2, BackfillLockService.lockKey(SEASON_ID));
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getBoolean(1);
            }
        }
    }
}
