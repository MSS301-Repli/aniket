package com.animallocket.outbox;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Hồi quy: nếu OutboxAutoConfiguration không khai báo chạy sau KafkaAutoConfiguration thì
 * {@code @ConditionalOnBean(KafkaTemplate)} có thể sai và relay không bao giờ được tạo (event kẹt vĩnh viễn).
 * Test cấp service luôn tắt relay nên không phát hiện được lỗi này.
 */
class OutboxAutoConfigurationOrderingTest {
    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            // Cố ý liệt kê Outbox TRƯỚC Kafka: thứ tự phải do @AutoConfiguration(after=...) quyết định.
            .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class, KafkaAutoConfiguration.class))
            .withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
            .withPropertyValues("spring.kafka.bootstrap-servers=localhost:9092", "spring.application.name=test");

    @Test
    void relayIsCreatedWhenKafkaIsPresent() {
        runner.run(ctx -> assertThat(ctx).hasSingleBean(OutboxRelay.class).hasSingleBean(OutboxService.class));
    }

    @Test
    void relayCanBeDisabledByProperty() {
        runner.withPropertyValues("animal.outbox.relay.enabled=false")
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxRelay.class).hasSingleBean(OutboxService.class));
    }

    @Test
    void noRelayWithoutKafka() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
                .withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxRelay.class).hasSingleBean(OutboxService.class));
    }
}
