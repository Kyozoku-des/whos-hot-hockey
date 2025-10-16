package com.nhl.whoshotbackend.config;

import com.nhl.whoshotbackend.service.DataIntegrationService;
import com.nhl.whoshotbackend.service.NhlApiService;
import com.nhl.whoshotbackend.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes data on application startup.
 * Syncs data for the current season by default.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DataIntegrationService dataIntegrationService;
    private final StatisticsService statisticsService;
    private final NhlApiService nhlApiService;

    public DataInitializer(
            DataIntegrationService dataIntegrationService,
            StatisticsService statisticsService,
            NhlApiService nhlApiService) {
        this.dataIntegrationService = dataIntegrationService;
        this.statisticsService = statisticsService;
        this.nhlApiService = nhlApiService;
    }

    @Override
    public void run(String... args) {
        String currentSeason = nhlApiService.getCurrentSeason();
        log.info("=== Starting initial data synchronization for season {} ===", currentSeason);

        try {
            // Sync all data from NHL API for current season
            dataIntegrationService.syncStandings(currentSeason);
            dataIntegrationService.syncPlayerStats(currentSeason);

            // Calculate hot ratings and streaks for current season
            statisticsService.calculateHotRatings(currentSeason);

            log.info("=== Initial data synchronization completed successfully for season {} ===", currentSeason);
        } catch (Exception e) {
            log.error("Error during initial data synchronization. Application will continue but may have incomplete data.", e);
        }
    }
}
