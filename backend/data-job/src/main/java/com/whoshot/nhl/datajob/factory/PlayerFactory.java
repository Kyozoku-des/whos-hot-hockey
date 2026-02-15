package com.whoshot.nhl.datajob.factory;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerGameLogDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.datajob.exception.PlayerStatisticsException;
import com.whoshot.nhl.datajob.model.PlayerStatistics;
import com.whoshot.nhl.datajob.service.StatisticsCalculationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Factory responsible for creating {@link Player} entities from NHL API DTOs.
 */
@Service
@RequiredArgsConstructor
public class PlayerFactory {

    private final StatisticsCalculationService statisticsCalculationService;

    /**
     * Builds a fully populated player entity for a single season from upstream API data.
     *
     * @param info player metadata payload
     * @param standing player standing row used for totals validation
     * @param gameLogs game-by-game logs used for derived statistics
     * @param seasonId target season identifier
     * @return constructed player entity ready for persistence
     * @throws PlayerStatisticsException if calculated totals do not match expected standings totals
     */
    public Player createFromApiData(
            PlayerInfoDto info,
            PlayerStandingDto standing,
            List<PlayerGameLogDto> gameLogs,
            String seasonId
    ) throws PlayerStatisticsException {
        // Calculate statistics separately (pure function)
        PlayerStatistics statistics = statisticsCalculationService.calculatePlayerStatistics(standing, gameLogs);

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
                .lastUpdated(statistics.lastUpdated())
                .build();
    }
}
