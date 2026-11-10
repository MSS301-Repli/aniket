CREATE TABLE users (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(20) NOT NULL,
    keycloak_sub VARCHAR(100) NOT NULL,
    alias VARCHAR(30) NOT NULL,
    lead_animal VARCHAR(40) NOT NULL,
    birth_date DATE NULL,
    age_group VARCHAR(10) NOT NULL,
    discoverable BIT NOT NULL DEFAULT 1,
    status VARCHAR(12) NOT NULL,
    avatar_key VARCHAR(80) NULL,
    avatar_version INT NULL,
    alias_changed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_users_public_id UNIQUE (public_id),
    CONSTRAINT uk_users_sub UNIQUE (keycloak_sub),
    CONSTRAINT uk_users_avatar UNIQUE (avatar_key)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE animals (
    code VARCHAR(40) NOT NULL,
    name VARCHAR(60) NOT NULL,
    rarity VARCHAR(10) NOT NULL,
    pack_code VARCHAR(40) NULL,
    PRIMARY KEY (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE avatar_pool (
    avatar_key VARCHAR(80) NOT NULL,
    lib_version INT NOT NULL,
    owner_public_id VARCHAR(20) NULL,
    assigned_at DATETIME(6) NULL,
    PRIMARY KEY (avatar_key),
    INDEX idx_avatar_pool_owner (owner_public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE user_avatars (
    id BIGINT NOT NULL AUTO_INCREMENT,
    public_id VARCHAR(20) NOT NULL,
    avatar_key VARCHAR(80) NOT NULL,
    lib_version INT NOT NULL,
    source VARCHAR(12) NOT NULL,
    source_ref VARCHAR(64) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_user_avatars_key UNIQUE (avatar_key),
    INDEX idx_user_avatars_owner (public_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
