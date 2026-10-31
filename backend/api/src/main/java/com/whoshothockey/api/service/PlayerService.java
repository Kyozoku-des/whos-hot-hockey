package com.whoshothockey.api.service;

import com.whoshothockey.api.config.GlobalExceptionHandler;
import com.whoshothockey.api.dto.MissedGameDto;
import com.whoshothockey.api.dto.PlayerDetailDto;
import com.whoshothockey.api.dto.PlayerGameLogDto;
import com.whoshothockey.api.dto.PlayerStandingsDto;
import com.whoshothockey.domain.entity.GameLog;
import com.whoshothockey.domain.entity.Player;
import com.whoshothockey.domain.entity.TeamGame;
import com.whoshothockey.domain.repository.GameLogRepository;
import com.whoshothockey.domain.repository.PlayerRepository;
import com.whoshothockey.domain.repository.TeamGameRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service providing player statistics and standings for the API layer.
 */
@Service
@RequiredArgsConstructor
public class PlayerService {

    /** Games the "last N games" figures cover. */
    static final int LAST_N_GAMES = 10;

    private final PlayerRepository playerRepository;
    private final GameLogRepository gameLogRepository;
    private final TeamGameRepository teamGameRepository;
    private final SeasonResolver seasonResolver;

    /**
     * Get all players for a season ordered by points descending.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return player standings sorted by total points
     */
    public List<PlayerStandingsDto> getPlayerStandings(String season) {
        String resolved = seasonResolver.resolve(season);
        return toStandingsDtos(playerRepository.findByIdSeasonIdOrderByPointsDesc(resolved), resolved);
    }

    /**
     * Get players currently on a point streak for a season.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return players with active point streaks
     */
    public List<PlayerStandingsDto> getPlayerStreaks(String season) {
        String resolved = seasonResolver.resolve(season);
        return toStandingsDtos(playerRepository.findPlayersWithPointStreaks(resolved), resolved);
    }

    /**
     * Get players ranked by recent points per game for a season.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return hot players sorted by recent performance
     */
    public List<PlayerStandingsDto> getHotPlayers(String season) {
        String resolved = seasonResolver.resolve(season);
        return toStandingsDtos(playerRepository.findOrderedByPointsPerLastNGames(resolved), resolved);
    }

    /**
     * Get detailed information for a specific player.
     *
     * @param playerId NHL player identifier
     * @param season season identifier, or null to auto-detect active season
     * @return detailed player information
     * @throws GlobalExceptionHandler.PlayerNotFoundException if the player is not found
     */
    public PlayerDetailDto getPlayerDetail(long playerId, String season) {
        String resolved = seasonResolver.resolve(season);
        Player player = playerRepository.findById(new Player.PlayerId(playerId, resolved))
                .orElseThrow(() -> new GlobalExceptionHandler.PlayerNotFoundException(playerId));
        List<TeamGame> teamGames = teamGames(player);
        Long latestGameId = gameLogRepository.findByPlayerIdAndSeasonId(playerId, resolved).stream()
                .max(Comparator.comparing(GameLog::getGameDate))
                .map(GameLog::getGameId)
                .orElse(null);
        return toDetailDto(player, SeasonPace.regularSeasonGames(resolved),
                MissedGames.inARow(teamGames, latestGameId));
    }

    /**
     * Get the games a player's current team played without them since their first game for it.
     *
     * @param playerId NHL player identifier
     * @param season season identifier, or null to auto-detect active season
     * @return missed games in chronological order; empty for an unknown player
     */
    public List<MissedGameDto> getPlayerMissedGames(long playerId, String season) {
        String resolved = seasonResolver.resolve(season);
        Player player = playerRepository.findById(new Player.PlayerId(playerId, resolved)).orElse(null);
        if (player == null) {
            return List.of();
        }
        Set<Long> played = gameLogRepository.findByPlayerIdAndSeasonId(playerId, resolved).stream()
                .map(GameLog::getGameId)
                .collect(Collectors.toSet());
        return MissedGames.missed(teamGames(player), played).stream()
                .map(game -> new MissedGameDto(
                        game.getGameId(),
                        game.getGameDate(),
                        game.getOpponentTeamCode(),
                        game.getHomeGame()
                ))
                .toList();
    }

