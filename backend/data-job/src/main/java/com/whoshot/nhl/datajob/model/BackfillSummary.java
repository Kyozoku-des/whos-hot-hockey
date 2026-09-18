package com.whoshot.nhl.datajob.model;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The outcome of one backfill run (FR-008, FR-009). Mutable during the run via {@link #skip}, then
 * finalized with {@link #finish}. {@link #render()} produces the human-readable summary described
 * in contracts/cli-contract.md.
 */
public class BackfillSummary {

    /**
     * One record that could not be loaded, and why.
     *
     * @param kind       what kind of record was skipped, e.g. "player" or "team"
     * @param identifier the record's identifier, e.g. a player id or team code
     * @param reason     human-readable reason
     */
    public record Skip(String kind, String identifier, String reason) {
    }

    private final String seasonId;
    private final Instant startedAt;
    private Instant finishedAt;
    private int teamsWritten;
    private int teamGamesWritten;
    private int playersWritten;
    private int gameLogsWritten;
    private final List<Skip> skipped = new ArrayList<>();
    private boolean success = true;

    public BackfillSummary(String seasonId) {
        this.seasonId = seasonId;
        this.startedAt = Instant.now();
    }

    public void addTeamsWritten(int count) {
        teamsWritten += count;
    }

    public void addTeamGamesWritten(int count) {
        teamGamesWritten += count;
    }

    public void addPlayerWritten() {
        playersWritten++;
    }

    public void addGameLogsWritten(int count) {
        gameLogsWritten += count;
    }

    public void skip(String kind, String identifier, String reason) {
        skipped.add(new Skip(kind, identifier, reason));
    }

    public void markFailed() {
        success = false;
    }

    public BackfillSummary finish() {
        this.finishedAt = Instant.now();
        return this;
    }

    public String seasonId() {
        return seasonId;
    }

    public int teamsWritten() {
        return teamsWritten;
    }

    public int teamGamesWritten() {
        return teamGamesWritten;
    }

    public int playersWritten() {
        return playersWritten;
    }

    public int gameLogsWritten() {
        return gameLogsWritten;
    }

    public List<Skip> skipped() {
        return Collections.unmodifiableList(skipped);
    }

    public boolean success() {
        return success;
    }

    public Duration duration() {
        Instant end = finishedAt != null ? finishedAt : Instant.now();
        return Duration.between(startedAt, end);
    }

    /**
     * Renders the human-readable summary block described in contracts/cli-contract.md.
     */
    public String render() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Backfill summary: season ").append(seasonId).append(" ===\n");
        sb.append("Duration:        ").append(formatDuration(duration())).append('\n');
        sb.append("Teams:           ").append(teamsWritten).append('\n');
        sb.append("Team games:      ").append(teamGamesWritten).append('\n');
        sb.append("Players:         ").append(playersWritten).append('\n');
        sb.append("Player game logs: ").append(gameLogsWritten).append('\n');
        sb.append("Skipped:         ").append(skipped.size()).append('\n');
        for (Skip skip : skipped) {
            sb.append("  - ").append(skip.kind()).append(' ').append(skip.identifier())
                    .append(": ").append(skip.reason()).append('\n');
        }
        sb.append("Result:          ").append(success ? "SUCCESS" : "FAILURE").append('\n');
        return sb.toString();
    }

    static String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        return String.format("%02d:%02d:%02d",
                totalSeconds / 3600, (totalSeconds % 3600) / 60, totalSeconds % 60);
    }
}
