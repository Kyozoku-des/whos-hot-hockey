package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Game-time profile cache (issue #29): reuse within the TTL, refresh after it, explicit
 * invalidation, and a bounded size.
 */
class PlayerInfoCacheTest {

    private static final class MutableClock extends Clock {
        private Instant now = Instant.parse("2026-01-01T00:00:00Z");

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration duration) {
            now = now.plus(duration);
        }
    }

    private final MutableClock clock = new MutableClock();
    private final AtomicInteger fetches = new AtomicInteger();

    private PlayerInfoDto fetch() {
        fetches.incrementAndGet();
        return new PlayerInfoDto();
    }

    @Test
    void reusesAProfileWithinTheTtl_andRefetchesAfterIt() {
        var cache = new PlayerInfoCache(Duration.ofMinutes(30), 100, clock);

        cache.get(1L, this::fetch);
        clock.advance(Duration.ofMinutes(29));
        cache.get(1L, this::fetch);
        assertThat(fetches).hasValue(1);

        clock.advance(Duration.ofMinutes(2));
        cache.get(1L, this::fetch);
        assertThat(fetches).hasValue(2);
    }

    @Test
    void invalidate_forcesARefetch() {
        var cache = new PlayerInfoCache(Duration.ofMinutes(30), 100, clock);
        cache.get(1L, this::fetch);

        cache.invalidate(1L);
        cache.get(1L, this::fetch);

        assertThat(fetches).hasValue(2);
    }

    @Test
    void size_staysBounded_evictingTheOldestFirst() {
        var cache = new PlayerInfoCache(Duration.ofHours(1), 3, clock);
        for (long id = 1; id <= 5; id++) {
            cache.get(id, this::fetch);
            clock.advance(Duration.ofSeconds(1));
        }

        assertThat(cache.size()).isEqualTo(3);
        cache.get(5L, this::fetch);
        assertThat(fetches).hasValue(5);
        cache.get(1L, this::fetch);
        assertThat(fetches).hasValue(6);
    }
}
