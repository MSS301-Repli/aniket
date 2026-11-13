package com.animallocket.outbox;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * Gauge outbox (Prometheus): {@code outbox_pending}, {@code outbox_oldest_pending_age_seconds},
 * {@code outbox_failed}. Lỗi đọc DB trả NaN thay vì làm hỏng cả lần scrape.
 */
public class OutboxMetrics implements MeterBinder {
    private final OutboxRepository repository;
    private final Clock clock;

    public OutboxMetrics(OutboxRepository repository) {
        this(repository, Clock.systemUTC());
    }

    OutboxMetrics(OutboxRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    double pending() {
        try {
            return repository.countByPublishedAtIsNullAndFailedAtIsNull();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    double oldestAgeSeconds() {
        try {
            Instant oldest = repository.oldestPendingCreatedAt();
            return oldest == null ? 0 : Math.max(0, Duration.between(oldest, clock.instant()).toMillis() / 1000.0);
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    double failed() {
        try {
            return repository.countByFailedAtIsNotNull();
        } catch (RuntimeException e) {
            return Double.NaN;
        }
    }

    @Override
    public void bindTo(MeterRegistry registry) {
        Gauge.builder("outbox.pending", this, OutboxMetrics::pending)
                .description("Số event outbox chưa được đẩy lên Kafka").register(registry);
        Gauge.builder("outbox.oldest.pending.age", this, OutboxMetrics::oldestAgeSeconds)
                .baseUnit("seconds").description("Tuổi event outbox chưa đẩy cũ nhất").register(registry);
        Gauge.builder("outbox.failed", this, OutboxMetrics::failed)
                .description("Số poison event bị đánh dấu FAILED, cần xử lý thủ công").register(registry);
    }
}
