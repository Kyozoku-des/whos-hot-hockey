package com.whoshot.factory;

import com.whoshot.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.dto.nhlapi.PlayerInfoDto;
import com.whoshot.dto.nhlapi.PlayerStandingDto;
import com.whoshot.entity.Player;
import com.whoshot.exception.PlayerStatisticsException;
import com.whoshot.model.PlayerStatistics;
import com.whoshot.service.StatisticsCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PlayerFactory {

    private final StatisticsCalculationService statisticsCalculationService;

    public Player createFromApiData(
            PlayerInfoDto info,
            PlayerStandingDto standing,
            List<PlayerGameLogDto> gameLogs,
            String seasonId,
            int gameWindow
    ) throws PlayerStatisticsException {
        // Calculate statistics separately (pure function)
        PlayerStatistics statistics = statisticsCalculationService.calculatePlayerStatistics(standing, gameLogs, gameWindow);

        // Build complete player in one place
        return Player.builder()
                .id(new Player.PlayerId(info.getPlayerId(), seasonId))
                .firstName(info.getFirstName().getName())
                .lastName(info.getLastName().getName())
                .fullName(info.getFirstName().getName() + " " + info.getLastName().getName())
                .positionCode(info.getPosition())
                .teamCode(info.getCurrentTeamAbbrev())
                .headshotUrl(info.getHeadshotUrl())
                .gamesPlayed(statistics.gamesPlayed())
                .points(statistics.points())
                .goals(statistics.goals())
                .assists(statistics.assists())
                .pointsPerGame(statistics.pointsPerGame())
                .plusMinus(statistics.plusMinus())
                .currentPointStreak(statistics.currentPointStreak())
                .currentPointlessStreak(statistics.currentPointlessStreak())
                .pointsPerLastNGames(statistics.pointsPerLastNGames())
                .lastUpdated(statistics.lastUpdated())
                .build();
    }
}
