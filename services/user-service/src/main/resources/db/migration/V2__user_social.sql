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
    CONSTRAINT uk_friend_request_pair UNIQUE (from_id, to_id),
    INDEX idx_friend_request_to_status (to_id, status),
    INDEX idx_friend_request_from_created (from_id, created_at)
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
    INDEX idx_report_status_created (status, created_at),
    INDEX idx_report_reporter_created (reporter_id, created_at)
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
