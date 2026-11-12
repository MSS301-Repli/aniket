package com.animallocket.common.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JCircuitBreakerFactory;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JConfigBuilder;
import org.springframework.cloud.client.circuitbreaker.Customizer;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.time.Duration;

/**
 * Cấu hình mặc định cho mọi lời gọi đồng bộ qua {@code CircuitBreakerFactory} của Spring Cloud.
 *
 * <p>Vì sao cần: khoá {@code resilience4j.*} trong YAML áp dụng cho annotation/registry của Resilience4j, KHÔNG tự
 * áp dụng cho {@code CircuitBreakerFactory}; không cấu hình thì time limiter mặc định chỉ 1 giây. Mặc định ở đây:
 * <ul>
 *   <li>timeout {@code animal.resilience.timeout} (2s);</li>
 *   <li>mở mạch khi ≥ 50% lỗi trên cửa sổ 20 lời gọi (tối thiểu 10);</li>
 *   <li>thử lại (half-open, 5 lời gọi) sau {@code animal.resilience.open-wait} (10s).</li>
 * </ul>
 * Retry không bật mặc định: thử lại lời gọi không idempotent là nguy hiểm, service tự thêm khi cần.
 */
@AutoConfiguration
@ConditionalOnClass(Resilience4JCircuitBreakerFactory.class)
public class ResilienceAutoConfiguration {

    static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(2);
    static final Duration DEFAULT_OPEN_WAIT = Duration.ofSeconds(10);

    @Bean
    @ConditionalOnMissingBean(name = "animalDefaultCircuitBreakerCustomizer")
    Customizer<Resilience4JCircuitBreakerFactory> animalDefaultCircuitBreakerCustomizer(Environment env) {
        Binder binder = Binder.get(env);
        Duration timeout = binder.bind("animal.resilience.timeout", Duration.class).orElse(DEFAULT_TIMEOUT);
        Duration openWait = binder.bind("animal.resilience.open-wait", Duration.class).orElse(DEFAULT_OPEN_WAIT);
        return factory -> factory.configureDefault(id -> new Resilience4JConfigBuilder(id)
                .circuitBreakerConfig(CircuitBreakerConfig.custom()
                        .slidingWindowSize(20)
                        .minimumNumberOfCalls(10)
                        .failureRateThreshold(50)
                        .waitDurationInOpenState(openWait)
                        .permittedNumberOfCallsInHalfOpenState(5)
                        .build())
                .timeLimiterConfig(TimeLimiterConfig.custom().timeoutDuration(timeout).build())
                .build());
    }
}
