package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.backfill.PostgresIntegrationTestBase;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.Player;
import jakarta.persistence.EntityManager;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void allMigrationsAppliedAndNoneAreReapplied() {
        flyway.validate();
        assertEquals(0, flyway.info().pending().length);
        assertEquals(0, flyway.migrate().migrationsExecuted);
    }

    @Test
    void upgradesPopulatedV1WithoutLosingRetainedData() {
        var dataSource = jdbcTemplate.getDataSource();
        Flyway.configure().dataSource(dataSource).defaultSchema("upgrade_test")
                .locations("classpath:db/migration").target("1").load().migrate();
        jdbcTemplate.execute("""
                INSERT INTO upgrade_test.players
                    (player_id, season, first_name, last_name, points, penalty_minutes, shots, hot)
                VALUES (1, '20242025', 'Test', 'Player', 42, 10, 100, true)
                """);
        jdbcTemplate.execute("""
                INSERT INTO upgrade_test.game_logs (player_id, game_id, game_date, shots)
                VALUES (1, 2024020001, '2024-10-04', 5)
                """);
        jdbcTemplate.execute("""
                INSERT INTO upgrade_test.teams (team_code, season, team_name, last_updated)
                VALUES ('EDM', '20242025', 'Oilers', '2024-10-04T12:30:00')
                """);
        jdbcTemplate.execute("""
                INSERT INTO upgrade_test.current_season (season_id, last_updated)
                VALUES ('20242025', '')
                """);

        Flyway upgrade = Flyway.configure().dataSource(dataSource).defaultSchema("upgrade_test")
                .locations("classpath:db/migration").load();
        assertEquals(4, upgrade.migrate().migrationsExecuted);
        upgrade.validate();
        assertEquals(0, upgrade.migrate().migrationsExecuted);

        assertEquals(42, jdbcTemplate.queryForObject(
                "SELECT points FROM upgrade_test.players WHERE player_id = 1 AND season_id = '20242025'",
                Integer.class));
        assertEquals(5, jdbcTemplate.queryForObject(
                "SELECT shots FROM upgrade_test.game_logs WHERE player_id = 1", Integer.class));
        assertEquals(LocalDateTime.parse("2024-10-04T12:30:00"), jdbcTemplate.queryForObject(
                "SELECT last_updated FROM upgrade_test.teams WHERE team_code = 'EDM' AND season_id = '20242025'",
                LocalDateTime.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT last_updated FROM upgrade_test.current_season WHERE season_id = '20242025'",
                LocalDateTime.class));
        assertEquals(0, jdbcTemplate.queryForObject("""
                SELECT count(*) FROM information_schema.columns
                WHERE table_schema = 'upgrade_test' AND table_name = 'players'
                  AND column_name IN ('season', 'penalty_minutes', 'shots', 'shooting_percentage', 'hot')
                """, Integer.class));
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
