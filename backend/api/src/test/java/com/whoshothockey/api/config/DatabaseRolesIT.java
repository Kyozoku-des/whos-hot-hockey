package com.whoshothockey.api.config;

import com.whoshothockey.api.ApiApplication;
import com.whoshothockey.api.service.TeamService;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import static org.assertj.core.api.Assertions.*;

@Tag("integration")
@Testcontainers
class DatabaseRolesIT {
    // Synthetic passwords exercise SQL quoting; these are never deployment secrets.
    private static final String ADMIN = "fixture-admin-only-password";
    private static final String API = "fixture-api-'quoted'-$-password";
    private static final String INGEST = "fixture-ingest-'quoted'-$-password";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17")
            .withUsername("whoshot").withPassword(ADMIN)
            .withCommand("postgres", "-c", "log_statement=all", "-c", "log_min_duration_statement=0")
            .withEnv("PGPASSWORD", ADMIN).withEnv("API_DB_PASSWORD", API)
            .withEnv("INGEST_DB_PASSWORD", INGEST).withEnv("APP_PROFILE", "prod");

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void provisionsLeastPrivilegeOnFreshAndExistingDatabases(boolean upgrade) throws Exception {
        String database = upgrade ? "review_upgrade" : "review_fresh";
        try (Connection admin = POSTGRES.createConnection("")) {
            admin.createStatement().execute("CREATE DATABASE " + database);
        }
        String url = "jdbc:postgresql://" + POSTGRES.getHost() + ":" + POSTGRES.getMappedPort(5432) + "/" + database;
        if (upgrade) {
            Flyway.configure().dataSource(url, "whoshot", ADMIN).target("3").load().migrate();
            execute(url, "whoshot", ADMIN,
                    "INSERT INTO current_season(season_id, season_display_name, is_active) VALUES ('20252026','Existing data',true)");
        }

        // Exercise the actual one-shot application, not a test-only migration path.
        ConfigurableApplicationContext migration = run(url, "whoshot", ADMIN, "migrate", true);
        assertThat(migration.isActive()).isFalse();
        POSTGRES.copyFileToContainer(MountableFile.forHostPath(
                Path.of("../../infra/database/roles.sql").toAbsolutePath().normalize()), "/roles.sql");
        provision(database);
        provision(database); // Retries must preserve data and grants.

        if (upgrade) {
            assertThat(scalar(url, "whoshot_api", API, "SELECT count(*) FROM current_season WHERE season_display_name='Existing data'"))
                    .isEqualTo(1);
        }
        execute(url, "whoshot_ingest", INGEST,
                "INSERT INTO current_season(season_id, season_display_name) VALUES ('20262027','Runtime write')");
        assertThat(scalar(url, "whoshot_api", API, "SELECT count(*) FROM current_season WHERE season_id='20262027'"))
                .isEqualTo(1);
        for (String sql : new String[]{"UPDATE current_season SET season_display_name='bad'",
                "DELETE FROM current_season", "CREATE TABLE forbidden(id int)",
                "CREATE TEMP TABLE forbidden(id int)", "DROP TABLE current_season", "SET ROLE whoshot"}) {
            denied(url, "whoshot_api", API, sql);
        }
        for (String sql : new String[]{"CREATE TABLE forbidden(id int)", "DROP TABLE current_season",
                "DELETE FROM flyway_schema_history", "SET ROLE whoshot"}) {
            denied(url, "whoshot_ingest", INGEST, sql);
        }
        assertThat(scalar(url, "whoshot", ADMIN,
                "SELECT count(*) FROM pg_roles WHERE rolname IN ('whoshot_api','whoshot_ingest') " +
                        "AND (rolsuper OR rolcreatedb OR rolcreaterole OR rolreplication OR rolbypassrls)"))
                .isZero();

        // Future migrations inherit grants; old administrative credentials still work for rollback.
        execute(url, "whoshot", ADMIN, "CREATE TABLE future_data(id BIGSERIAL PRIMARY KEY, value text)");
        execute(url, "whoshot_ingest", INGEST, "INSERT INTO future_data(value) VALUES ('future fixture')");
        assertThat(scalar(url, "whoshot_api", API, "SELECT count(*) FROM future_data")).isEqualTo(1);
        try (ConfigurableApplicationContext api = run(url, "whoshot_api", API, "test", false)) {
            assertThat(api.getBean(TeamService.class).getTeamStandings(null)).isEmpty();
        }
        var invalid = POSTGRES.execInContainer("env", "API_DB_PASSWORD=whoshot_api_local",
                "psql", "-X", "-U", "whoshot", "-d", database, "-f", "/roles.sql");
        assertThat(invalid.getExitCode()).isNotZero();
        // Refusing a default password must leave the existing role password intact.
        assertThat(scalar(url, "whoshot_api", API, "SELECT count(*) FROM future_data")).isEqualTo(1);
    }

    private void provision(String database) throws Exception {
        var result = POSTGRES.execInContainer("psql", "-X", "-U", "whoshot", "-d", database, "-f", "/roles.sql");
        assertThat(result.getExitCode()).withFailMessage(result.getStderr()).isZero();
        assertThat(result.getStdout() + result.getStderr()).doesNotContain(API, INGEST, ADMIN);
        assertThat(POSTGRES.getLogs()).doesNotContain(API, INGEST, ADMIN);
    }

    private ConfigurableApplicationContext run(String url, String user, String password, String profile, boolean migrate) {
        return new SpringApplication(ApiApplication.class).run(
                "--spring.profiles.active=" + profile, "--spring.main.web-application-type=none",
                "--spring.datasource.url=" + url, "--spring.datasource.username=" + user,
                "--spring.datasource.password=" + password, "--spring.flyway.enabled=" + migrate,
                "--spring.datasource.hikari.read-only=" + !migrate, "--logging.config=classpath:logback-console.xml");
    }

    private void execute(String url, String user, String password, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            connection.createStatement().execute(sql);
        }
    }

    private int scalar(String url, String user, String password, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password);
             var statement = connection.createStatement(); var result = statement.executeQuery(sql)) {
            result.next();
            return result.getInt(1);
        }
    }

    private void denied(String url, String user, String password, String sql) throws SQLException {
        try (Connection connection = DriverManager.getConnection(url, user, password)) {
            // Prove database grants protect us even if a client disables its read-only default.
            connection.createStatement().execute("SET default_transaction_read_only=off");
            assertThatThrownBy(() -> connection.createStatement().execute(sql))
                    .isInstanceOfSatisfying(SQLException.class, error -> assertThat(error.getSQLState()).isEqualTo("42501"));
        }
    }
}
