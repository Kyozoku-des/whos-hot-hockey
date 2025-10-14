package com.nhl.whoshotbackend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Main application class for the NHL "Who's Hot" Backend Service.
 * This application provides REST APIs for NHL statistics with focus on
 * identifying "hot" and "cold" players and teams based on recent performance.
 */
@SpringBootApplication
@EnableScheduling
public class WhosHotBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(WhosHotBackendApplication.class, args);
    }
}
