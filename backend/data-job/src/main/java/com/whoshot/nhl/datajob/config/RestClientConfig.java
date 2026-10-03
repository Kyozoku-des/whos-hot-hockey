package com.whoshot.nhl.datajob.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
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
        // Spring Framework 7 removed setConnectTimeout; the connect timeout now lives on the HttpClient.
        CloseableHttpClient httpClient = HttpClients.custom()
                .useSystemProperties()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .useSystemProperties()
                        .setDefaultConnectionConfig(ConnectionConfig.custom()
                                .setConnectTimeout(Timeout.ofSeconds(5))
                                .build())
                        .build())
                .build();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory(httpClient);
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
