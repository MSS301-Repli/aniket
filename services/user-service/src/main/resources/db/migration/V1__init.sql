CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(20) NOT NULL,
    keycloak_sub VARCHAR(100) NOT NULL,
    alias VARCHAR(30) NOT NULL,
    lead_animal VARCHAR(40) NOT NULL,
    birth_date DATE NULL,
    age_group VARCHAR(10) NOT NULL,
    discoverable BIT NOT NULL,
    status VARCHAR(12) NOT NULL,
    alias_changed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_public_id UNIQUE (public_id),
    CONSTRAINT uk_users_sub UNIQUE (keycloak_sub)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE friendships (
    id BIGINT NOT NULL AUTO_INCREMENT,
    user_low VARCHAR(20) NOT NULL,
    user_high VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_friend_pair UNIQUE (user_low, user_high),
    INDEX idx_friend_high (user_high)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE friend_requests (
    id BIGINT NOT NULL AUTO_INCREMENT,
    from_id VARCHAR(20) NOT NULL,
    to_id VARCHAR(20) NOT NULL,
    source VARCHAR(10) NOT NULL,
    status VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_fr_pair UNIQUE (from_id, to_id),
    INDEX idx_fr_to_status (to_id, status),
    INDEX idx_fr_from_created (from_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE blocks (
    id BIGINT NOT NULL AUTO_INCREMENT,
    blocker_id VARCHAR(20) NOT NULL,
    blocked_id VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_block_pair UNIQUE (blocker_id, blocked_id),
    INDEX idx_block_blocked (blocked_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE reports (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reporter_id VARCHAR(20) NOT NULL,
    target_public_id VARCHAR(20) NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_ref VARCHAR(64) NULL,
    reason VARCHAR(30) NOT NULL,
    details VARCHAR(500) NULL,
    status VARCHAR(10) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_report_status (status, created_at),
    INDEX idx_report_reporter (reporter_id, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE animals (
    code VARCHAR(40) NOT NULL,
    name VARCHAR(60) NOT NULL,
    rarity VARCHAR(10) NOT NULL,
    pack_code VARCHAR(40) NULL,
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE user_animals (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(20) NOT NULL,
    animal_code VARCHAR(40) NOT NULL,
    acquired_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_animal UNIQUE (public_id, animal_code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE spin_log (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(20) NOT NULL,
    animal_code VARCHAR(40) NOT NULL,
    spun_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_spin_user_time (public_id, spun_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE device_tokens (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(20) NOT NULL,
    token VARCHAR(255) NOT NULL,
    platform VARCHAR(10) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_device_token UNIQUE (token),
    INDEX idx_device_user (public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE entitlements (
    public_id VARCHAR(20) NOT NULL,
    plan VARCHAR(10) NOT NULL,
    expires_at DATETIME(6) NULL,
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
