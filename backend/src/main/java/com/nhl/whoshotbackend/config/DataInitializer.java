package com.nhl.whoshotbackend.config;

import com.nhl.whoshotbackend.service.DataIntegrationService;
import com.nhl.whoshotbackend.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes data on application startup.
 */
@Component
@Slf4j
public class DataInitializer implements CommandLineRunner {

    private final DataIntegrationService dataIntegrationService;
    private final StatisticsService statisticsService;

    public DataInitializer(
            DataIntegrationService dataIntegrationService,
            StatisticsService statisticsService) {
        this.dataIntegrationService = dataIntegrationService;
        this.statisticsService = statisticsService;
    }

    @Override
    public void run(String... args) {
        log.info("=== Starting initial data synchronization ===");

        try {
            // Sync all data from NHL API
            dataIntegrationService.syncAllData();

            // Calculate hot ratings and streaks
            statisticsService.calculateHotRatings();

            log.info("=== Initial data synchronization completed successfully ===");
        } catch (Exception e) {
            log.error("Error during initial data synchronization. Application will continue but may have incomplete data.", e);
        }
    }
}
