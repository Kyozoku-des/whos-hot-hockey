package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.DataIngestionJob;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base class for backfill integration tests.
 * <p>
 * Boots a single disposable PostgreSQL 16 container, shared across all subclasses for the whole
 * test JVM (the Testcontainers "singleton container" pattern — a plain static field started once,
 * <em>not</em> annotated with {@code @Container}/{@code @Testcontainers}, so JUnit does not stop it
 * between test classes and Spring's test-context cache can safely reuse one Spring context for
 * every subclass). A shutdown hook stops it when the JVM exits.
 * <p>
 * These tests never touch a developer's real database (see the known problem with
 * {@code NhlApiServiceTest} booting the full context against dev Postgres). Every table this
 * feature writes is truncated before each test method so container reuse cannot leak state between
 * tests.
 */
@Tag("integration")
@SpringBootTest(classes = DataIngestionJob.class)
@ActiveProfiles("test")
public abstract class PostgresIntegrationTestBase {

    @ServiceConnection
    static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>(DockerImageName.parse("postgres:16"));

    static {
        POSTGRES.start();
        Runtime.getRuntime().addShutdownHook(new Thread(POSTGRES::stop));
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void resetDatabase() {
        jdbcTemplate.execute(
                "TRUNCATE TABLE players, teams, game_logs, team_games, current_season RESTART IDENTITY");
    }
}
