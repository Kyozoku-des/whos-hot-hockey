package com.whoshot.nhl.datajob.service;

import com.sun.net.httpserver.HttpServer;
import com.whoshot.nhl.datajob.config.ApiRequestProperties;
import com.whoshot.nhl.datajob.config.RestClientConfig;
import com.whoshot.nhl.datajob.dto.SeasonDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import com.whoshot.nhl.datajob.dto.nhlapi.PlayerStandingDto;
import com.whoshot.nhl.datajob.factory.PlayerFactory;
import com.whoshot.nhl.domain.entity.Player;
import com.whoshot.nhl.domain.repository.PlayerRepository;
import com.whoshot.nhl.domain.repository.TeamGameRepository;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.ValueAtPercentile;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.LongStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Latency-bound benchmark of a full player sync at 1, 2, 4 and 8 fetch workers (issue #29).
 * <p>
 * Every profile and game-log request is a real HTTP call, through the production
 * {@link RestClientConfig} request factory, {@link ApiClient} and {@link RequestThrottle}, to a
 * local stub that answers after a fixed delay. The rate budget is set high so that latency, not
 * the budget, is the limit; the in-flight cap is 8 for every run so the configurations differ only
 * in worker count. Persistence is a mock that takes a fixed time per player.
 * <p>
 * This measures the pipeline in a deliberately latency-bound scenario. It says nothing about the
 * throughput the public NHL API allows, where the configured request rate is the real limit.
 */
@Tag("benchmark")
class IngestionBenchmarkTest {

    private static final int PLAYERS = 32;
    private static final Duration LATENCY = Duration.ofMillis(30);
    private static final Duration PERSIST = Duration.ofMillis(2);
    private static final int REPETITIONS = 3;
    private static final int MAX_IN_FLIGHT = 8;
    private static final String SEASON = "20252026";
    private static final ParameterizedTypeReference<Map<String, String>> JSON = new ParameterizedTypeReference<>() {
    };

    private HttpServer server;
    private ExecutorService serverThreads;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger concurrent = new AtomicInteger();
    private final AtomicInteger peakConcurrent = new AtomicInteger();

    @BeforeEach
    void startStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        serverThreads = Executors.newFixedThreadPool(32);
        server.setExecutor(serverThreads);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            peakConcurrent.accumulateAndGet(concurrent.incrementAndGet(), Math::max);
            try {
                TimeUnit.NANOSECONDS.sleep(LATENCY.toNanos());
                byte[] body = "{\"ok\":\"yes\"}".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                concurrent.decrementAndGet();
                exchange.close();
            }
        });
        server.start();
    }

    @AfterEach
    void stopStub() {
        server.stop(0);
        serverThreads.shutdownNow();
    }

    private record Measurement(int workers, Duration median, Duration worst, int requests, int peakInFlight,
                               Duration latencyP50, Duration latencyP95) {
    }

    @Test
    void fourWorkersAreAtLeastTwiceAsFastAsOne_whenLatencyIsTheLimit() throws Exception {
        Map<Integer, Measurement> results = new LinkedHashMap<>();
        for (int workers : new int[]{1, 2, 4, 8}) {
            results.put(workers, measure(workers));
        }

        System.out.printf("%nFull player sync, %d players, %d ms stub latency, %d ms persist, median/worst of %d runs%n",
                PLAYERS, LATENCY.toMillis(), PERSIST.toMillis(), REPETITIONS);
        System.out.println("workers | median ms | worst ms | requests/run | peak in-flight | http p50 ms | http p95 ms");
        results.values().forEach(m -> System.out.printf("%7d | %9d | %8d | %12d | %14d | %11d | %11d%n",
                m.workers(), m.median().toMillis(), m.worst().toMillis(), m.requests(), m.peakInFlight(),
                m.latencyP50().toMillis(), m.latencyP95().toMillis()));

        Duration sequential = results.get(1).median();
        Duration fourWorkers = results.get(4).median();
        assertThat(fourWorkers.multipliedBy(2)).isLessThan(sequential);
        results.values().forEach(m -> {
            assertThat(m.requests()).isEqualTo(PLAYERS * 2);
            assertThat(m.peakInFlight()).isLessThanOrEqualTo(Math.min(m.workers(), MAX_IN_FLIGHT));
        });
    }

    private Measurement measure(int workers) throws Exception {
        List<Duration> durations = new ArrayList<>();
        int requestsPerRun = 0;
        int peak = 0;
        IngestionMetrics metrics = IngestionMetrics.standalone();
        for (int run = 0; run < REPETITIONS; run++) {
            requests.set(0);
            peakConcurrent.set(0);
            FetchPipeline pipeline = new FetchPipeline(workers);
            try {
                DataSyncService sync = syncService(pipeline, metrics);
                long started = System.nanoTime();
                DataSyncService.SyncResult result = sync.syncPlayers();
                durations.add(Duration.ofNanos(System.nanoTime() - started));
                assertThat(result.written()).isEqualTo(PLAYERS);
            } finally {
                pipeline.destroy();
            }
            requestsPerRun = requests.get();
            peak = Math.max(peak, peakConcurrent.get());
        }
        durations.sort(null);
        Timer timer = metrics.registry().find("nhl.api.requests").tag("outcome", "success").timer();
        Map<Double, Duration> percentiles = new LinkedHashMap<>();
        for (ValueAtPercentile value : timer.takeSnapshot().percentileValues()) {
            percentiles.put(value.percentile(), Duration.ofNanos((long) value.value(TimeUnit.NANOSECONDS)));
        }
        return new Measurement(workers, durations.get(REPETITIONS / 2), durations.getLast(), requestsPerRun, peak,
                percentiles.get(0.5), percentiles.get(0.95));
    }

    private DataSyncService syncService(FetchPipeline pipeline, IngestionMetrics metrics) throws Exception {
        var properties = new ApiRequestProperties(MAX_IN_FLIGHT, 10_000, 100, 3, Duration.ofMillis(100),
                Duration.ofSeconds(1), Duration.ofSeconds(30), 100, Duration.ofSeconds(30));
        HttpComponentsClientHttpRequestFactory requestFactory = new RestClientConfig().nhlRequestFactory(properties);
        RestClient restClient = RestClient.builder().requestFactory(requestFactory).build();
        ApiClient apiClient = new ApiClient(restClient, new RequestThrottle(properties), properties, metrics);
        String base = "http://127.0.0.1:" + server.getAddress().getPort();

        NhlApiService api = mock(NhlApiService.class);
        var season = new SeasonDto();
        season.setId(SEASON);
        season.setStartDate(LocalDateTime.parse("2025-10-07T17:00:00"));
        when(api.getSeasons()).thenReturn(List.of(season));
        when(api.getLeagueSchedule()).thenReturn(List.of());
        when(api.getPlayerStandingsOrder(SEASON, 2)).thenReturn(LongStream.rangeClosed(1, PLAYERS)
                .mapToObj(IngestionBenchmarkTest::standing).toList());
        when(api.getPlayerInfo(anyLong())).thenAnswer(invocation -> {
            long id = invocation.getArgument(0);
            apiClient.get(base + "/v1/player/" + id + "/landing", JSON);
            return info(id);
        });
        when(api.getPlayerGameLogs(anyLong(), eq(SEASON), eq(2))).thenAnswer(invocation -> {
            apiClient.get(base + "/v1/player/" + invocation.getArgument(0) + "/game-log/" + SEASON + "/2", JSON);
            return List.of();
        });

        PlayerFactory factory = mock(PlayerFactory.class);
        when(factory.createFromApiData(any(), any(), any(), anyString())).thenAnswer(invocation ->
                Player.builder().id(new Player.PlayerId(invocation.<PlayerInfoDto>getArgument(0).getPlayerId(), SEASON))
                        .build());
        SeasonDataWriter writer = mock(SeasonDataWriter.class);
        doAnswer(invocation -> {
            TimeUnit.NANOSECONDS.sleep(PERSIST.toNanos());
            return 0;
        }).when(writer).writePlayer(any(), anyList(), any());

        DataSyncService sync = new DataSyncService(api, mock(PlayerRepository.class), factory, writer,
                mock(TeamGameRepository.class), mock(GameLogWriter.class), pipeline,
                new PlayerInfoCache(Duration.ofMinutes(30), 100));
        sync.setMetrics(metrics);
        sync.initialize();
        return sync;
    }

    private static PlayerStandingDto standing(long id) {
        var standing = new PlayerStandingDto();
        standing.setId(id);
        return standing;
    }

    private static PlayerInfoDto info(long id) {
        var info = new PlayerInfoDto();
        info.setPlayerId(id);
        info.setActive(true);
        info.setCurrentTeamAbbrev("EDM");
        return info;
    }
}
