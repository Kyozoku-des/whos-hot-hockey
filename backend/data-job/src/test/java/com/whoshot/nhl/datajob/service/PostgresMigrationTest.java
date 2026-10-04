package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.backfill.PostgresIntegrationTestBase;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.Player;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Flyway applies every migration to a fresh PostgreSQL, and the entities map onto the result
 * (Hibernate validates the schema when the context starts).
 */
class PostgresMigrationTest extends PostgresIntegrationTestBase {

    @Autowired
    private Flyway flyway;

    @Autowired
    private EntityManager entityManager;

    @Test
    void allMigrationsAppliedAndNoneAreReapplied() {
        flyway.validate();
        assertEquals(0, flyway.info().pending().length);
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
        entityManager.persist(player);
        entityManager.flush();
        entityManager.clear();

        assertNotNull(season.getId());
        assertEquals("migration-test", entityManager.find(CurrentSeason.class, season.getId()).getSeasonId());
        assertEquals("Migration", entityManager.find(Player.class, player.getId()).getFirstName());
    }
}
