package com.whoshot.nhl.datajob.service;

import com.whoshot.nhl.datajob.dto.nhlapi.PlayerInfoDto;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/**
 * Bounded, time-limited cache of player profiles ({@code /landing}) for game-time polls (issue #29).
 * <p>
 * A game-time poll needs a profile only to confirm a stored roster player is still active and on a
 * playing team; that rarely changes within a game window, so a poll reuses a profile fetched within
 * {@code ingestion.player-info-cache.ttl} instead of requesting it again every minute. Game logs and
 * standings, which do change, are never cached.
 * <p>
 * Every full sync fetches every profile fresh and overwrites its entry, reconciling the cache at
 * least hourly; a trade or retirement is therefore picked up by the next full sync or when the
 * entry expires, whichever comes first. Backfill never uses this cache: historical participation
 * must not depend on current team or activity.
 */
@Component
public class PlayerInfoCache {

    private record Entry(PlayerInfoDto info, Instant fetchedAt) {
    }

    private final Duration ttl;
    private final int maxEntries;
    private final Clock clock;
    private final Map<Long, Entry> entries = new ConcurrentHashMap<>();

    @Autowired
    public PlayerInfoCache(@Value("${ingestion.player-info-cache.ttl:30m}") Duration ttl,
                           @Value("${ingestion.player-info-cache.max-entries:2000}") int maxEntries) {
        this(ttl, maxEntries, Clock.systemUTC());
    }

    PlayerInfoCache(Duration ttl, int maxEntries, Clock clock) {
        this.ttl = ttl;
        this.maxEntries = maxEntries;
        this.clock = clock;
    }

    /**
     * Returns a cached profile younger than the TTL, or fetches, stores and returns a fresh one.
     */
    public PlayerInfoDto get(Long playerId, Supplier<PlayerInfoDto> fetch) {
        Entry entry = entries.get(playerId);
        if (entry != null && entry.fetchedAt().plus(ttl).isAfter(clock.instant())) {
            return entry.info();
        }
        return put(playerId, fetch.get());
    }

    /** Stores a freshly fetched profile, as every full sync does, and returns it. */
    public PlayerInfoDto put(Long playerId, PlayerInfoDto info) {
        entries.put(playerId, new Entry(info, clock.instant()));
        if (entries.size() > maxEntries) {
            evict();
        }
        return info;
    }

    /** Drops one player's profile, forcing the next poll to fetch it. */
    public void invalidate(Long playerId) {
        entries.remove(playerId);
    }

    int size() {
        return entries.size();
    }

    /** Removes expired entries, then the oldest ones, until the cache is back within its bound. */
    private synchronized void evict() {
        Instant expiredBefore = clock.instant().minus(ttl);
        entries.values().removeIf(entry -> entry.fetchedAt().isBefore(expiredBefore));
        int excess = entries.size() - maxEntries;
        if (excess > 0) {
            entries.entrySet().stream()
                    .sorted(Comparator.comparing(e -> e.getValue().fetchedAt()))
                    .limit(excess)
                    .map(Map.Entry::getKey)
                    .toList()
                    .forEach(entries::remove);
        }
    }
}
