package com.animallocket.outbox;

import org.apache.kafka.common.errors.RetriableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Đọc outbox theo thứ tự tạo và đẩy lên Kafka (at-least-once: consumer khử trùng bằng eventId).
 *
 * <p>Xử lý lỗi:
 * <ul>
 *   <li><b>Lỗi tạm thời</b> (broker sập, timeout, {@link RetriableException}): dừng lô để giữ thứ tự, không tính
 *       attempt — sự cố hạ tầng không được biến event hợp lệ thành FAILED.</li>
 *   <li><b>Lỗi vĩnh viễn</b> (record quá lớn, topic sai, lỗi serialize...): tăng {@code attempts}; đạt
 *       {@code animal.outbox.relay.max-attempts} (mặc định 5) thì đánh dấu FAILED và đi tiếp, để một poison event
 *       không chặn mãi đầu hàng đợi. Đánh đổi: event sau cùng key có thể đi trước event FAILED.</li>
 * </ul>
 */
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
    static final Duration RETENTION = Duration.ofDays(1);
    public static final int DEFAULT_MAX_ATTEMPTS = 5;

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;
    private final int maxAttempts;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka) {
        this(repository, kafka, DEFAULT_MAX_ATTEMPTS);
    }

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka, int maxAttempts) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts phải ≥ 1");
        }
        this.repository = repository;
        this.kafka = kafka;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${animal.outbox.relay.interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.findTop100ByPublishedAtIsNullAndFailedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent e : batch) {
            try {
                kafka.send(e.getTopic(), e.getKey(), e.getPayload()).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                e.markPublished();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception ex) {
                Throwable cause = ex instanceof ExecutionException && ex.getCause() != null ? ex.getCause() : ex;
                if (isTransient(cause)) {
                    log.warn("Đẩy event {} ({}) thất bại tạm thời, sẽ thử lại: {}", e.getId(), e.getType(), cause.toString());
                    return;
                }
                if (e.recordPermanentFailure(cause.toString(), maxAttempts)) {
                    log.error("Event outbox {} ({}, topic {}) FAILED sau {} lần: {}. Bỏ qua để relay không bị nghẽn.",
                            e.getId(), e.getType(), e.getTopic(), e.getAttempts(), cause.toString());
                    continue;
                }
                log.warn("Đẩy event {} ({}) lỗi lần {}/{}: {}", e.getId(), e.getType(), e.getAttempts(), maxAttempts,
                        cause.toString());
                return;
            }
        }
    }

    static boolean isTransient(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof RetriableException || c instanceof TimeoutException) {
                return true;
            }
            if (c.getCause() == c) {
                break;
            }
        }
        return false;
    }

    /** Dọn event đã đẩy quá 1 ngày để bảng không phình. Event FAILED được giữ lại cho người vận hành. */
    @Scheduled(fixedDelayString = "${animal.outbox.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanup() {
        int n = repository.deletePublishedBefore(Instant.now().minus(RETENTION));
        if (n > 0) {
            log.debug("Dọn {} event outbox đã đẩy", n);
        }
    }
}
