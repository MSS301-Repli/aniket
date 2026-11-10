package com.animallocket.discovery.config;

import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.discovery.geo.LocationPrivacy;
import com.animallocket.discovery.store.DiscoveryStore;
import com.animallocket.discovery.store.InMemoryDiscoveryStore;
import com.animallocket.discovery.store.RedisDiscoveryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Clock;

@Configuration
public class DiscoveryConfig {
    @Bean
    LocationPrivacy locationPrivacy(@Value("${animal.discovery.secret}") String secret) {
        return new LocationPrivacy(secret);
    }

    @Bean
    @ConditionalOnMissingBean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @ConditionalOnProperty(name = "animal.kv.type", havingValue = "memory")
    DiscoveryStore inMemoryDiscoveryStore() {
        return new InMemoryDiscoveryStore();
    }

    @Bean
    @ConditionalOnProperty(name = "animal.kv.type", havingValue = "redis", matchIfMissing = true)
    DiscoveryStore redisDiscoveryStore(StringRedisTemplate redis) {
        return new RedisDiscoveryStore(redis);
    }

    @Bean
    @ConditionalOnMissingBean(KeyValueStore.class)
    KeyValueStore fallbackKeyValueStore() {
        return new InMemoryKeyValueStore();
    }
}
