package com.animallocket.common.kv;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chạy với Redis thật, chỉ khi có biến môi trường {@code REDIS_HOST} (CI có Redis service). Máy dev:
 * {@code docker run --rm -p 6379:6379 redis:7} rồi {@code REDIS_HOST=localhost mvn -pl libs/common verify}.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_HOST", matches = ".+")
class RedisKeyValueStoreTest extends KeyValueStoreContract {
    static LettuceConnectionFactory factory;
    static StringRedisTemplate template;

    @BeforeAll
    static void connect() {
        String host = System.getenv("REDIS_HOST");
        int port = Integer.parseInt(System.getenv().getOrDefault("REDIS_PORT", "6379"));
        RedisStandaloneConfiguration cfg = new RedisStandaloneConfiguration(host, port);
        String password = System.getenv("REDIS_PASSWORD");
        if (password != null && !password.isBlank()) {
            cfg.setPassword(password);
        }
        factory = new LettuceConnectionFactory(cfg);
        factory.afterPropertiesSet();
        factory.start();
        template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
    }

    @AfterAll
    static void close() {
        factory.destroy();
    }

    @Override
    protected KeyValueStore store() {
        return new RedisKeyValueStore(template);
    }

    @Test
    void newCounterAlwaysGetsTtl() {
        String k = key("ttl");
        store().increment(k, Duration.ofSeconds(30));
        long ttl = template.getExpire(k);
        assertTrue(ttl > 0 && ttl <= 30, "ttl=" + ttl);
        store().increment(k, Duration.ofHours(1));
        assertTrue(template.getExpire(k) <= 30, "TTL không bị kéo dài");
        store().delete(k);
    }

    @Test
    void expiredCounterRestarts() throws Exception {
        String k = key("short");
        store().increment(k, Duration.ofMillis(150));
        Thread.sleep(300);
        assertEquals(1, store().increment(k, Duration.ofSeconds(10)));
        store().delete(k);
    }
}
