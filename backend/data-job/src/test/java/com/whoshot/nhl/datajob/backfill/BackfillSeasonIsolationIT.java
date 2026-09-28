package com.whoshot.nhl.datajob.backfill;

import com.whoshot.nhl.datajob.model.BackfillRequest;
import com.whoshot.nhl.datajob.model.BackfillSummary;
import com.whoshot.nhl.datajob.service.NhlApiService;
import com.whoshot.nhl.datajob.service.SeasonBackfillService;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.PLAYER_ID;
import static com.whoshot.nhl.datajob.backfill.BackfillFixtures.SEASON_ID;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration coverage for FR-004 (other seasons untouched), FR-005 (active-season marker
 * untouched), and SC-006 (current-season data provably unaffected by a backfill).
 */
class BackfillSeasonIsolationIT extends PostgresIntegrationTestBase {

    @MockitoBean
    private NhlApiService nhlApiService;

    @Autowired
    private SeasonBackfillService seasonBackfillService;
    @Autowired
    private PlayerRepository playerRepository;
    @Autowired
    private TeamRepository teamRepository;
    @Autowired
    private CurrentSeasonRepository currentSeasonRepository;

    private static final String CURRENT_SEASON_ID = "20242025";

    @Test
    void backfillOfPastSeason_leavesCurrentSeasonAndActiveMarkerUntouched() {
        // Seed pre-existing current-season data and the active-season marker.
        Player currentSeasonPlayer = Player.builder()
                .id(new Player.PlayerId(PLAYER_ID, CURRENT_SEASON_ID))
                .firstName("Connor").lastName("McDavid").fullName("Connor McDavid")
                .gamesPlayed(10).points(20).goals(10).assists(10)
                .build();
        playerRepository.save(currentSeasonPlayer);

        CurrentSeason activeSeason = new CurrentSeason();
        activeSeason.setSeasonId(CURRENT_SEASON_ID);
        activeSeason.setSeasonDisplayName("2024-2025");
        activeSeason.setIsActive(true);
        activeSeason.setLastUpdated("2025-01-01T00:00:00");
        currentSeasonRepository.save(activeSeason);

        long playersBefore = playerRepository.count();
        long teamsBefore = teamRepository.count();
        CurrentSeason activeBefore = currentSeasonRepository.findByIsActiveTrue().orElseThrow();

        BackfillTestSupport.stubUpstream(nhlApiService);

        BackfillRequest request = BackfillRequest.validate(SEASON_ID, List.of(
                BackfillFixtures.season(SEASON_ID,
                        LocalDateTime.of(2023, 10, 1, 0, 0), LocalDateTime.of(2024, 4, 17, 0, 0))));

        BackfillSummary summary = seasonBackfillService.run(request, false);

        assertTrue(summary.success());
        // Current-season row count is unchanged: only the backfilled season's rows were added.
        assertEquals(playersBefore + 1, playerRepository.count(), "exactly one new player row (the backfilled one) should exist");
        assertTrue(teamRepository.count() > teamsBefore, "backfilled teams should be added");

        // The pre-existing current-season player is untouched.
        Player stillThere = playerRepository.findById(new Player.PlayerId(PLAYER_ID, CURRENT_SEASON_ID)).orElseThrow();
        assertEquals(20, stillThere.getPoints());

        // The active-season marker was not moved.
        CurrentSeason activeAfter = currentSeasonRepository.findByIsActiveTrue().orElseThrow();
        assertEquals(activeBefore.getSeasonId(), activeAfter.getSeasonId());
        assertEquals(CURRENT_SEASON_ID, activeAfter.getSeasonId());
        assertEquals(1, currentSeasonRepository.findAllByIsActiveTrue().size(),
                "backfilling must not create a second active row");
    }
}
