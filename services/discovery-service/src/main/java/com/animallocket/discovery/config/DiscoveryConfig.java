package com.animallocket.discovery.config;

import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.kv.KeyValueStore;
import com.animallocket.discovery.store.DiscoveryStore;
import com.animallocket.discovery.store.InMemoryDiscoveryStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class DiscoveryConfig {
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
    @ConditionalOnMissingBean(KeyValueStore.class)
    KeyValueStore fallbackKeyValueStore() {
        return new InMemoryKeyValueStore();
    }
}
