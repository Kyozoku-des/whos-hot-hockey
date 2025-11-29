package com.whoshot.service;

import com.whoshot.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.dto.nhlapi.PlayerStandingDto;
import com.whoshot.exception.PlayerStatisticsException;
import com.whoshot.model.PlayerStatistics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
public class StatisticsCalculationService {

    public PlayerStatistics calculatePlayerStatistics(
            PlayerStandingDto playerStanding,
            List<PlayerGameLogDto> playerGameLogs,
            int n
    ) throws PlayerStatisticsException {
        int gamesPlayed = 0;
        int points = 0;
        int goals = 0;
        int assists = 0;
        int plusMinus = 0;
        double pointsPerGame;
        int pointStreak = 0;
        int pointlessStreak = 0;
        double pointsPerLastNGames = 0.0;

        for (PlayerGameLogDto gameLog : playerGameLogs) {
            gamesPlayed++;
            points += gameLog.getPoints() != null ? gameLog.getPoints() : 0;
            goals += gameLog.getGoals() != null ? gameLog.getGoals() : 0;
            assists += gameLog.getAssists() != null ? gameLog.getAssists() : 0;
            plusMinus += gameLog.getPlusMinus() != null ? gameLog.getPlusMinus() : 0;

            if (points > 0) {
                pointStreak++;
                pointlessStreak = 0;
            } else {
                pointStreak = 0;
                pointlessStreak++;
            }

            if (gamesPlayed == n) {
                pointsPerLastNGames = points / (double) n;
            }
        }

        if (playerStanding.getPoints() != points) {
            String errorMessage = String.format("Points mismatch for player ID %d: calculated %d, expected %d", playerStanding.getId(), points, playerStanding.getPoints());
            throw new PlayerStatisticsException(errorMessage);
        }

        pointsPerGame = gamesPlayed > 0 ? (double) points / gamesPlayed : 0.0;

        return PlayerStatistics.builder()
                .gamesPlayed(gamesPlayed)
                .points(points)
                .goals(goals)
                .assists(assists)
                .pointsPerGame(pointsPerGame)
                .plusMinus(plusMinus)
                .currentPointStreak(pointStreak)
                .currentPointlessStreak(pointlessStreak)
                .pointsPerLastNGames(pointsPerLastNGames)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
}
