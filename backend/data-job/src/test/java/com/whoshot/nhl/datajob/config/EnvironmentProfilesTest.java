package com.whoshot.nhl.datajob.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.config.ConfigDataEnvironmentPostProcessor;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.mock.env.MockEnvironment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises this application's real config without starting JPA or the NHL scheduler. */
class EnvironmentProfilesTest {
    @TempDir
    Path temporaryDirectory;

    @Test
    void localLoadsEnvFileAndBoundsSeparateLogArchives() throws Exception {
        Path envFile = temporaryDirectory.resolve(".env");
        Files.writeString(envFile, "DB_USER=local-user\nDB_PASSWORD=from-file\n"
                + "DB_URL=jdbc:postgresql://localhost:15432/local\n"
                + "LOG_DIRECTORY=custom-logs\nSERVER_PORT=18080\n");
        var environment = environment("local", envFile);
        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-process-environment", Map.of("DB_PASSWORD", "from-process")));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        assertEquals("local-user", environment.getProperty("spring.datasource.username"));
        assertEquals("from-process", environment.getProperty("spring.datasource.password"));
        assertEquals("jdbc:postgresql://localhost:15432/local", environment.getProperty("spring.datasource.url"));
        assertEquals("custom-logs/data-job.log", environment.getProperty("logging.file.name"));
        assertEquals("10MB", environment.getProperty("logging.logback.rollingpolicy.max-file-size"));
        assertEquals("14", environment.getProperty("logging.logback.rollingpolicy.max-history"));
        assertEquals("200MB", environment.getProperty("logging.logback.rollingpolicy.total-size-cap"));
    }

    @Test
    void localDefaultsWorkWithoutEnvFile() {
        var environment = environment("local", temporaryDirectory.resolve("missing.env"));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        assertEquals("whoshot", environment.getProperty("spring.datasource.username"));
        assertEquals("./logs/data-job.log", environment.getProperty("logging.file.name"));
    }

    @Test
    void noActiveProfileDefaultsToLocal() {
        var environment = environment("", temporaryDirectory.resolve("missing.env"));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        assertArrayEquals(new String[] {"local"}, environment.getDefaultProfiles());
        assertEquals("./logs/data-job.log", environment.getProperty("logging.file.name"));
    }

    @Test
    void prodIgnoresLocalEnvAndRequiresExplicitDatabaseSettings() throws Exception {
        Path envFile = temporaryDirectory.resolve(".env");
        Files.writeString(envFile, "DB_PASSWORD=must-not-load\n");
        var environment = environment("prod", envFile);
        ConfigDataEnvironmentPostProcessor.applyTo(environment);

        for (String property : new String[] {"url", "username", "password"}) {
            assertThrows(IllegalArgumentException.class,
                    () -> environment.getProperty("spring.datasource." + property));
        }
        assertNull(environment.getProperty("logging.file.name"));
        assertEquals("classpath:logback-console.xml", environment.getProperty("logging.config"));
        assertEquals("false", environment.getProperty("spring.jpa.show-sql"));

        environment.getPropertySources().addFirst(new SystemEnvironmentPropertySource(
                "test-process-environment", Map.of("DB_URL", "jdbc:postgresql://prod/nhl",
                "DB_USER", "prod-user", "DB_PASSWORD", "prod-password")));
        assertEquals("jdbc:postgresql://prod/nhl", environment.getProperty("spring.datasource.url"));
        assertEquals("prod-user", environment.getProperty("spring.datasource.username"));
        assertEquals("prod-password", environment.getProperty("spring.datasource.password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"local,backfill", "local,initial-load", "prod,backfill", "prod,initial-load"})
    void jobModesComposeWithEnvironmentProfiles(String profiles) {
        var environment = environment(profiles, temporaryDirectory.resolve("missing.env"));
        ConfigDataEnvironmentPostProcessor.applyTo(environment);
        assertArrayEquals(profiles.split(","), environment.getActiveProfiles());
        assertEquals("none", environment.getProperty("spring.main.web-application-type"));
        assertEquals(profiles.startsWith("local") ? "./logs/data-job.log" : null,
                environment.getProperty("logging.file.name"));
    }

    private MockEnvironment environment(String profiles, Path envFile) {
        // Load only this module's production resources, independently of test defaults.
        String location = Path.of("src", "main", "resources").toAbsolutePath().toUri().toString();
        return new MockEnvironment()
                .withProperty("spring.config.location", location)
                .withProperty("spring.profiles.active", profiles)
                .withProperty("BACKEND_ENV_FILE", envFile.toAbsolutePath().toString().replace('\\', '/'));
    }
}
