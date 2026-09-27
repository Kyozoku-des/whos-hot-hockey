package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.config.GlobalExceptionHandler;
import com.whoshot.nhl.api.dto.RosterPlayerDto;
import com.whoshot.nhl.api.dto.TeamDetailDto;
import com.whoshot.nhl.api.dto.TeamGameLogDto;
import com.whoshot.nhl.api.dto.TeamStandingsDto;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.entity.Team;
import com.whoshot.nhl.domain.entity.TeamGame;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import com.whoshot.nhl.domain.repository.TeamRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service providing team statistics and standings for the API layer.
 */
@Service
@RequiredArgsConstructor
public class TeamService {

    private final TeamRepository teamRepository;
    private final CurrentSeasonRepository currentSeasonRepository;
    private final TeamGameRepository teamGameRepository;
    private final PlayerRepository playerRepository;

    /**
     * Get all teams for a season ordered by points descending.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return team standings sorted by points
     */
    public List<TeamStandingsDto> getTeamStandings(String season) {
        String resolved = resolveSeason(season);
        return teamRepository.findBySeasonOrderByPointsDesc(resolved).stream()
                .map(this::toStandingsDto)
                .toList();
    }

    /**
     * Get teams currently on a win streak for a season.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return teams with active win streaks
     */
    public List<TeamStandingsDto> getTeamWinStreaks(String season) {
        String resolved = resolveSeason(season);
        return teamRepository.findTeamsWithWinStreaks(resolved).stream()
                .map(this::toStandingsDto)
                .toList();
    }

    /**
     * Get teams currently on a loss streak for a season.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return teams with active loss streaks
     */
    public List<TeamStandingsDto> getTeamLossStreaks(String season) {
        String resolved = resolveSeason(season);
        return teamRepository.findTeamsWithLossStreaks(resolved).stream()
                .map(this::toStandingsDto)
                .toList();
    }

    /**
     * Get detailed team information including roster.
     *
     * @param teamCode team abbreviation code
     * @param season season identifier, or null to auto-detect active season
     * @return team detail with roster
     * @throws GlobalExceptionHandler.TeamNotFoundException if team not found
     */
    public TeamDetailDto getTeamDetail(String teamCode, String season) {
        String resolved = resolveSeason(season);
        Team team = teamRepository.findByTeamCodeAndSeason(teamCode, resolved)
                .orElseThrow(() -> new GlobalExceptionHandler.TeamNotFoundException(teamCode));

        List<RosterPlayerDto> roster = playerRepository.findByTeamCodeAndIdSeason(teamCode, resolved).stream()
                .map(this::toRosterPlayerDto)
                .toList();

        return toDetailDto(team, roster);
    }

    /**
     * Get the game log for a team in a season.
     *
     * @param teamCode team abbreviation code
     * @param season season identifier, or null to auto-detect active season
     * @return game log entries ordered by game date descending
     */
    public List<TeamGameLogDto> getTeamGameLog(String teamCode, String season) {
        String resolved = resolveSeason(season);
        return teamGameRepository.findByTeamCodeAndSeasonIdOrderByGameDateDesc(teamCode, resolved).stream()
                .map(this::toGameLogDto)
                .toList();
    }

    private String resolveSeason(String season) {
        if (season != null && !season.isBlank()) {
            return season;
        }
        return currentSeasonRepository.findByIsActiveTrue()
                .map(CurrentSeason::getSeasonId)
                .orElse(null);
    }

    private TeamStandingsDto toStandingsDto(Team team) {
        return new TeamStandingsDto(
                team.getTeamCode(),
                team.getTeamName(),
                team.getFranchiseName(),
                team.getLogoUrl(),
                team.getGamesPlayed(),
                team.getWins(),
                team.getLosses(),
                team.getOvertimeLosses(),
                team.getPoints(),
                team.getPointPercentage(),
                team.getGoalsFor(),
                team.getGoalsAgainst(),
                team.getGoalDifferential(),
                team.getConferenceName(),
                team.getDivisionName(),
                team.getCurrentWinStreak(),
                team.getCurrentLossStreak(),
                team.getLast10GamesWinPercentage(),
                team.getLast10GamesPointPercentage(),
                team.getLast10GamesPPG(),
                team.getHot(),
                team.getCold(),
                team.getPointStreak(),
                team.getNextOpponentCode(),
                team.getNextGameDate(),
                team.getNextGameIsHome()
        );
    }

    private TeamDetailDto toDetailDto(Team team, List<RosterPlayerDto> roster) {
        return new TeamDetailDto(
                team.getTeamCode(),
                team.getTeamName(),
                team.getFranchiseName(),
                team.getLogoUrl(),
                team.getGamesPlayed(),
                team.getWins(),
                team.getLosses(),
                team.getOvertimeLosses(),
                team.getPoints(),
                team.getPointPercentage(),
                team.getGoalsFor(),
                team.getGoalsAgainst(),
                team.getGoalDifferential(),
                team.getConferenceName(),
                team.getDivisionName(),
                team.getCurrentWinStreak(),
                team.getCurrentLossStreak(),
                team.getLast10GamesWinPercentage(),
                team.getLast10GamesPointPercentage(),
                team.getLast10GamesPPG(),
                team.getHot(),
                team.getCold(),
                team.getPointStreak(),
                team.getNextOpponentCode(),
                team.getNextGameDate(),
                team.getNextGameIsHome(),
                roster
        );
    }

    private RosterPlayerDto toRosterPlayerDto(Player player) {
        return new RosterPlayerDto(
                player.getId().playerId(),
                player.getFullName(),
                player.getPositionCode(),
                player.getTeamCode(),
                player.getHeadshotUrl()
        );
    }

    private TeamGameLogDto toGameLogDto(TeamGame game) {
        return new TeamGameLogDto(
                game.getGameId(),
                game.getGameDate(),
                game.getOpponentTeamCode(),
                game.getHomeGame(),
                game.getGoalsFor(),
                game.getGoalsAgainst(),
                game.getWon(),
                game.getOvertimeLoss(),
                game.getGameType(),
                game.getGameNumber()
        );
    }
}
