package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.ApiRequestProperties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Bounded parallel fetching with an ordered single consumer (issue #29): bounds, ordering,
 * backpressure, failure isolation, cancellation and shutdown.
 */
class FetchPipelineTest {

    private final List<FetchPipeline> pipelines = new ArrayList<>();

    private FetchPipeline pipeline(int concurrency) {
        FetchPipeline pipeline = new FetchPipeline(concurrency);
        pipelines.add(pipeline);
        return pipeline;
    }

    @AfterEach
    void shutDown() throws InterruptedException {
        for (FetchPipeline pipeline : pipelines) {
            pipeline.destroy();
        }
    }

    private static List<Integer> inputs(int count) {
        return IntStream.range(0, count).boxed().toList();
    }

    private static void sleep(long millis) {
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("interrupted");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8})
    void resultsAreConsumedInInputOrder_despiteOutOfOrderCompletion(int concurrency) {
        List<Integer> consumed = new ArrayList<>();

        pipeline(concurrency).<Integer, Integer>run(inputs(40), input -> {
            sleep(ThreadLocalRandom.current().nextInt(0, 10));
            return input * 10;
        }, (input, result) -> {
            assertThat(result.value()).isEqualTo(input * 10);
            consumed.add(input);
        });

        assertThat(consumed).isEqualTo(inputs(40));
    }

    @Test
    void concurrentFetches_andOutstandingResults_stayWithinBounds() {
        FetchPipeline pipeline = pipeline(4);
        AtomicInteger running = new AtomicInteger();
        AtomicInteger peakRunning = new AtomicInteger();
        AtomicInteger submittedAhead = new AtomicInteger();
        AtomicInteger peakAhead = new AtomicInteger();

        pipeline.<Integer, Integer>run(inputs(60), input -> {
            peakAhead.accumulateAndGet(submittedAhead.incrementAndGet(), Math::max);
            peakRunning.accumulateAndGet(running.incrementAndGet(), Math::max);
            try {
                sleep(5);
                return input;
            } finally {
                running.decrementAndGet();
            }
        }, (input, result) -> {
            submittedAhead.decrementAndGet();
            sleep(2); // slow consumer: producers must wait for it, not pile up results
        });

        assertThat(peakRunning.get()).isLessThanOrEqualTo(4);
        assertThat(peakAhead.get()).isLessThanOrEqualTo(8);
        assertThat(pipeline.peakOutstanding()).isLessThanOrEqualTo(8);
    }

    @Test
    void fetchFailure_isHandedToTheConsumer_andOtherRecordsContinue() {
        List<String> outcomes = Collections.synchronizedList(new ArrayList<>());

        pipeline(4).<Integer, Integer>run(inputs(6), input -> {
            if (input == 2) {
                throw new IllegalStateException("bad record");
            }
            return input;
        }, (input, result) -> outcomes.add(result.succeeded() ? "ok" : result.failure().getMessage()));

        assertThat(outcomes).containsExactly("ok", "ok", "bad record", "ok", "ok", "ok");
    }

    @Test
    void consumerFailure_endsTheRun_andCancelsOutstandingFetches() throws Exception {
        CountDownLatch blocked = new CountDownLatch(1);
        AtomicInteger interrupted = new AtomicInteger();

        assertThatThrownBy(() -> pipeline(4).<Integer, Integer>run(inputs(20), input -> {
            if (input == 0) {
                return input;
            }
            blocked.countDown();
            try {
                TimeUnit.SECONDS.sleep(30);
            } catch (InterruptedException e) {
                interrupted.incrementAndGet();
                throw new CancellationException("cancelled");
            }
            return input;
        }, (input, result) -> {
            awaitQuietly(blocked);
            throw new IllegalStateException("database lost");
        })).isInstanceOf(IllegalStateException.class).hasMessage("database lost");

        // Outstanding fetches are interrupted rather than left running for 30 s.
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
        while (interrupted.get() == 0 && System.nanoTime() < deadline) {
            TimeUnit.MILLISECONDS.sleep(10);
        }
        assertThat(interrupted.get()).isPositive();
    }

    @Test
    void interruptingTheCaller_cancelsPromptly_includingRequestBudgetWaits() throws Exception {
        // A throttle allowing one request per 30 s: every fetch after the first waits on it.
        var throttle = new RequestThrottle(new ApiRequestProperties(4, 1.0 / 30, 1, 1, Duration.ofSeconds(1),
                Duration.ofSeconds(1), Duration.ofMinutes(5), 100, Duration.ofSeconds(30)));
        FetchPipeline pipeline = pipeline(4);
        var caller = new Thread[1];
        var run = CompletableFuture.runAsync(() -> {
            caller[0] = Thread.currentThread();
            pipeline.<Integer, Integer>run(inputs(20), input -> {
                var permit = throttle.acquire("api-web.nhle.com", System.nanoTime() + TimeUnit.MINUTES.toNanos(5));
                throttle.release(permit, true);
                return input;
            }, (input, result) -> {
            });
        }, runnable -> new Thread(runnable).start());

        TimeUnit.MILLISECONDS.sleep(300);
        long interruptedAt = System.nanoTime();
        caller[0].interrupt();

        assertThatThrownBy(() -> run.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(CancellationException.class);
        assertThat(Duration.ofNanos(System.nanoTime() - interruptedAt)).isLessThan(Duration.ofSeconds(1));
    }

    @Test
    void sequentialPipeline_runsOnTheCallingThread() {
        Thread caller = Thread.currentThread();
        List<Thread> fetchThreads = new ArrayList<>();

        pipeline(1).<Integer, Integer>run(inputs(3), input -> {
            fetchThreads.add(Thread.currentThread());
            return input;
        }, (input, result) -> {
        });

        assertThat(fetchThreads).containsOnly(caller);
    }

    @Test
    void destroy_interruptsAndReleasesWorkersWithinTheGracePeriod() throws Exception {
        FetchPipeline pipeline = new FetchPipeline(4);
        CountDownLatch started = new CountDownLatch(4);
        var run = CompletableFuture.runAsync(() -> pipeline.<Integer, Integer>run(inputs(8), input -> {
            started.countDown();
            TimeUnit.SECONDS.sleep(30);
            return input;
        }, (input, result) -> {
        }), runnable -> new Thread(runnable).start());
        assertThat(started.await(2, TimeUnit.SECONDS)).isTrue();

        long began = System.nanoTime();
        pipeline.destroy();

        assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(1));
        assertThatThrownBy(() -> run.get(2, TimeUnit.SECONDS)).hasCauseInstanceOf(CancellationException.class);
    }

    @Test
    void latencyBoundWork_isAtLeastTwiceAsFastWithFourWorkers() {
        Duration sequential = timeRun(pipeline(1));
        Duration parallel = timeRun(pipeline(4));

        assertThat(parallel.multipliedBy(2)).isLessThan(sequential);
    }

    private static Duration timeRun(FetchPipeline pipeline) {
        long started = System.nanoTime();
        pipeline.<Integer, Integer>run(inputs(24), input -> {
            sleep(25);
            return input;
        }, (input, result) -> sleep(1));
        return Duration.ofNanos(System.nanoTime() - started);
    }

    private static void awaitQuietly(CountDownLatch latch) {
        try {
            latch.await(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
