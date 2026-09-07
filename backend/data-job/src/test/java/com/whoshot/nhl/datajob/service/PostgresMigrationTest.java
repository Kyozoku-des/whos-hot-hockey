package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.Player;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/** Opt-in PostgreSQL check; Flyway persists the schema, test records are rolled back. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("test")
@EnabledIfEnvironmentVariable(named = "RUN_POSTGRES_TESTS", matches = "true")
class PostgresMigrationTest {
    @MockitoBean
    private DataSyncService dataSyncService;

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManager entityManager;

    @Test
    void migrationIsValidAndIsNotReapplied() {
        flyway.validate();
        assertEquals("1", flyway.info().current().getVersion().toString());
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    @Transactional
    void supportsGeneratedAndCompositeIdentifiers() {
        var season = new CurrentSeason();
        season.setSeasonId("migration-test");
        entityManager.persist(season);

        var player = new Player();
        player.setId(new Player.PlayerId(-1L, "19001901"));
        player.setFirstName("Migration");
        player.setLastName("Test");
        player.setNextGame(new Player.NextGame("1900-10-01", "EDM", "H"));
        entityManager.persist(player);
        entityManager.flush();
        entityManager.clear();

        assertNotNull(season.getId());
        assertEquals("migration-test", entityManager.find(CurrentSeason.class, season.getId()).getSeasonId());
        assertEquals("EDM", entityManager.find(Player.class, player.getId()).getNextGame().opponentAbbrev());
    }
}
