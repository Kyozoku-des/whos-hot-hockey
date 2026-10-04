package com.whoshot.nhl.datajob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.persistence.autoconfigure.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application class for the Data Ingestion Job.
 */
@SpringBootApplication
@EntityScan("com.whoshot.nhl.domain.entity")
@EnableJpaRepositories("com.whoshot.nhl.domain.repository")
public class DataIngestionJob {

    /**
     * Bootstraps the Spring Boot data-ingestion application.
     *
     * @param args startup arguments passed from the runtime
     */
    public static void main(String[] args) {
        SpringApplication.run(DataIngestionJob.class, args);
    }
}
