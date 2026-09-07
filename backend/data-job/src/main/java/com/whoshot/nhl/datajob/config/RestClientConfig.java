package com.whoshot.nhl.datajob.config;

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
     * Bounds connection and response waits and releases HTTP resources on shutdown.
     */
    @Bean
    public HttpComponentsClientHttpRequestFactory nhlRequestFactory() {
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(5));
        requestFactory.setConnectionRequestTimeout(Duration.ofSeconds(5));
        requestFactory.setReadTimeout(Duration.ofSeconds(10));
        return requestFactory;
    }

    @Bean
    public RestClient restClient(RestClient.Builder builder, HttpComponentsClientHttpRequestFactory nhlRequestFactory) {
        return builder
                .requestFactory(nhlRequestFactory)
                .build();
    }
}
