package com.whoshot.nhl.api.config;

import org.flywaydb.core.Flyway;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** One-shot schema migration; never runs in the serving API profile. */
@Configuration
@Profile("migrate")
public class MigrationConfiguration {
    @Bean
    ApplicationRunner exitAfterMigration(Flyway flyway, ConfigurableApplicationContext context) {
        return args -> {
            // The auto-configured Flyway initializer has migrated before runners
            // execute. A migration/validation failure must exit nonzero.
            flyway.validate();
            context.close();
        };
    }
}
