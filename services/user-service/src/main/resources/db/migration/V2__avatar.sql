-- Avatar duy nhất toàn hệ thống (docs/19-avatar-mechanism.md).
-- Kho được seed lúc khởi động từ avatar-lib (AvatarPoolSeeder); tài khoản cũ được cấp bởi AvatarBackfill.

CREATE TABLE avatar_pool (
    avatar_key VARCHAR(80) NOT NULL,          -- "fox:0:none"
    lib_version INT NOT NULL,                 -- phiên bản thư viện đầu tiên có tổ hợp này
    owner_public_id VARCHAR(20) NULL,         -- đã gán thì không bao giờ về NULL, kể cả khi xoá tài khoản
    assigned_at DATETIME(6) NULL,
    PRIMARY KEY (avatar_key),
    INDEX idx_pool_owner (owner_public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE user_avatars (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(20) NOT NULL,
    avatar_key VARCHAR(80) NOT NULL,
    lib_version INT NOT NULL,
    source VARCHAR(12) NOT NULL,              -- REGISTER | BACKFILL | PURCHASE | ADMIN
    source_ref VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_avatars_key UNIQUE (avatar_key),
    INDEX idx_user_avatars_owner (public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

-- NULL chỉ với tài khoản đã xoá trước khi có avatar (backfill bỏ qua DELETED). UNIQUE cho phép nhiều NULL.
ALTER TABLE users
    ADD COLUMN avatar_key VARCHAR(80) NULL,
    ADD COLUMN avatar_version INT NULL,
    ADD CONSTRAINT uk_users_avatar UNIQUE (avatar_key);
