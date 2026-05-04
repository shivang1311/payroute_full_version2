-- =====================================================
-- V2: Party & Account Directory Tables
-- Owner: party-service
-- =====================================================

CREATE TABLE party (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    type            ENUM('INDIVIDUAL', 'CORPORATE') NOT NULL,
    contact_info    JSON,
    country         VARCHAR(3)   NOT NULL DEFAULT 'IND',
    risk_rating     VARCHAR(20)  NOT NULL DEFAULT 'STANDARD',
    user_id         BIGINT,
    status          ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED') NOT NULL DEFAULT 'ACTIVE',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_party_type      ON party(type);
CREATE INDEX idx_party_status    ON party(status);
CREATE INDEX idx_party_country   ON party(country);
CREATE INDEX idx_party_deleted   ON party(deleted_at);
CREATE INDEX idx_party_user_id  ON party(user_id);

CREATE TABLE account_directory (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    party_id        BIGINT       NOT NULL,
    account_number  VARCHAR(16)  NOT NULL,
    ifsc_iban       VARCHAR(50)  NOT NULL,
    alias           VARCHAR(100),
    currency        VARCHAR(3)   NOT NULL,
    account_type    VARCHAR(30)  NOT NULL,
    vpa_upi_id      VARCHAR(100),
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP    NULL,

    CONSTRAINT fk_account_party FOREIGN KEY (party_id) REFERENCES party(id),
    CONSTRAINT uk_account_number UNIQUE (account_number, ifsc_iban)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_account_party   ON account_directory(party_id);
CREATE INDEX idx_account_alias   ON account_directory(alias);
CREATE INDEX idx_account_currency ON account_directory(currency);
CREATE INDEX idx_account_deleted ON account_directory(deleted_at);
