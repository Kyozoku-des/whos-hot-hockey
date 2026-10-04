package com.whoshot.nhl.datajob.config;

import com.whoshot.nhl.datajob.service.InitialDataLoadService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Triggers a full initial data load when the application starts with the "initial-load" profile.
 */
@Slf4j
@Component
@Profile("initial-load")
@RequiredArgsConstructor
public class InitialLoadRunner {

    private final InitialDataLoadService initialDataLoadService;

    private final TaskScheduler taskScheduler;
    private final ConfigurableApplicationContext applicationContext;

    @EventListener(ApplicationReadyEvent.class)
    public void run() {
        taskScheduler.schedule(this::load, Instant.now());
    }

    private void load() {
        log.info("Initial-load profile active — starting full season data load");
        try {
            initialDataLoadService.loadFullSeason();
        } finally {
            Thread.ofPlatform().name("initial-load-shutdown").start(applicationContext::close);
        }
        log.info("Initial load runner completed");
    }
}
