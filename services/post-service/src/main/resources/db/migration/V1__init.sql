CREATE TABLE posts (
    id VARCHAR(36) NOT NULL,
    author_id VARCHAR(20) NOT NULL,
    caption VARCHAR(200) NULL,
    media_id VARCHAR(36) NOT NULL,
    public_key VARCHAR(120) NULL,
    status VARCHAR(14) NOT NULL,
    reject_reason VARCHAR(60) NULL,
    like_count INT NOT NULL,
    comment_count INT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    published_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_post_media UNIQUE (media_id),
    INDEX idx_post_feed (author_id, status, published_at),
    INDEX idx_post_status_created (status, created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE post_likes (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id VARCHAR(36) NOT NULL,
    user_id VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_like UNIQUE (post_id, user_id),
    INDEX idx_like_user (user_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE comments (
    id BIGINT NOT NULL AUTO_INCREMENT,
    post_id VARCHAR(36) NOT NULL,
    author_id VARCHAR(20) NOT NULL,
    text VARCHAR(300) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_comment_post (post_id, id),
    INDEX idx_comment_author (author_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE media_states (
    media_id VARCHAR(36) NOT NULL,
    owner_id VARCHAR(20) NOT NULL,
    status VARCHAR(10) NOT NULL,
    public_key VARCHAR(120) NULL,
    reason VARCHAR(60) NULL,
    updated_at DATETIME(6) NOT NULL,
    PRIMARY KEY (media_id),
    INDEX idx_media_state_owner (owner_id)
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
