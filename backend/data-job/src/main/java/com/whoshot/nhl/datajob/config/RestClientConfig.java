package com.whoshot.nhl.datajob.config;

import com.whoshot.nhl.datajob.service.RequestThrottle;
import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.Timeout;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

/**
 * Spring configuration that creates the shared {@link RestClient} bean.
 */
@Configuration
@EnableConfigurationProperties(ApiRequestProperties.class)
public class RestClientConfig {

    /**
     * Bounds connection and response waits and releases HTTP resources on shutdown. The pool holds
     * exactly as many connections as {@link RequestThrottle} lets requests run, and Apache's own
     * automatic retries are off so {@link com.whoshot.nhl.datajob.service.ApiClient} is the single
     * retry owner and attempts are never multiplied.
     */
    @Bean
    public HttpComponentsClientHttpRequestFactory nhlRequestFactory(ApiRequestProperties requestProperties) {
        // Spring Framework 7 removed setConnectTimeout; the connect timeout now lives on the HttpClient.
        CloseableHttpClient httpClient = HttpClients.custom()
                .useSystemProperties()
                .disableAutomaticRetries()
                .setConnectionManager(PoolingHttpClientConnectionManagerBuilder.create()
                        .useSystemProperties()
                        .setMaxConnTotal(requestProperties.maxInFlight())
                        .setMaxConnPerRoute(requestProperties.maxInFlight())
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
