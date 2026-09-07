package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.config.SchedulingConfig;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DataJobShutdownTest {
    @Test
    void closingContextInterruptsAnActiveSyncWithoutWaitingForStartup() throws Exception {
        var sync = mock(DataSyncService.class);
        var started = new CountDownLatch(1);
        var interrupted = new CountDownLatch(1);
        doAnswer(invocation -> {
            started.countDown();
            try {
                new CountDownLatch(1).await();
            } catch (InterruptedException e) {
                interrupted.countDown();
                Thread.currentThread().interrupt();
                throw new CancellationException();
            }
            return null;
        }).when(sync).syncPlayers();

        try (var context = context(sync)) {
            var scheduler = context.getBean(ThreadPoolTaskScheduler.class);
            context.getBean(DynamicSchedulingService.class).init();
            assertTrue(started.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(5), context::close);
            assertTrue(interrupted.await(1, TimeUnit.SECONDS));
            assertTrue(scheduler.getScheduledThreadPoolExecutor().isTerminated());
        }
    }

    @Test
    void closingContextCancelsFutureGameTask() throws Exception {
        var sync = mock(DataSyncService.class);
        var scheduled = new CountDownLatch(1);
        when(sync.getFirstGameTimeForToday()).thenReturn(LocalDateTime.now(ZoneOffset.UTC).plusHours(2));
        when(sync.getLastGameTimeForToday()).thenAnswer(invocation -> {
            scheduled.countDown();
            return LocalDateTime.now(ZoneOffset.UTC).plusHours(4);
        });
        try (var context = context(sync)) {
            var scheduler = context.getBean(ThreadPoolTaskScheduler.class);
            context.getBean(DynamicSchedulingService.class).init();
            assertTrue(scheduled.await(5, TimeUnit.SECONDS));
            assertTimeoutPreemptively(Duration.ofSeconds(5), context::close);
            assertTrue(scheduler.getScheduledThreadPoolExecutor().isTerminated());
            verify(sync, never()).syncPlayers();
        }
    }

    @Test
    void successfulOneShotClosesContextWithoutExitingJvm() throws Exception {
        var sync = mock(DataSyncService.class);
        var closed = new CountDownLatch(1);
        try (var context = context(sync)) {
            context.addApplicationListener(event -> {
                if (event instanceof ContextClosedEvent) {
                    closed.countDown();
                }
            });
            context.getBean(DynamicSchedulingService.class).init();
            assertTrue(closed.await(5, TimeUnit.SECONDS));
            verify(sync).initialize();
            verify(sync).syncPlayers();
        }
    }

    @Test
    void interruptedPlayerSyncStopsBeforeAnyApiCall() {
        var api = mock(NhlApiService.class);
        var sync = new DataSyncService(api, null, null, null, null);
        try {
            Thread.currentThread().interrupt();
            assertThrows(CancellationException.class, sync::syncPlayers);
        } finally {
            Thread.interrupted();
        }
        verifyNoInteractions(api);
    }

    private AnnotationConfigApplicationContext context(DataSyncService sync) {
        var context = new AnnotationConfigApplicationContext();
        context.register(SchedulingConfig.class);
        context.registerBean(DataSyncService.class, () -> sync);
        context.register(DynamicSchedulingService.class);
        context.refresh();
        return context;
    }
}
