package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.dto.nhlapi.TeamStandingsDto;
import com.whoshot.nhl.datajob.model.TeamGameIndex;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.entity.TeamRosterEntry;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import com.whoshot.nhl.domain.repository.TeamRosterRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * Short, atomic persistence units for ingestion (issue #29). Each public method is one transaction
 * that performs no network calls, so fetch work never holds a transaction open and a failure rolls
 * back exactly one unit:
 * <ul>
 *   <li>{@link #activateSeason} — the active-season switch;</li>
 *   <li>{@link #persistStandings} — one standings snapshot;</li>
 *   <li>{@link #writePlayer} — one player together with all of their game logs;</li>
 *   <li>{@link #writeRoster} — one team's roster.</li>
 * </ul>
 * Callers pass fetched DTOs and new, unmanaged entities only; managed entities never leave the
 * transaction that loaded them.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeasonDataWriter {

    private final CurrentSeasonRepository currentSeasonRepository;
    private final TeamRepository teamRepository;
    private final PlayerRepository playerRepository;
    private final GameLogWriter gameLogWriter;
    private final TeamRosterRepository teamRosterRepository;

    /**
     * Persists the resolved season as the sole active season. Deactivates any other season
     * previously marked active so that {@code findByIsActiveTrue()} always returns at most
     * one row; leaving more than one active row throws IncorrectResultSizeDataAccessException
     * for every API request that resolves the default season (players, teams, search).
     */
    @Transactional
    public void activateSeason(SeasonDto season) {
        String seasonId = season.getId();

        currentSeasonRepository.findAllByIsActiveTrue().stream()
                .filter(active -> !active.getSeasonId().equals(seasonId))
                .forEach(active -> {
                    active.setIsActive(false);
                    currentSeasonRepository.save(active);
                });

        CurrentSeason currentSeason = currentSeasonRepository.findBySeasonId(seasonId)
                .orElse(new CurrentSeason());
        currentSeason.setSeasonId(seasonId);
        currentSeason.setSeasonDisplayName(
                seasonId.substring(0, 4) + "-" + seasonId.substring(4));
        currentSeason.setIsActive(true);
        currentSeason.setLastUpdated(LocalDateTime.now());
        currentSeasonRepository.save(currentSeason);
        log.info("Persisted active season to current_season table: {}", seasonId);
    }

    /**
     * Persists an already-fetched list of standings rows for the given season. Season-parameterised
     * so callers can supply either today's standings (live sync) or a historical date's standings
     * (backfill, via {@link NhlApiService#getTeamStandings(String)}) without this method knowing
     * which.
     *
     * @param seasonId      season the standings belong to
     * @param onlyTeamCodes when non-null, only these team codes are persisted
     * @param standings     standings rows to persist, in official rank order
     * @return number of teams saved
     */
    @Transactional
    public int persistStandings(String seasonId, Set<String> onlyTeamCodes, List<TeamStandingsDto> standings) {
        int processedCount = 0;

        for (TeamStandingsDto standing : standings) {
            String teamCode = standing.getTeamAbbrev().getDefaultValue();

            if (onlyTeamCodes != null && !onlyTeamCodes.contains(teamCode)) {
                continue;
            }

            Team team = teamRepository.findByTeamCodeAndSeasonId(teamCode, seasonId)
                    .orElse(new Team());

            applyStandings(team, standing, seasonId);
            teamRepository.save(team);
            processedCount++;
        }

        teamRepository.flush();
        return processedCount;
    }

    /**
     * Writes one player and all of their game logs for a season as a single unit: the logs are
     * validated before anything is written, and any failure (including a database error part-way
     * through the logs) rolls back the player row as well, so a player is never committed without
     * their logs.
     *
     * @param player    new, unmanaged player built from fetched data
     * @param gameLogs  that player's upstream game logs, most-recent-first
     * @param teamGames outcomes of the team games already written for the season
     * @return number of game logs written
     * @throws IllegalArgumentException if a game log cannot be mapped; nothing is written
     */
    @Transactional(rollbackFor = Exception.class)
    public int writePlayer(Player player, List<PlayerGameLogDto> gameLogs, TeamGameIndex teamGames) {
        GameLogWriter.validatePlayerGameLogs(gameLogs);
        playerRepository.save(player);
        playerRepository.flush();
        return gameLogWriter.writePlayerGameLogs(player.getId().playerId(), player.getId().seasonId(),
                gameLogs, teamGames);
    }

    /**
     * Replaces one team's roster for a season. A player listed here moves off any other team's
     * roster, since a player is on at most one roster per season.
     *
     * @param seasonId  season the roster belongs to
     * @param teamCode  team the roster belongs to
     * @param playerIds every player on the team's roster
     */
    @Transactional
    public void writeRoster(String seasonId, String teamCode, List<Long> playerIds) {
        teamRosterRepository.deleteBySeasonIdAndTeamCode(seasonId, teamCode);
        teamRosterRepository.flush();
        LocalDateTime now = LocalDateTime.now();
        teamRosterRepository.saveAll(playerIds.stream().distinct()
                .map(playerId -> new TeamRosterEntry(seasonId, playerId, teamCode, now))
                .toList());
    }

    /**
     * Copies one standings row onto a Team entity.
     */
    static void applyStandings(Team team, TeamStandingsDto standing, String seasonId) {
        team.setTeamCode(standing.getTeamAbbrev().getDefaultValue());
        team.setSeasonId(seasonId);
        team.setTeamName(standing.getTeamName().getDefaultValue());
        team.setLogoUrl(standing.getTeamLogo());
        team.setGamesPlayed(standing.getGamesPlayed());
        team.setWins(standing.getWins());
        team.setLosses(standing.getLosses());
        team.setOvertimeLosses(standing.getOtLosses());
        team.setPoints(standing.getPoints());
        team.setPointPercentage(standing.getPointPctg());
        team.setGoalsFor(standing.getGoalFor());
        team.setGoalsAgainst(standing.getGoalAgainst());
        team.setGoalDifferential(standing.getGoalDifferential());
        team.setConferenceName(standing.getConferenceName());
        team.setDivisionName(standing.getDivisionName());

        // Streaks
        if ("W".equals(standing.getStreakCode())) {
            team.setCurrentWinStreak(standing.getStreakCount());
            team.setCurrentLossStreak(0);
        } else if ("L".equals(standing.getStreakCode()) || "OT".equals(standing.getStreakCode())) {
            team.setCurrentWinStreak(0);
            team.setCurrentLossStreak(standing.getStreakCount());
        }

        // Last 10 games
        if (standing.getL10Wins() != null && standing.getL10Losses() != null && standing.getL10OtLosses() != null) {
            int l10Games = standing.getL10Wins() + standing.getL10Losses() + standing.getL10OtLosses();
            if (l10Games > 0) {
                int l10Points = standing.getL10Wins() * 2 + standing.getL10OtLosses();
                team.setLast10GamesPointPercentage((double) l10Points / (l10Games * 2));
                team.setLast10GamesPPG((double) l10Points / l10Games);
            }
        }

        team.setLastUpdated(LocalDateTime.now());
    }
}
