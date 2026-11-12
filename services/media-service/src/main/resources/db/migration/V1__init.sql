CREATE TABLE media_jobs (
    id VARCHAR(36) NOT NULL,
    owner_public_id VARCHAR(20) NOT NULL,
    context VARCHAR(10) NOT NULL,
    ref_id VARCHAR(64) NULL,
    raw_key VARCHAR(120) NOT NULL,
    public_key VARCHAR(120) NULL,
    status VARCHAR(16) NOT NULL,
    lead_animal VARCHAR(40) NULL,
    method VARCHAR(10) NULL,
    faces_detected INT NULL,
    reject_reason VARCHAR(60) NULL,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_media_owner (owner_public_id),
    INDEX idx_media_status_updated (status, updated_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE owner_animals (
    public_id VARCHAR(20) NOT NULL,
    lead_animal VARCHAR(40) NOT NULL,
    PRIMARY KEY (public_id)
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
    PRIMARY KEY (event_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
