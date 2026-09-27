package com.whoshot.nhl.datajob;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Main application class for the Data Ingestion Job.
 */
@SpringBootApplication(scanBasePackages = "com.whoshot.nhl")
@EntityScan(basePackages = "com.whoshot.nhl.domain.entity")
@EnableJpaRepositories(basePackages = "com.whoshot.nhl.domain.repository")
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
