package com.animallocket.common.kv;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cài đặt trong bộ nhớ, an toàn đa luồng (mọi thao tác ghi đi qua {@link ConcurrentHashMap#compute}).
 * Key hết hạn bị xoá lười khi đọc/ghi.
 */
public class InMemoryKeyValueStore implements KeyValueStore {
    private record Entry(String value, Instant expiresAt) {
        boolean expired(Instant now) {
            return expiresAt != null && !expiresAt.isAfter(now);
        }
    }

    private final Map<String, Entry> data = new ConcurrentHashMap<>();
    private final Clock clock;

    public InMemoryKeyValueStore() {
        this(Clock.systemUTC());
    }

    /** Cho test: điều khiển thời gian thay vì sleep. */
    public InMemoryKeyValueStore(Clock clock) {
        this.clock = clock;
    }

    private Instant now() {
        return clock.instant();
    }

    private Instant expiry(Duration ttl) {
        return ttl == null ? null : now().plus(ttl);
    }

    @Override
    public long increment(String key, Duration ttlIfNew) {
        return Long.parseLong(data.compute(key, (k, old) -> {
            if (old == null || old.expired(now())) {
                return new Entry("1", expiry(ttlIfNew));
            }
            return new Entry(String.valueOf(Long.parseLong(old.value) + 1), old.expiresAt);
        }).value);
    }

    @Override
    public void decrement(String key) {
        data.computeIfPresent(key, (k, old) -> old.expired(now()) ? null
                : new Entry(String.valueOf(Math.max(0, Long.parseLong(old.value) - 1)), old.expiresAt));
    }

    @Override
    public Optional<String> get(String key) {
        Entry e = data.get(key);
        if (e == null) {
            return Optional.empty();
        }
        if (e.expired(now())) {
            data.remove(key, e);
            return Optional.empty();
        }
        return Optional.of(e.value);
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        data.put(key, new Entry(value, expiry(ttl)));
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        boolean[] created = {false};
        data.compute(key, (k, old) -> {
            if (old == null || old.expired(now())) {
                created[0] = true;
                return new Entry(value, expiry(ttl));
            }
            return old;
        });
        return created[0];
    }

    @Override
    public void delete(String... keys) {
        for (String k : keys) {
            data.remove(k);
        }
    }
}
