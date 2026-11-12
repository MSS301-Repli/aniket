package com.animallocket.outbox;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Khử trùng lặp event phía consumer (Kafka giao at-least-once). */
public class IdempotencyService {
    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository repository;
    private final Duration retention;

    public IdempotencyService(ProcessedEventRepository repository, Duration retention) {
        this.repository = repository;
        this.retention = retention;
    }

    /**
     * Trả true nếu đây là lần đầu thấy event. Gọi trong CÙNG transaction với phần xử lý ({@code MANDATORY}):
     * xử lý lỗi thì dấu vết cũng rollback và event sẽ được xử lý lại. Hai consumer đua nhau cùng eventId:
     * INSERT thứ hai vi phạm khoá chính và ném lỗi, Kafka thử lại, lần sau trả false.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean firstTime(String eventId) {
        if (repository.existsById(eventId)) {
            return false;
        }
        repository.saveAndFlush(new ProcessedEvent(eventId));
        return true;
    }

    /**
     * Dọn dấu vết cũ hơn {@code animal.outbox.processed-retention} (mặc định 7 ngày). Retention phải dài hơn
     * khoảng thời gian một event có thể bị giao lại (retry + DLQ replay), nếu không event cũ sẽ bị xử lý lần nữa.
     */
    @Scheduled(fixedDelayString = "${animal.outbox.processed-cleanup.interval-ms:3600000}")
    @Transactional
    public void cleanup() {
        int n = repository.deleteProcessedBefore(Instant.now().minus(retention));
        if (n > 0) {
            log.debug("Dọn {} dấu vết processed_events", n);
        }
    }
}
