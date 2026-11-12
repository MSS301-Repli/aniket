package com.animallocket.outbox;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurationPackage;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;



/**
 * Đăng ký entity/repository của outbox vào JPA của service ({@code @AutoConfigurationPackage}) và bật relay.
 *
 * <p>{@code after = KafkaAutoConfiguration}: nếu không, {@code @ConditionalOnBean(KafkaTemplate)} có thể được đánh giá
 * TRƯỚC khi KafkaTemplate tồn tại và OutboxRelay âm thầm không bao giờ được tạo (event kẹt trong bảng outbox).
 * Tắt relay (test): {@code animal.outbox.relay.enabled=false}.
 */
@AutoConfiguration(after = KafkaAutoConfiguration.class,
        afterName = "org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration")
@AutoConfigurationPackage
@EnableScheduling
public class OutboxAutoConfiguration {

    @Bean
    OutboxService outboxService(OutboxRepository repository,
                                @Value("${spring.application.name:unknown}") String producer) {
        return new OutboxService(repository, producer);
    }

    @Bean
    IdempotencyService idempotencyService(ProcessedEventRepository repository,
                                          @Value("${animal.outbox.processed-retention:7d}") String retention) {
        return new IdempotencyService(repository, DurationStyle.detectAndParse(retention));
    }

    /** Gauge độ trễ outbox cho cảnh báo "Outbox kẹt" (Prometheus: outbox_pending, outbox_oldest_pending_age_seconds). */
    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    OutboxMetrics outboxMetrics(OutboxRepository repository, MeterRegistry registry) {
        OutboxMetrics metrics = new OutboxMetrics(repository);
        metrics.bindTo(registry);
        return metrics;
    }

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnProperty(name = "animal.outbox.relay.enabled", havingValue = "true", matchIfMissing = true)
    OutboxRelay outboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka,
                            @Value("${animal.outbox.relay.max-attempts:" + OutboxRelay.DEFAULT_MAX_ATTEMPTS + "}")
                            int maxAttempts) {
        return new OutboxRelay(repository, kafka, maxAttempts);
    }
}
