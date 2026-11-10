package com.animallocket.discovery.config;

import com.animallocket.common.kv.InMemoryKeyValueStore;
import com.animallocket.common.kv.KeyValueStore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
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
    @ConditionalOnMissingBean(KeyValueStore.class)
    KeyValueStore fallbackKeyValueStore() {
        return new InMemoryKeyValueStore();
    }
}
