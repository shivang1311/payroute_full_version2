-- =====================================================
-- V1: Identity & Access Management Tables
-- Owner: iam-service
-- =====================================================

CREATE TABLE users (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    username        VARCHAR(50)  NOT NULL,
    email           VARCHAR(100) NOT NULL,
    phone           VARCHAR(20),
    password_hash   VARCHAR(255) NOT NULL,
    role            ENUM('CUSTOMER', 'OPERATIONS', 'COMPLIANCE', 'RECONCILIATION', 'ADMIN') NOT NULL DEFAULT 'CUSTOMER',
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP    NULL,

    CONSTRAINT uk_users_username UNIQUE (username),
    CONSTRAINT uk_users_email    UNIQUE (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_users_role      ON users(role);
CREATE INDEX idx_users_active    ON users(active);
CREATE INDEX idx_users_deleted   ON users(deleted_at);

CREATE TABLE refresh_token (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    token           VARCHAR(255) NOT NULL,
    expires_at      TIMESTAMP    NOT NULL,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT uk_refresh_token      UNIQUE (token)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_refresh_token_user ON refresh_token(user_id);

CREATE TABLE audit_log (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT,
    action          VARCHAR(50)  NOT NULL,
    entity_type     VARCHAR(50),
    entity_id       VARCHAR(255),
    details         JSON,
    ip_address      VARCHAR(45),
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_audit_user      ON audit_log(user_id);
CREATE INDEX idx_audit_action    ON audit_log(action);
CREATE INDEX idx_audit_entity    ON audit_log(entity_type, entity_id);
CREATE INDEX idx_audit_created   ON audit_log(created_at);
