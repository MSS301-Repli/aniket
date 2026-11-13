package com.animallocket.common.kv;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InMemoryKeyValueStoreTest extends KeyValueStoreContract {

    /** Đồng hồ điều khiển được: test TTL không cần sleep. */
    static final class MutableClock extends Clock {
        Instant now = Instant.parse("2026-11-13T00:00:00Z");

        void advance(Duration d) {
            now = now.plus(d);
        }

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
    }

    private final MutableClock clock = new MutableClock();
    private final InMemoryKeyValueStore kv = new InMemoryKeyValueStore(clock);

    @Override
    protected KeyValueStore store() {
        return kv;
    }

    @Test
    void expiredKeysDisappear() {
        kv.set("k", "v", Duration.ofSeconds(30));
        clock.advance(Duration.ofSeconds(29));
        assertTrue(kv.get("k").isPresent());
        clock.advance(Duration.ofSeconds(1));
        assertTrue(kv.get("k").isEmpty());
    }

    @Test
    void counterRestartsAfterExpiryAndKeepsOriginalTtl() {
        kv.increment("c", Duration.ofSeconds(10));
        clock.advance(Duration.ofSeconds(6));
        assertEquals(2, kv.increment("c", Duration.ofHours(1))); // TTL không bị kéo dài
        clock.advance(Duration.ofSeconds(4));
        assertEquals(1, kv.increment("c", Duration.ofSeconds(10)));
    }

    @Test
    void setIfAbsentSucceedsAgainAfterExpiry() {
        assertTrue(kv.setIfAbsent("lock", "a", Duration.ofSeconds(5)));
        clock.advance(Duration.ofSeconds(5));
        assertTrue(kv.setIfAbsent("lock", "b", Duration.ofSeconds(5)));
        assertEquals("b", kv.get("lock").orElseThrow());
    }

    @Test
    void concurrentIncrementsAreNotLost() throws Exception {
        InMemoryKeyValueStore real = new InMemoryKeyValueStore();
        AtomicInteger winners = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(8);
        for (int i = 0; i < 1000; i++) {
            pool.submit(() -> {
                real.increment("n", Duration.ofMinutes(1));
                if (real.setIfAbsent("once", "x", Duration.ofMinutes(1))) {
                    winners.incrementAndGet();
                }
            });
        }
        pool.shutdown();
        assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        assertEquals("1000", real.get("n").orElseThrow());
        assertEquals(1, winners.get());
    }
}
