CREATE TABLE entitlements (
    public_id VARCHAR(20) NOT NULL,
    plan VARCHAR(10) NOT NULL,
    expires_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (public_id),
    INDEX idx_entitlements_expiry (expires_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE outbox_events (
    id VARCHAR(36) NOT NULL,
    topic VARCHAR(100) NOT NULL,
    event_key VARCHAR(100) NOT NULL,
    type VARCHAR(60) NOT NULL,
    payload TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    INDEX idx_outbox_unpublished (published_at, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE processed_events (
    event_id VARCHAR(36) NOT NULL,
    processed_at DATETIME(6) NOT NULL,
    PRIMARY KEY (event_id),
    INDEX idx_processed_events_time (processed_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
