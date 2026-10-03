package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.model.TeamGameIndex;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonDataWriter;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.PLAYER_ID;
import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;

/**
 * A player and their game logs are one persistence unit (issue #29): a database failure part-way
 * through the logs must not leave the player committed without them, and rewriting converges.
 */
class PlayerWriteAtomicityIT extends PostgresIntegrationTestBase {

    @MockitoBean
    private NhlApiService nhlApiService;
    @MockitoSpyBean
    private GameLogRepository gameLogRepository;

    @Autowired
    private SeasonDataWriter seasonDataWriter;
    @Autowired
    private PlayerRepository playerRepository;

    @Test
    void failureWhileWritingGameLogs_rollsBackThePlayerToo() {
        doThrow(new DataAccessResourceFailureException("connection lost")).when(gameLogRepository).flush();

        assertThrows(DataAccessResourceFailureException.class, () -> seasonDataWriter.writePlayer(
                player(), BackfillFixtures.twoGameLogsMostRecentFirst(), TeamGameIndex.empty()));

        assertTrue(playerRepository.findById(new Player.PlayerId(PLAYER_ID, SEASON_ID)).isEmpty(),
                "the player must not be committed without their game logs");
        assertTrue(gameLogRepository.findByPlayerIdAndSeasonId(PLAYER_ID, SEASON_ID).isEmpty());
    }

    @Test
    void malformedGameLog_writesNothing() {
        PlayerGameLogDto malformed = BackfillFixtures.twoGameLogsMostRecentFirst().getFirst();
        malformed.setToi("not-a-toi");

        assertThrows(IllegalArgumentException.class,
                () -> seasonDataWriter.writePlayer(player(), List.of(malformed), TeamGameIndex.empty()));

        assertTrue(playerRepository.findById(new Player.PlayerId(PLAYER_ID, SEASON_ID)).isEmpty());
    }

    @Test
    void rewritingThePlayer_isIdempotent() {
        seasonDataWriter.writePlayer(player(), BackfillFixtures.twoGameLogsMostRecentFirst(), TeamGameIndex.empty());
        seasonDataWriter.writePlayer(player(), BackfillFixtures.twoGameLogsMostRecentFirst(), TeamGameIndex.empty());

        assertEquals(1, playerRepository.count());
        assertEquals(2, gameLogRepository.findByPlayerIdAndSeasonId(PLAYER_ID, SEASON_ID).size());
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
