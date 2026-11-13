package com.animallocket.outbox;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.dao.DataAccessResourceFailureException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OutboxMetricsTest {
    private static final Instant NOW = Instant.parse("2026-11-13T12:00:00Z");

    private SimpleMeterRegistry bind(OutboxRepository repo) {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        new OutboxMetrics(repo, Clock.fixed(NOW, ZoneOffset.UTC)).bindTo(registry);
        return registry;
    }

    @Test
    void exposesPendingOldestAgeAndFailed() {
        OutboxRepository repo = mock(OutboxRepository.class);
        when(repo.countByPublishedAtIsNullAndFailedAtIsNull()).thenReturn(7L);
        when(repo.oldestPendingCreatedAt()).thenReturn(NOW.minusSeconds(120));
        when(repo.countByFailedAtIsNotNull()).thenReturn(2L);
        SimpleMeterRegistry registry = bind(repo);
        assertThat(registry.get("outbox.pending").gauge().value()).isEqualTo(7.0);
        assertThat(registry.get("outbox.oldest.pending.age").gauge().value()).isEqualTo(120.0);
        assertThat(registry.get("outbox.failed").gauge().value()).isEqualTo(2.0);
    }

    @Test
    void emptyOutboxHasZeroAge() {
        SimpleMeterRegistry registry = bind(mock(OutboxRepository.class));
        assertThat(registry.get("outbox.oldest.pending.age").gauge().value()).isZero();
        assertThat(registry.get("outbox.pending").gauge().value()).isZero();
    }

    @Test
    void databaseErrorsBecomeNaNInsteadOfBreakingTheScrape() {
        OutboxRepository repo = mock(OutboxRepository.class);
        when(repo.countByPublishedAtIsNullAndFailedAtIsNull()).thenThrow(new DataAccessResourceFailureException("db down"));
        when(repo.oldestPendingCreatedAt()).thenThrow(new DataAccessResourceFailureException("db down"));
        when(repo.countByFailedAtIsNotNull()).thenThrow(new DataAccessResourceFailureException("db down"));
        SimpleMeterRegistry registry = bind(repo);
        assertThat(registry.get("outbox.pending").gauge().value()).isNaN();
        assertThat(registry.get("outbox.oldest.pending.age").gauge().value()).isNaN();
        assertThat(registry.get("outbox.failed").gauge().value()).isNaN();
    }

    @Test
    void autoConfigurationRegistersGaugesWhenARegistryExists() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
                .withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
                .withBean(ProcessedEventRepository.class, () -> mock(ProcessedEventRepository.class))
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(ctx -> {
                    assertThat(ctx).hasSingleBean(OutboxMetrics.class);
                    assertThat(ctx.getBean(MeterRegistry.class).find("outbox.pending").gauge()).isNotNull();
                });
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OutboxAutoConfiguration.class))
                .withBean(OutboxRepository.class, () -> mock(OutboxRepository.class))
                .withBean(ProcessedEventRepository.class, () -> mock(ProcessedEventRepository.class))
                .run(ctx -> assertThat(ctx).doesNotHaveBean(OutboxMetrics.class));
    }
}
