package com.nhl.whoshotbackend.script;

import com.nhl.whoshotbackend.service.DataIntegrationService;
import com.nhl.whoshotbackend.service.NhlApiService;
import com.nhl.whoshotbackend.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * One-time script to populate previous season data.
 * Activated via command-line argument: --populate-previous-season=true
 *
 * Usage: java -jar app.jar --populate-previous-season=true
 */
@Component
@Slf4j
@ConditionalOnProperty(name = "populate-previous-season", havingValue = "true")
public class PopulatePreviousSeasonScript implements CommandLineRunner {

    private final DataIntegrationService dataIntegrationService;
    private final StatisticsService statisticsService;
    private final NhlApiService nhlApiService;

    public PopulatePreviousSeasonScript(
            DataIntegrationService dataIntegrationService,
            StatisticsService statisticsService,
            NhlApiService nhlApiService) {
        this.dataIntegrationService = dataIntegrationService;
        this.statisticsService = statisticsService;
        this.nhlApiService = nhlApiService;
    }

    @Override
    public void run(String... args) {
        log.info("=== Starting previous season data population script ===");

        try {
            // Calculate previous season ID
            String currentSeasonId = nhlApiService.getCurrentSeason();
            String previousSeasonId = calculatePreviousSeasonId(currentSeasonId);

            log.info("Current season: {}, Previous season: {}", currentSeasonId, previousSeasonId);

            // Sync standings for previous season
            log.info("Syncing standings for previous season {}...", previousSeasonId);
            dataIntegrationService.syncStandings(previousSeasonId);

            // Sync player stats for previous season
            log.info("Syncing player stats for previous season {}...", previousSeasonId);
            dataIntegrationService.syncPlayerStats(previousSeasonId);

            // Calculate hot ratings and streaks for previous season
            log.info("Calculating statistics for previous season {}...", previousSeasonId);
            statisticsService.calculateHotRatings(previousSeasonId);

            log.info("=== Previous season data population completed successfully ===");
            log.info("Previous season {} data has been stored for future comparison features", previousSeasonId);

        } catch (Exception e) {
            log.error("Error during previous season data population", e);
            throw new RuntimeException("Failed to populate previous season data", e);
        }
    }

    /**
     * Calculate previous season ID from current season ID.
     * Example: "20242025" -> "20232024"
     */
    private String calculatePreviousSeasonId(String currentSeasonId) {
        if (currentSeasonId == null || currentSeasonId.length() != 8) {
            throw new IllegalArgumentException("Invalid season ID format: " + currentSeasonId);
        }

        int startYear = Integer.parseInt(currentSeasonId.substring(0, 4));
        int endYear = Integer.parseInt(currentSeasonId.substring(4, 8));

        int previousStartYear = startYear - 1;
        int previousEndYear = endYear - 1;

        return String.format("%d%d", previousStartYear, previousEndYear);
    }
}
