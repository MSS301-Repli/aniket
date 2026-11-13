package com.animallocket.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/** Dấu vết webhook đã xử lý, khử trùng lặp theo id thông báo của store. */
@Entity
@Table(name = "webhook_events")
@Getter
@NoArgsConstructor
public class WebhookEvent {
    @Id
    @Column(length = 100)
    private String id;
    @Column(nullable = false, length = 10)
    private String store;
    @Column(length = 60)
    private String type;
    @Column(nullable = false)
    private Instant receivedAt = Instant.now();

    public WebhookEvent(String id, String store, String type) {
        this.id = id;
        this.store = store;
        this.type = type;
    }
}