    /**
     * Get the game log for a specific player in a season.
     *
     * @param playerId NHL player identifier
     * @param season season identifier, or null to auto-detect active season
     * @return game logs in chronological order by game number
     */
    public List<PlayerGameLogDto> getPlayerGameLog(long playerId, String season) {
        String resolved = seasonResolver.resolve(season);
        return gameLogRepository.findByPlayerIdAndSeasonIdOrderByGameNumberAsc(playerId, resolved).stream()
                .map(gl -> new PlayerGameLogDto(
                        gl.getGameId(),
                        gl.getGameDate(),
                        gl.getOpponentTeamCode(),
                        gl.getHomeGame(),
                        gl.getGoals(),
                        gl.getAssists(),
                        gl.getPoints(),
                        gl.getPlusMinus(),
                        gl.getShots(),
                        gl.getTimeOnIce(),
                        gl.getGameWon(),
                        gl.getGameNumber()
                ))
                .toList();
    }

    private List<TeamGame> teamGames(Player player) {
        if (player.getTeamCode() == null) {
            return List.of();
        }
        return teamGameRepository.findByTeamCodeAndSeasonIdOrderByGameDateAsc(
                player.getTeamCode(), player.getId().seasonId());
    }

    private PlayerDetailDto toDetailDto(Player player, int seasonGames, int gamesMissedInARow) {
        return new PlayerDetailDto(
                player.getId().playerId(),
                player.getFirstName(),
                player.getLastName(),
                player.getFullName(),
                player.getPositionCode(),
                player.getTeamCode(),
                player.getTeamLogoUrl(),
                player.getGamesPlayed(),
                player.getGoals(),
                player.getAssists(),
                player.getPoints(),
                player.getPointsPerGame(),
                player.getPlusMinus(),
                player.getCurrentPointStreak(),
                player.getCurrentPointlessStreak(),
                player.getPointsPerLastNGames(),
                seasonGames,
                SeasonPace.projectedPoints(player.getPoints(), player.getGamesPlayed(), seasonGames),
                gamesMissedInARow
        );
    }

    /**
     * Maps a season's players, adding their last-N totals and missed games from the season's
     * team games and each player's latest game.
     */
    private List<PlayerStandingsDto> toStandingsDtos(List<Player> players, String seasonId) {
        Map<String, List<TeamGame>> teamGames = teamGameRepository.findBySeasonId(seasonId).stream()
                .sorted(Comparator.comparing(TeamGame::getGameDate))
                .collect(Collectors.groupingBy(TeamGame::getTeamCode));
        Map<Long, Long> latestGameIds = gameLogRepository.findLatestGames(seasonId).stream()
                .collect(Collectors.toMap(GameLog::getPlayerId, GameLog::getGameId, (a, b) -> a));
        int seasonGames = SeasonPace.regularSeasonGames(seasonId);
        return players.stream()
                .map(player -> toStandingsDto(player,
                        teamGames.getOrDefault(player.getTeamCode(), List.of()),
                        latestGameIds.get(player.getId().playerId()),
                        seasonGames))
                .toList();
    }

    private PlayerStandingsDto toStandingsDto(Player player, List<TeamGame> teamGames, Long latestGameId,
                                              int seasonGames) {
        Integer lastNGames = player.getGamesPlayed() == null ? null
                : Math.min(LAST_N_GAMES, player.getGamesPlayed());
        Integer lastNPoints = lastNGames == null || player.getPointsPerLastNGames() == null ? null
                : (int) Math.round(player.getPointsPerLastNGames() * lastNGames);
        // The games left are the team's: a player who sat out games cannot make them up
        int teamGamesPlayed = teamGames.isEmpty() && player.getGamesPlayed() != null
                ? player.getGamesPlayed() : teamGames.size();
        return new PlayerStandingsDto(
                player.getId().playerId(),
                player.getFirstName(),
                player.getLastName(),
                player.getFullName(),
                player.getPositionCode(),
                player.getTeamCode(),
                player.getTeamLogoUrl(),
                player.getGamesPlayed(),
                player.getGoals(),
                player.getAssists(),
                player.getPoints(),
                player.getPointsPerGame(),
                player.getPlusMinus(),
                player.getPointsPerLastNGames(),
                player.getCurrentPointStreak(),
                player.getCurrentPointlessStreak(),
                lastNGames,
                lastNPoints,
                SeasonPace.projectedPointsAtRate(player.getPoints(), player.getPointsPerLastNGames(),
                        seasonGames - teamGamesPlayed),
                MissedGames.inARow(teamGames, latestGameId)
        );
    }
}
