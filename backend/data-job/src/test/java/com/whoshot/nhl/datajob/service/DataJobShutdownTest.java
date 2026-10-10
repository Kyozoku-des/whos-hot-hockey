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
            verify(sync).syncPlayers();
        }
    }

    @Test
    void noGameDayKeepsDaemonRunningAndSchedulesNextCheck() throws Exception {
        var sync = mock(DataSyncService.class);
        var finished = new CountDownLatch(1);
        when(sync.getLastGameTimeForToday()).thenAnswer(invocation -> {
            finished.countDown();
            return null;
        });
        try (var context = context(sync)) {
            context.getBean(DynamicSchedulingService.class).init();
            assertTrue(finished.await(5, TimeUnit.SECONDS));
            assertTrue(context.isActive());
            verify(sync).initialize();
            verify(sync).syncTeams();
            verify(sync).syncPlayers();
        }
    }

    @Test
    void interruptedPlayerSyncStopsBeforeAnyApiCall() {
        var api = mock(NhlApiService.class);
        var sync = new DataSyncService(api, null, null, null, null, null, FetchPipeline.sequential(),
                new PlayerInfoCache(java.time.Duration.ofMinutes(30), 100));
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
        context.registerBean(BackfillLockService.class, TestSeasonLocks::available);
        context.registerBean(ScoreboardSyncService.class, () -> mock(ScoreboardSyncService.class));
        context.register(DynamicSchedulingService.class);
        context.refresh();
        return context;
    }
}
