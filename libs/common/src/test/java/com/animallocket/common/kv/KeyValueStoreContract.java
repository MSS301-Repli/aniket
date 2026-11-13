package com.animallocket.common.kv;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Hành vi chung mọi cài đặt KeyValueStore phải thoả. Lớp con cung cấp store; key được thêm tiền tố ngẫu nhiên
 * để chạy được trên Redis dùng chung.
 */
abstract class KeyValueStoreContract {

    protected abstract KeyValueStore store();

    protected final String prefix = "test:" + UUID.randomUUID() + ":";

    protected String key(String k) {
        return prefix + k;
    }

    @Test
    void incrementCountsAndDecrementFloorsAtZero() {
        KeyValueStore kv = store();
        String q = key("q");
        assertEquals(1, kv.increment(q, Duration.ofMinutes(1)));
        assertEquals(2, kv.increment(q, Duration.ofMinutes(1)));
        kv.decrement(q);
        assertEquals("1", kv.get(q).orElseThrow());
        kv.decrement(q);
        kv.decrement(q);
        assertEquals("0", kv.get(q).orElseThrow());
    }

    @Test
    void decrementOnMissingKeyDoesNotCreateIt() {
        KeyValueStore kv = store();
        kv.decrement(key("none"));
        assertTrue(kv.get(key("none")).isEmpty());
    }

    @Test
    void setGetDeleteRoundTrip() {
        KeyValueStore kv = store();
        kv.set(key("a"), "1", Duration.ofMinutes(1));
        kv.set(key("b"), "2", null);
        assertEquals("1", kv.get(key("a")).orElseThrow());
        assertEquals("2", kv.get(key("b")).orElseThrow());
        kv.delete(key("a"), key("b"));
        assertTrue(kv.get(key("a")).isEmpty());
        assertTrue(kv.get(key("b")).isEmpty());
    }

    @Test
    void setIfAbsentOnlyFirstWins() {
        KeyValueStore kv = store();
        String lock = key("lock");
        assertTrue(kv.setIfAbsent(lock, "a", Duration.ofMinutes(1)));
        assertFalse(kv.setIfAbsent(lock, "b", Duration.ofMinutes(1)));
        assertEquals("a", kv.get(lock).orElseThrow());
        kv.delete(lock);
        assertTrue(kv.setIfAbsent(lock, "c", Duration.ofMinutes(1)));
    }
}
