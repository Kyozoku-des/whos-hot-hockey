package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.model.TeamGameIndex;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonDataWriter;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.util.ArrayList;
import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.PLAYER_ID;
import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Measures database round trips for one player write (issue #29) instead of assuming that
 * {@code saveAll} or {@code hibernate.jdbc.batch_size} reduce them.
 */
@TestPropertySource(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class GameLogWriteStatementCountIT extends PostgresIntegrationTestBase {

    private static final int GAMES = 40;

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private SeasonDataWriter seasonDataWriter;
    @Autowired
    private GameLogRepository gameLogRepository;
    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void rewritingAPlayer_doesNotLookUpEachGameSeparately() {
        List<PlayerGameLogDto> gameLogs = gameLogs();
        seasonDataWriter.writePlayer(player(), gameLogs, TeamGameIndex.empty());
        Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        statistics.clear();

        seasonDataWriter.writePlayer(player(), gameLogs, TeamGameIndex.empty());

        long statements = statistics.getPrepareStatementCount();
        // One preload query plus batched updates: well under one statement per game, where the
        // per-game lookup needed a select and an update for every one of the 40 games.
        assertThat(statements).isLessThan(GAMES);
        assertThat(gameLogRepository.findByPlayerIdAndSeasonId(PLAYER_ID, SEASON_ID)).hasSize(GAMES);
        System.out.printf("Rewriting a player with %d game logs: %d prepared statements, %d queries%n",
                GAMES, statements, statistics.getQueryExecutionCount());
    }

    private static List<PlayerGameLogDto> gameLogs() {
        List<PlayerGameLogDto> logs = new ArrayList<>();
        for (int i = GAMES; i >= 1; i--) {
            logs.add(BackfillFixtures.gameLog(2023020000L + i, "2023-10-%02d".formatted(Math.min(i, 28)),
                    "MTL", "H", 0, 1));
        }
        return logs;
    }

    private static Player player() {
        return Player.builder()
                .id(new Player.PlayerId(PLAYER_ID, SEASON_ID))
                .firstName("Test")
                .lastName("Player")
                .fullName("Test Player")
                .build();
    }
}
