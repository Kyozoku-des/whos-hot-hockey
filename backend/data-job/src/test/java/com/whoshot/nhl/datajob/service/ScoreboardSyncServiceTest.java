package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.ScoreResponseDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Which game days {@link ScoreboardSyncService} fetches, and that a failure never escapes.
 */
class ScoreboardSyncServiceTest {

    private final NhlApiService api = mock(NhlApiService.class);
    private final ScoreboardWriter writer = mock(ScoreboardWriter.class);
    private final ScoreboardSyncService service = new ScoreboardSyncService(api, writer);

    @Test
    void pollFetchesOnlyTheCurrentDayAndKeepsOlderDays() {
        var current = day("2026-10-08", "2026-10-09", game(1L));
        when(api.getScores("now")).thenReturn(current);
        when(writer.writeGames(anyList(), any())).thenReturn(1);

        assertThat(service.syncScoreboard(false)).isEqualTo(1);

        verify(api, never()).getScores("2026-10-08");
        verify(writer).writeGames(current.getGames(), null);
    }

    @Test
    void fullSyncAlsoFetchesThePreviousDayAndDropsOlderDays() {
        var currentGame = game(1L);
        var previousGame = game(2L);
        when(api.getScores("now")).thenReturn(day("2026-10-08", "2026-10-09", currentGame));
        when(api.getScores("2026-10-08")).thenReturn(day("2026-10-07", "2026-10-08", previousGame));

        service.syncScoreboard(true);

        verify(writer).writeGames(List.of(currentGame, previousGame), "2026-10-08");
    }

    @Test
    void upstreamFailureIsSkipped() {
        when(api.getScores("now")).thenThrow(new IllegalStateException("upstream down"));

        assertThat(service.syncScoreboard(true)).isZero();

        verifyNoInteractions(writer);
    }

    private static ScoreResponseDto day(String prevDate, String currentDate, ScoreResponseDto.ScoreGame... games) {
        var day = new ScoreResponseDto();
        day.setPrevDate(prevDate);
        day.setCurrentDate(currentDate);
        day.setGames(List.of(games));
        return day;
    }

    private static ScoreResponseDto.ScoreGame game(Long id) {
        var game = new ScoreResponseDto.ScoreGame();
        game.setId(id);
        return game;
    }
}
