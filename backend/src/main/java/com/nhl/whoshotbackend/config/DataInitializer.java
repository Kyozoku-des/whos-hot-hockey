package com.nhl.whoshotbackend.config;

import com.nhl.whoshotbackend.service.DataIntegrationService;
import com.nhl.whoshotbackend.service.StatisticsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Initializes data on application startup.
 * Does NOT sync data - scheduled jobs handle that.
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
        log.info("=== Application startup - Data initialization ===");

        try {
            // Ensure current season is set in the database
            // This does NOT sync data from NHL API on startup
            dataIntegrationService.ensureCurrentSeasonExists();

            log.info("=== Data initialization completed ===");
            log.info("=== Scheduled jobs will handle data synchronization ===");
        } catch (Exception e) {
            log.error("Error during data initialization.", e);
        }
    }
}
