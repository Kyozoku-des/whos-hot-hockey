package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.ApiRequestProperties;
import com.whoshot.nhl.datajob.exception.ApiClientException;
import com.whoshot.nhl.datajob.exception.NonRetryableApiClientException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.time.Duration;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Retry policy of {@link ApiClient} (issue #29): which failures retry, how many times, how long it
 * waits, and that cancellation and deadlines stop it.
 */
class ApiClientRetryTest {

    private static final String URL = "https://api-web.nhle.com/v1/test";
    private static final ParameterizedTypeReference<Map<String, String>> TYPE = new ParameterizedTypeReference<>() {
    };

    private MockRestServiceServer server;

    private ApiClient client(Duration backoff, Duration deadline) {
        var properties = new ApiRequestProperties(4, 1000, 10, 3, backoff, backoff.multipliedBy(4),
                deadline, 100, Duration.ofSeconds(30));
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        return new ApiClient(builder.build(), new RequestThrottle(properties), properties, IngestionMetrics.standalone());
    }

    private ApiClient client() {
        return client(Duration.ofMillis(10), Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(ints = {408, 429, 500, 502, 503, 504})
    void transientStatus_isRetried(int status) {
        ApiClient apiClient = client();
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("{\"ok\":\"yes\"}", MediaType.APPLICATION_JSON));

        assertThat(apiClient.get(URL, TYPE)).containsEntry("ok", "yes");
        server.verify();
    }

    @ParameterizedTest
    @ValueSource(ints = {400, 401, 403, 404, 410, 501})
    void permanentStatus_isNotRetried(int status) {
        ApiClient apiClient = client();
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.valueOf(status)));

        assertThatThrownBy(() -> apiClient.get(URL, TYPE))
                .isInstanceOf(NonRetryableApiClientException.class)
                .hasMessageContaining(String.valueOf(status))
                .satisfies(e -> assertThat(((ApiClientException) e).status()).hasValue(status));
        server.verify();
    }

    @Test
    void transportFailure_isRetried() {
        ApiClient apiClient = client();
        server.expect(once(), requestTo(URL)).andRespond(withException(new IOException("connection reset")));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("{\"ok\":\"yes\"}", MediaType.APPLICATION_JSON));

        assertThat(apiClient.get(URL, TYPE)).containsEntry("ok", "yes");
        server.verify();
    }

    @Test
    void invalidPayload_isNotRetried() {
        ApiClient apiClient = client();
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("not json", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> apiClient.get(URL, TYPE)).isInstanceOf(NonRetryableApiClientException.class);
        server.verify();
    }

    @Test
    void attemptsAreCapped() {
        ApiClient apiClient = client();
        server.expect(times(3), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));

        assertThatThrownBy(() -> apiClient.get(URL, TYPE))
                .isInstanceOf(NonRetryableApiClientException.class)
                .hasMessageContaining("Giving up after 3 attempts");
        server.verify();
    }

    @Test
    void retryAfterBeyondDeadline_failsVisiblyWithoutWaiting() {
        ApiClient apiClient = client(Duration.ofMillis(10), Duration.ofSeconds(2));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, "60"));

        long started = System.nanoTime();
        assertThatThrownBy(() -> apiClient.get(URL, TYPE))
                .isInstanceOf(NonRetryableApiClientException.class)
                .hasMessageContaining("deadline");
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isLessThan(Duration.ofSeconds(1));
        server.verify();
    }

    @Test
    void retryAfter_isHonouredBeforeRetrying() {
        ApiClient apiClient = client();
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, "1"));
        server.expect(once(), requestTo(URL)).andRespond(withSuccess("{\"ok\":\"yes\"}", MediaType.APPLICATION_JSON));

        long started = System.nanoTime();
        apiClient.get(URL, TYPE);
        assertThat(Duration.ofNanos(System.nanoTime() - started)).isGreaterThanOrEqualTo(Duration.ofMillis(950));
        server.verify();
    }

    @Test
    void interruptDuringBackoff_cancelsPromptly() throws Exception {
        ApiClient apiClient = client(Duration.ofSeconds(20), Duration.ofSeconds(60));
        server.expect(once(), requestTo(URL)).andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        var interruptKept = new AtomicBoolean();
        var worker = new Thread[1];
        var result = CompletableFuture.runAsync(() -> {
            worker[0] = Thread.currentThread();
            try {
                apiClient.get(URL, TYPE);
            } finally {
                interruptKept.set(Thread.interrupted());
            }
        }, runnable -> new Thread(runnable).start());

        TimeUnit.MILLISECONDS.sleep(300);
        worker[0].interrupt();

        assertThatThrownBy(() -> result.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(CancellationException.class);
        assertThat(interruptKept).isTrue();
    }

    @Test
    void interruptedCaller_sendsNoRequest() {
        ApiClient apiClient = client();
        try {
            Thread.currentThread().interrupt();
            assertThatThrownBy(() -> apiClient.get(URL, TYPE)).isInstanceOf(CancellationException.class);
        } finally {
            Thread.interrupted();
        }
        server.verify();
    }

    @Test
    void backoff_growsWithJitterAndIsCapped() {
        ApiClient apiClient = client(Duration.ofMillis(1000), Duration.ofSeconds(10));
        for (int i = 0; i < 50; i++) {
            assertThat(apiClient.backoff(1)).isBetween(Duration.ofMillis(500), Duration.ofMillis(1000));
            assertThat(apiClient.backoff(2)).isBetween(Duration.ofMillis(1000), Duration.ofMillis(2000));
            assertThat(apiClient.backoff(10)).isBetween(Duration.ofMillis(2000), Duration.ofMillis(4000));
        }
    }

    @Test
    void parseRetryAfter_handlesSecondsDatesAndBadValues() {
        assertThat(ApiClient.parseRetryAfter("5")).isEqualTo(Duration.ofSeconds(5));
        assertThat(ApiClient.parseRetryAfter("3600")).isEqualTo(Duration.ofHours(1));
        assertThat(ApiClient.parseRetryAfter("999999")).isEqualTo(ApiClient.MAX_RETRY_AFTER);
        assertThat(ApiClient.parseRetryAfter("-1")).isZero();
        assertThat(ApiClient.parseRetryAfter(null)).isNull();
        assertThat(ApiClient.parseRetryAfter("soon")).isNull();

        String future = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).plusSeconds(10));
        assertThat(ApiClient.parseRetryAfter(future)).isBetween(Duration.ofSeconds(8), Duration.ofSeconds(10));

        String past = DateTimeFormatter.RFC_1123_DATE_TIME.format(ZonedDateTime.now(ZoneOffset.UTC).minusSeconds(10));
        assertThat(ApiClient.parseRetryAfter(past)).isZero();
    }
}
