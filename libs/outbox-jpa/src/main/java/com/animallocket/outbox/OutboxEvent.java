package com.animallocket.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Một event chờ đẩy lên Kafka, ghi cùng transaction với dữ liệu nghiệp vụ.
 * Schema (Flyway của từng service): bảng {@code outbox_events}, gồm cột {@code attempts}, {@code failed_at},
 * {@code last_error} cho xử lý poison event (xem docs/outbox.md).
 */
@Entity
@Table(name = "outbox_events", indexes = @Index(name = "idx_outbox_unpublished", columnList = "publishedAt,createdAt"))
public class OutboxEvent {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 100)
    private String topic;
    @Column(name = "event_key", nullable = false, length = 100)
    private String key;
    @Column(nullable = false, length = 60)
    private String type;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;
    @Column(nullable = false)
    private Instant createdAt;
    private Instant publishedAt;
    /** Số lần đẩy thất bại do lỗi không thử lại được (lỗi tạm thời như broker sập không tính). */
    @Column(nullable = false)
    private int attempts;
    /** Khác null: event bị loại khỏi hàng đợi (poison), cần người vận hành xem xét. */
    private Instant failedAt;
    @Column(length = 500)
    private String lastError;

    protected OutboxEvent() {
    }

    public OutboxEvent(String id, String topic, String key, String type, String payload) {
        this.id = id;
        this.topic = topic;
        this.key = key;
        this.type = type;
        this.payload = payload;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public String getTopic() {
        return topic;
    }

    public String getKey() {
        return key;
    }

    public String getType() {
        return type;
    }

    public String getPayload() {
        return payload;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public int getAttempts() {
        return attempts;
    }

    public Instant getFailedAt() {
        return failedAt;
    }

    public String getLastError() {
        return lastError;
    }

    public void markPublished() {
        this.publishedAt = Instant.now();
    }

    /** Ghi nhận một lần lỗi không thử lại được; đạt {@code maxAttempts} thì đánh dấu FAILED. Trả true nếu vừa FAILED. */
    public boolean recordPermanentFailure(String error, int maxAttempts) {
        attempts++;
        lastError = error == null ? null : error.substring(0, Math.min(error.length(), 500));
        if (attempts >= maxAttempts) {
            failedAt = Instant.now();
            return true;
        }
        return false;
    }
}
