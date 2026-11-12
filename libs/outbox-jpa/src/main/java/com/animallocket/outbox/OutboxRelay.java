package com.animallocket.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Đọc outbox theo thứ tự tạo và đẩy lên Kafka (at-least-once: consumer khử trùng bằng eventId).
 * Gặp lỗi thì dừng lô để giữ thứ tự, lần chạy sau thử lại.
 */
public class OutboxRelay {
    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);
    static final Duration SEND_TIMEOUT = Duration.ofSeconds(5);
    static final Duration RETENTION = Duration.ofDays(1);

    private final OutboxRepository repository;
    private final KafkaTemplate<String, String> kafka;

    public OutboxRelay(OutboxRepository repository, KafkaTemplate<String, String> kafka) {
        this.repository = repository;
        this.kafka = kafka;
    }

    @Scheduled(fixedDelayString = "${animal.outbox.relay.interval-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEvent> batch = repository.findTop100ByPublishedAtIsNullOrderByCreatedAtAsc();
        for (OutboxEvent e : batch) {
            try {
                kafka.send(e.getTopic(), e.getKey(), e.getPayload()).get(SEND_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS);
                e.markPublished();
            } catch (Exception ex) {
                if (ex instanceof InterruptedException) {
                    Thread.currentThread().interrupt();
                }
                log.warn("Đẩy event {} ({}) thất bại, sẽ thử lại: {}", e.getId(), e.getType(), ex.toString());
                break;
            }
        }
    }

    /** Dọn event đã đẩy quá 1 ngày để bảng không phình. */
    @Scheduled(fixedDelayString = "${animal.outbox.cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanup() {
        int n = repository.deletePublishedBefore(Instant.now().minus(RETENTION));
        if (n > 0) {
            log.debug("Dọn {} event outbox đã đẩy", n);
        }
    }
}
