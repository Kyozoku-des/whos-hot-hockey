package com.whoshot.nhl.api.service;

import com.whoshot.nhl.api.config.GlobalExceptionHandler;
import com.whoshot.nhl.api.dto.PlayerDetailDto;
import com.whoshot.nhl.api.dto.PlayerGameLogDto;
import com.whoshot.nhl.api.dto.PlayerStandingsDto;
import com.whoshot.nhl.domain.entity.CurrentSeason;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.CurrentSeasonRepository;
import com.whoshot.nhl.domain.repository.GameLogRepository;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service providing player statistics and standings for the API layer.
 */
@Service
@RequiredArgsConstructor
public class PlayerService {

    private final PlayerRepository playerRepository;
    private final GameLogRepository gameLogRepository;
    private final CurrentSeasonRepository currentSeasonRepository;

    /**
     * Get all players for a season ordered by points descending.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return player standings sorted by total points
     */
    public List<PlayerStandingsDto> getPlayerStandings(String season) {
        String resolved = resolveSeason(season);
        return playerRepository.findByIdSeasonOrderByPointsDesc(resolved).stream()
                .map(this::toStandingsDto)
                .toList();
    }

    /**
     * Get players currently on a point streak for a season.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return players with active point streaks
     */
    public List<PlayerStandingsDto> getPlayerStreaks(String season) {
        String resolved = resolveSeason(season);
        return playerRepository.findPlayersWithPointStreaks(resolved).stream()
                .map(this::toStandingsDto)
                .toList();
    }

    /**
     * Get players flagged as hot for a season.
     *
     * @param season season identifier, or null to auto-detect active season
     * @return hot players sorted by recent performance
     */
    public List<PlayerStandingsDto> getHotPlayers(String season) {
        String resolved = resolveSeason(season);
        return playerRepository.findHotPlayers(resolved).stream()
                .map(this::toStandingsDto)
                .toList();
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
        String resolved = resolveSeason(season);
        Player player = playerRepository.findById(new Player.PlayerId(playerId, resolved))
                .orElseThrow(() -> new GlobalExceptionHandler.PlayerNotFoundException(playerId));
        return toDetailDto(player);
    }

    /**
     * Get the game log for a specific player in a season.
     *
     * @param playerId NHL player identifier
     * @param season season identifier, or null to auto-detect active season
     * @return game logs in chronological order by game number
     */
    public List<PlayerGameLogDto> getPlayerGameLog(long playerId, String season) {
        String resolved = resolveSeason(season);
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

    private PlayerDetailDto toDetailDto(Player player) {
        PlayerDetailDto.NextGameDto nextGameDto = null;
        if (player.getNextGame() != null) {
            nextGameDto = new PlayerDetailDto.NextGameDto(
                    player.getNextGame().date(),
                    player.getNextGame().opponentAbbrev(),
                    player.getNextGame().homeRoadFlag()
            );
        }
        return new PlayerDetailDto(
                player.getId().playerId(),
                player.getFirstName(),
                player.getLastName(),
                player.getFullName(),
                player.getPositionCode(),
                player.getTeamCode(),
                player.getTeamLogoUrl(),
                player.getHeadshotUrl(),
                player.getGamesPlayed(),
                player.getGoals(),
                player.getAssists(),
                player.getPoints(),
                player.getPointsPerGame(),
                player.getPlusMinus(),
                player.getCurrentPointStreak(),
                player.getCurrentPointlessStreak(),
                player.getPointsPerLastNGames(),
                player.getHot(),
                player.getCold(),
                nextGameDto
        );
    }

    private String resolveSeason(String season) {
        if (season != null && !season.isBlank()) {
            return season;
        }
        return currentSeasonRepository.findByIsActiveTrue()
                .map(CurrentSeason::getSeasonId)
                .orElse(null);
    }

    private PlayerStandingsDto toStandingsDto(Player player) {
        return new PlayerStandingsDto(
                player.getId().playerId(),
                player.getFirstName(),
                player.getLastName(),
                player.getFullName(),
                player.getPositionCode(),
                player.getTeamCode(),
                player.getTeamLogoUrl(),
                player.getHeadshotUrl(),
                player.getGamesPlayed(),
                player.getGoals(),
                player.getAssists(),
                player.getPoints(),
                player.getPointsPerGame(),
                player.getPlusMinus(),
                player.getPointsPerLastNGames(),
                player.getHot(),
                player.getCold(),
                player.getCurrentPointStreak(),
                player.getCurrentPointlessStreak()
        );
    }
}
