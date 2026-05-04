-- =====================================================
-- V4: Routing Rule & Rail Instruction Tables
-- Owner: routing-service
-- =====================================================

CREATE TABLE routing_rule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    condition_json  JSON         NOT NULL,
    preferred_rail  ENUM('BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE') NOT NULL,
    priority        INT          NOT NULL DEFAULT 100,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255),
    deleted_at      TIMESTAMP    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rule_priority ON routing_rule(priority);
CREATE INDEX idx_rule_active   ON routing_rule(active);
CREATE INDEX idx_rule_rail     ON routing_rule(preferred_rail);
CREATE INDEX idx_rule_deleted  ON routing_rule(deleted_at);

CREATE TABLE rail_instruction (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT       NOT NULL,
    rail            ENUM('BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE') NOT NULL,
    correlation_ref VARCHAR(64)  NOT NULL,
    rail_status     ENUM('PENDING', 'SENT', 'ACKNOWLEDGED', 'SETTLED', 'REJECTED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    retry_count     INT          NOT NULL DEFAULT 0,
    max_retries     INT          NOT NULL DEFAULT 3,
    sent_at         TIMESTAMP    NULL,
    completed_at    TIMESTAMP    NULL,
    failure_reason  VARCHAR(500),
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    CONSTRAINT uk_correlation_ref UNIQUE (correlation_ref)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_rail_payment    ON rail_instruction(payment_id);
CREATE INDEX idx_rail_status     ON rail_instruction(rail_status);
CREATE INDEX idx_rail_type       ON rail_instruction(rail);
CREATE INDEX idx_rail_corr       ON rail_instruction(correlation_ref);
