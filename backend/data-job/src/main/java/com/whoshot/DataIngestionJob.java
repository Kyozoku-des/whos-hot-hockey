package com.whoshot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the Data Ingestion Job.
 */
@SpringBootApplication
public class DataIngestionJob {

    public static void main(String[] args) {
        SpringApplication.run(DataIngestionJob.class, args);
    }
}
