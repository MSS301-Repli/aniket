package com.animallocket.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.cloud.circuitbreaker.resilience4j.Resilience4JAutoConfiguration;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;

import static org.assertj.core.api.Assertions.assertThat;

class ResilienceAutoConfigurationTest {

    private final ApplicationContextRunner resilience = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    io.github.resilience4j.springboot3.circuitbreaker.autoconfigure.CircuitBreakerAutoConfiguration.class,
                    io.github.resilience4j.springboot3.timelimiter.autoconfigure.TimeLimiterAutoConfiguration.class,
                    Resilience4JAutoConfiguration.class, ResilienceAutoConfiguration.class));

    private static String slow() {
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return "finished";
    }

    @Test
    void slowCallsAreCutOffByTheConfiguredTimeoutAndFallBack() {
        resilience.withPropertyValues("animal.resilience.timeout=200ms").run(ctx -> {
            CircuitBreakerFactory<?, ?> factory = ctx.getBean(CircuitBreakerFactory.class);
            long start = System.nanoTime();
            String r = factory.create("slow").run(ResilienceAutoConfigurationTest::slow, t -> "fallback");
            long ms = (System.nanoTime() - start) / 1_000_000;
            assertThat(r).isEqualTo("fallback");
            assertThat(ms).as("bị cắt theo timeout cấu hình").isLessThan(1500);
        });
    }

    @Test
    void defaultTimeoutIsTwoSecondsNotResilience4jsOneSecond() {
        resilience.run(ctx -> {
            CircuitBreakerFactory<?, ?> factory = ctx.getBean(CircuitBreakerFactory.class);
            String r = factory.create("medium").run(() -> {
                try {
                    Thread.sleep(1300);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return "ok";
            }, t -> "fallback");
            assertThat(r).isEqualTo("ok");
        });
    }

    @Test
    void fastCallsPassThroughUntouched() {
        resilience.run(ctx -> assertThat(ctx.getBean(CircuitBreakerFactory.class)
                .create("fast").run(() -> "ok", t -> "fb")).isEqualTo("ok"));
    }

    @Test
    void circuitOpensAfterRepeatedFailuresAndShortCircuits() {
        resilience.withPropertyValues("animal.resilience.open-wait=30s").run(ctx -> {
            CircuitBreakerFactory<?, ?> factory = ctx.getBean(CircuitBreakerFactory.class);
            int[] real = {0};
            for (int i = 0; i < 40; i++) {
                factory.create("flaky").run(() -> {
                    real[0]++;
                    throw new IllegalStateException("down");
                }, t -> "fb");
            }
            // Đủ 10 lời gọi tối thiểu thì mở mạch: các lời gọi sau không còn chạm tới dịch vụ.
            assertThat(real[0]).isBetween(10, 39);
        });
    }
}
