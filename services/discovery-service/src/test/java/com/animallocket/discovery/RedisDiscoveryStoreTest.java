package com.animallocket.discovery;

import com.animallocket.discovery.store.DiscoveryStore;
import com.animallocket.discovery.store.RedisDiscoveryStore;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/** Chỉ chạy khi có REDIS_TEST_HOST, ví dụ: docker run -d -p 6379:6379 redis:7 */
@EnabledIfEnvironmentVariable(named = "REDIS_TEST_HOST", matches = ".+")
class RedisDiscoveryStoreTest extends DiscoveryStoreContractTest {
    @Override
    DiscoveryStore newStore() {
        var cf = new LettuceConnectionFactory(new RedisStandaloneConfiguration(
                System.getenv("REDIS_TEST_HOST"), Integer.parseInt(System.getenv().getOrDefault("REDIS_TEST_PORT", "6379"))));
        cf.afterPropertiesSet();
        var t = new StringRedisTemplate(cf);
        t.afterPropertiesSet();
        return new RedisDiscoveryStore(t);
    }
}
