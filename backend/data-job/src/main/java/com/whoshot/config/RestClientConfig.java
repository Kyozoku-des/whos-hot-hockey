package com.whoshot.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration that creates the shared {@link RestClient} bean.
 */
@Configuration
public class RestClientConfig {

    /**
     * Builds a {@link RestClient} configured with request factory timeouts.
     *
     * @param builder builder injected by Spring Boot
     * @return configured RestClient instance
     */
    @Bean
    public RestClient restClient(RestClient.Builder builder) {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setReadTimeout(Duration.ofSeconds(10));

        return builder
                .requestFactory(requestFactory)
                .build();
    }
}
