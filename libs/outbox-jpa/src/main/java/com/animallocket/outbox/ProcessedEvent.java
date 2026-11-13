package com.animallocket.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import org.springframework.data.domain.Persistable;

import java.time.Instant;

/**
 * Dấu vết event đã xử lý, giúp consumer idempotent. Bảng {@code processed_events}.
 * {@link Persistable#isNew()} = true để {@code save()} dùng INSERT (persist) thay vì merge: hai consumer cùng xử lý
 * một eventId thì lần ghi thứ hai vi phạm khoá chính thay vì âm thầm "thành công".
 */
@Entity
@Table(name = "processed_events")
public class ProcessedEvent implements Persistable<String> {
    @Id
    @Column(length = 36)
    private String eventId;
    @Column(nullable = false)
    private Instant processedAt;
    @Transient
    private boolean isNew = true;

    protected ProcessedEvent() {
    }

    public ProcessedEvent(String eventId) {
        this.eventId = eventId;
        this.processedAt = Instant.now();
    }

    public String getEventId() {
        return eventId;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }

    @Override
    public String getId() {
        return eventId;
    }

    @Override
    public boolean isNew() {
        return isNew;
    }

    @PostLoad
    @PostPersist
    void markNotNew() {
        this.isNew = false;
    }
}
