package com.animallocket.common.kv;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Cài đặt Redis. increment/decrement chạy bằng Lua để nguyên tử: không bao giờ có bộ đếm thiếu TTL
 * (nếu INCR và EXPIRE là hai lệnh, service chết giữa chừng sẽ để lại hạn mức vĩnh viễn).
 */
public class RedisKeyValueStore implements KeyValueStore {

    private static final RedisScript<Long> INCREMENT = new DefaultRedisScript<>("""
            local v = redis.call('INCR', KEYS[1])
            if v == 1 then redis.call('PEXPIRE', KEYS[1], ARGV[1]) end
            return v""", Long.class);

    private static final RedisScript<Long> DECREMENT = new DefaultRedisScript<>("""
            local v = tonumber(redis.call('GET', KEYS[1]))
            if v == nil then return -1 end
            if v > 0 then return redis.call('DECR', KEYS[1]) end
            return 0""", Long.class);

    private final StringRedisTemplate redis;

    public RedisKeyValueStore(StringRedisTemplate redis) {
        this.redis = redis;
    }

    @Override
    public long increment(String key, Duration ttlIfNew) {
        Long v = redis.execute(INCREMENT, List.of(key), String.valueOf(ttlIfNew.toMillis()));
        return v == null ? 0 : v;
    }

    @Override
    public void decrement(String key) {
        redis.execute(DECREMENT, List.of(key));
    }

    @Override
    public Optional<String> get(String key) {
        return Optional.ofNullable(redis.opsForValue().get(key));
    }

    @Override
    public void set(String key, String value, Duration ttl) {
        if (ttl == null) {
            redis.opsForValue().set(key, value);
        } else {
            redis.opsForValue().set(key, value, ttl);
        }
    }

    @Override
    public boolean setIfAbsent(String key, String value, Duration ttl) {
        Boolean ok = ttl == null
                ? redis.opsForValue().setIfAbsent(key, value)
                : redis.opsForValue().setIfAbsent(key, value, ttl);
        return Boolean.TRUE.equals(ok);
    }

    @Override
    public void delete(String... keys) {
        if (keys.length > 0) {
            redis.delete(List.of(keys));
        }
    }
}
