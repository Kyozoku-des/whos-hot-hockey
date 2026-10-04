package com.whoshot.nhl.datajob.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Bounded parallel fetching with a single, ordered persistence consumer (issue #29).
 * <p>
 * A dedicated pool of {@code ingestion.fetch.concurrency} platform threads runs fetch tasks, which
 * must only make HTTP calls and build plain values: never touch JPA or shared mutable state. The
 * calling thread is the only consumer: it receives each result in input order and does all
 * persistence and summary bookkeeping itself, so transactions stay on one thread and completion
 * order never changes the outcome.
 * <p>
 * At most {@code 2 × concurrency} tasks are submitted but not yet consumed, so fetched-but-unwritten
 * results stay bounded and a slow consumer stops new fetches (backpressure). While the consumer
 * writes one record, workers fetch the next ones.
 * <p>
 * A fetch failure is handed to the consumer as a {@link Result#failure()} to skip or report. An
 * exception from the consumer, a {@link CancellationException} from a fetch, or an interrupt of the
 * calling thread ends the run and cancels (interrupts) every outstanding fetch. With concurrency 1
 * no pool is used and work runs sequentially on the calling thread, the original behaviour.
 */
@Slf4j
@Component
public class FetchPipeline implements DisposableBean {

    /** How long shutdown waits for interrupted workers before giving up on them. */
    static final Duration SHUTDOWN_GRACE = Duration.ofSeconds(5);

    private final int concurrency;
    private final int window;
    private final ThreadPoolExecutor executor;
    private final AtomicInteger peakOutstanding = new AtomicInteger();
    private final AtomicInteger outstandingNow = new AtomicInteger();

    public FetchPipeline(@Value("${ingestion.fetch.concurrency:4}") int concurrency) {
        if (concurrency < 1) {
            throw new IllegalArgumentException("ingestion.fetch.concurrency must be at least 1");
        }
        this.concurrency = concurrency;
        this.window = concurrency * 2;
        if (concurrency == 1) {
            this.executor = null;
        } else {
            AtomicInteger threadNumber = new AtomicInteger();
            this.executor = new ThreadPoolExecutor(concurrency, concurrency, 0, TimeUnit.MILLISECONDS,
                    // Each run keeps at most `window` tasks outstanding; the bounded queue and
                    // caller-runs fallback only matter if two runs ever overlap in one process.
                    new ArrayBlockingQueue<>(window * 2),
                    runnable -> Thread.ofPlatform()
                            .name("ingestion-fetch-" + threadNumber.incrementAndGet())
                            .unstarted(runnable),
                    (task, pool) -> {
                        if (pool.isShutdown()) {
                            throw new RejectedExecutionException("Fetch pipeline is shut down");
                        }
                        task.run();
                    });
        }
        log.info("Ingestion fetch concurrency: {}", concurrency);
    }

    /** A pipeline that runs everything on the calling thread, for tests and rollback. */
    public static FetchPipeline sequential() {
        return new FetchPipeline(1);
    }

    /** A fetch task; runs on a worker thread. */
    @FunctionalInterface
    public interface Fetch<I, T> {
        T fetch(I input) throws Exception;
    }

    /** The persistence step; runs on the calling thread, in input order. */
    @FunctionalInterface
    public interface Consumer<I, T> {
        void accept(I input, Result<T> result);
    }

    /** The outcome of one fetch: a value, or the exception the fetch threw. */
    public record Result<T>(T value, Exception failure) {
        public boolean succeeded() {
            return failure == null;
        }
    }

    /**
     * Fetches every input and hands each result to {@code consumer} in input order.
     *
     * @throws CancellationException if the calling thread is interrupted or a fetch was cancelled
     */
    public <I, T> void run(List<I> inputs, Fetch<I, T> fetch, Consumer<I, T> consumer) {
        if (executor == null) {
            for (I input : inputs) {
                checkInterrupted();
                consumer.accept(input, invoke(fetch, input));
            }
            return;
        }

        Deque<Pending<I, T>> outstanding = new ArrayDeque<>();
        Iterator<I> remaining = inputs.iterator();
        try {
            while (true) {
                while (outstanding.size() < window && remaining.hasNext()) {
                    checkInterrupted();
                    I input = remaining.next();
                    outstanding.add(new Pending<>(input, submit(fetch, input)));
                    outstandingNow.incrementAndGet();
                    peakOutstanding.accumulateAndGet(outstanding.size(), Math::max);
                }
                // Stays tracked while awaited, so an interrupt here cancels it with the rest.
                Pending<I, T> next = outstanding.peek();
                if (next == null) {
                    return;
                }
                Result<T> result = await(next.future());
                outstanding.poll();
                outstandingNow.decrementAndGet();
                consumer.accept(next.input(), result);
            }
        } finally {
            outstanding.forEach(pending -> pending.future().cancel(true));
            outstandingNow.addAndGet(-outstanding.size());
        }
    }

    /** Configured number of fetch workers. */
    public int concurrency() {
        return concurrency;
    }

    /** Fetches currently submitted and not yet consumed, across all runs. */
    public int outstanding() {
        return outstandingNow.get();
    }

    /** Highest number of fetches outstanding at once since startup; never above twice the concurrency. */
    public int peakOutstanding() {
        return peakOutstanding.get();
    }

    /**
     * Interrupts running fetches (aborting their throttle, backoff and queue waits) and releases the
     * worker threads. A fetch blocked on a socket read ends within the HTTP read timeout.
     */
    @Override
    public void destroy() throws InterruptedException {
        if (executor == null) {
            return;
        }
        executor.shutdownNow();
        if (!executor.awaitTermination(SHUTDOWN_GRACE.toMillis(), TimeUnit.MILLISECONDS)) {
            log.warn("Fetch workers still running {} after shutdown; abandoning them", SHUTDOWN_GRACE);
        }
    }

    private <I, T> Future<T> submit(Fetch<I, T> fetch, I input) {
        try {
            return executor.submit(() -> fetch.fetch(input));
        } catch (RejectedExecutionException e) {
            throw new CancellationException(e.getMessage());
        }
    }

    private static <I, T> Result<T> invoke(Fetch<I, T> fetch, I input) {
        try {
            return new Result<>(fetch.fetch(input), null);
        } catch (CancellationException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Fetch interrupted");
        } catch (Exception e) {
            return new Result<>(null, e);
        }
    }

    private static <T> Result<T> await(Future<T> future) {
        try {
            return new Result<>(future.get(), null);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CancellationException("Interrupted while waiting for fetched data");
        } catch (CancellationException e) {
            throw e;
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof CancellationException cancelled) {
                throw cancelled;
            }
            if (cause instanceof InterruptedException) {
                throw new CancellationException("Fetch interrupted");
            }
            if (cause instanceof Error error) {
                throw error;
            }
            return new Result<>(null, (Exception) cause);
        }
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) {
            throw new CancellationException("Synchronization was stopped");
        }
    }

    private record Pending<I, T>(I input, Future<T> future) {
    }
}
