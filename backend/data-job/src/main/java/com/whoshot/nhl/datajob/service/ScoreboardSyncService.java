package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.ScoreResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Refreshes the score ticker (issue #42): the current game day's started games and, on a full
 * sync, the previous game day's. A failure is logged and skipped so it never holds up the
 * player and team sync; the next sync retries.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScoreboardSyncService {

    private final NhlApiService nhlApiService;
    private final ScoreboardWriter scoreboardWriter;

    /**
     * Writes the current game day's games, plus the previous game day's when requested. Including
     * the previous day also drops anything older, so the table holds at most two game days.
     *
     * @param includePreviousDay whether to also refresh the previous game day
     * @return number of games written
     */
    public int syncScoreboard(boolean includePreviousDay) {
        try {
            ScoreResponseDto current = nhlApiService.getScores("now");
            List<ScoreResponseDto.ScoreGame> games = new ArrayList<>(gamesOf(current));
            String keepFromDate = null;
            if (includePreviousDay && current.getPrevDate() != null) {
                games.addAll(gamesOf(nhlApiService.getScores(current.getPrevDate())));
                keepFromDate = current.getPrevDate();
            }
            int written = scoreboardWriter.writeGames(games, keepFromDate);
            log.debug("Scoreboard sync wrote {} games for {}", written, current.getCurrentDate());
            return written;
        } catch (RuntimeException e) {
            IngestionFailures.rethrowIfFatal(e);
            log.warn("Could not sync scoreboard: {}", e.getMessage());
            return 0;
        }
    }

    private static List<ScoreResponseDto.ScoreGame> gamesOf(ScoreResponseDto response) {
        return response.getGames() != null ? response.getGames() : List.of();
    }
}
