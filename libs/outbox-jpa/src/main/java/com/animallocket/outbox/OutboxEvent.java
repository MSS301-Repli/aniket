package com.animallocket.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Một event chờ đẩy lên Kafka, ghi cùng transaction với dữ liệu nghiệp vụ.
 * Schema (Flyway của từng service): bảng {@code outbox_events}.
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

    public void markPublished() {
        this.publishedAt = Instant.now();
    }
}
