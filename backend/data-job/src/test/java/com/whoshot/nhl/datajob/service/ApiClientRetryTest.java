package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.ResilienceConfig;
import com.whoshot.nhl.datajob.exception.NonRetryableApiClientException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Verifies that {@link ApiClient} retries transient failures through the Spring proxy (issue #31).
 */
@SpringJUnitConfig(ApiClientRetryTest.TestConfig.class)
class ApiClientRetryTest {

    private static final String URL = "https://api-web.nhle.com/v1/test";
    private static final ParameterizedTypeReference<Map<String, String>> TYPE = new ParameterizedTypeReference<>() {
    };

    @Configuration
    @Import({ResilienceConfig.class, ApiClient.class})
    static class TestConfig {

        private final RestClient.Builder builder = RestClient.builder();

        @Bean
        MockRestServiceServer server() {
            return MockRestServiceServer.bindTo(builder).build();
        }

        @Bean
        RestClient restClient(MockRestServiceServer server) {
            return builder.build();
        }
    }

    @Autowired
    private ApiClient apiClient;

    @Autowired
    private MockRestServiceServer server;

    @AfterEach
    void reset() {
        server.reset();
    }

    @Test
    void transientServerError_isRetried() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("{\"ok\":\"yes\"}", MediaType.APPLICATION_JSON));

        Map<String, String> result = apiClient.get(URL, TYPE);

        assertThat(result).containsEntry("ok", "yes");
        server.verify();
    }

    @Test
    void clientError_isNotRetried() {
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> apiClient.get(URL, TYPE))
                .isInstanceOf(NonRetryableApiClientException.class)
                .hasMessageContaining("404");
        server.verify();
    }
}
