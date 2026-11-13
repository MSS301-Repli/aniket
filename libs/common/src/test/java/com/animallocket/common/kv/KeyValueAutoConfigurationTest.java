package com.animallocket.common.kv;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.data.redis.core.StringRedisTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class KeyValueAutoConfigurationTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(KeyValueAutoConfiguration.class));

    @Test
    void memoryTypeGivesInMemoryStore() {
        runner.withPropertyValues("animal.kv.type=memory")
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(ctx -> assertThat(ctx.getBean(KeyValueStore.class)).isInstanceOf(InMemoryKeyValueStore.class));
    }

    @Test
    void redisIsTheDefaultWhenTemplateExists() {
        runner.withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(ctx -> assertThat(ctx.getBean(KeyValueStore.class)).isInstanceOf(RedisKeyValueStore.class));
    }

    @Test
    void noStoreWithoutRedisTemplateOrRedisOnClasspath() {
        runner.run(ctx -> assertThat(ctx).doesNotHaveBean(KeyValueStore.class));
        runner.withClassLoader(new FilteredClassLoader(StringRedisTemplate.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(KeyValueStore.class));
    }

    @Test
    void serviceDefinedStoreWins() {
        KeyValueStore custom = new InMemoryKeyValueStore();
        runner.withBean(KeyValueStore.class, () -> custom)
                .withBean(StringRedisTemplate.class, () -> mock(StringRedisTemplate.class))
                .run(ctx -> assertThat(ctx.getBean(KeyValueStore.class)).isSameAs(custom));
    }
}
