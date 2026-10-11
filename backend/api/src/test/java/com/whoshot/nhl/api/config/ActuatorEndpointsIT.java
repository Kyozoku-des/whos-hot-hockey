package com.whoshot.nhl.api.config;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Health and Prometheus metrics are served on the separate management port only, so publishing or
 * proxying the API port can never expose them.
 */
@Tag("integration")
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "management.server.port=0")
class ActuatorEndpointsIT {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer(DockerImageName.parse("postgres:16"));

    private final HttpClient client = HttpClient.newHttpClient();

    @Value("${local.server.port}")
    private int serverPort;
    @Value("${local.management.port}")
    private int managementPort;

    @Test
    void managementPortServesHealthAndTaggedMetrics() throws Exception {
        assertThat(get(managementPort, "/actuator/health").statusCode()).isEqualTo(200);

        HttpResponse<String> metrics = get(managementPort, "/actuator/prometheus");
        assertThat(metrics.statusCode()).isEqualTo(200);
        assertThat(metrics.body())
                .contains("jvm_memory_used_bytes")
                .contains("application=\"nhl-api\"");
    }

    @Test
    void apiPortDoesNotServeActuator() throws Exception {
        assertThat(managementPort).isNotEqualTo(serverPort);
        assertThat(get(serverPort, "/actuator/prometheus").statusCode()).isEqualTo(404);
        assertThat(get(serverPort, "/actuator/health").statusCode()).isEqualTo(404);
    }

    private HttpResponse<String> get(int port, String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
