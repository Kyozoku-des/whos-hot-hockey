package com.whoshot.nhl.datajob.config;

import com.whoshot.nhl.datajob.service.InitialDataLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Triggers a full initial data load when the application starts with the "initial-load" profile.
 */
@Slf4j
@Component
@Profile("initial-load")
@RequiredArgsConstructor
public class InitialLoadRunner implements CommandLineRunner {

    private final InitialDataLoadService initialDataLoadService;

    @Override
    public void run(String... args) {
        log.info("Initial-load profile active — starting full season data load");
        initialDataLoadService.loadFullSeason();
        log.info("Initial load runner completed");
    }
}
