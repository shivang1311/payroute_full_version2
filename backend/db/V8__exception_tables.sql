-- =====================================================
-- V8: Exception, Return & Reconciliation Tables
-- Owner: exception-service
-- =====================================================

CREATE TABLE exception_case (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT       NOT NULL,
    category        ENUM('VALIDATION', 'RAIL', 'POSTING', 'COMPLIANCE', 'SYSTEM') NOT NULL,
    description     TEXT         NOT NULL,
    owner_id        BIGINT,
    status          ENUM('OPEN', 'IN_PROGRESS', 'RESOLVED', 'ESCALATED', 'CLOSED') NOT NULL DEFAULT 'OPEN',
    resolution      TEXT,
    priority        ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'MEDIUM',
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    resolved_at     TIMESTAMP    NULL,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_exception_payment  ON exception_case(payment_id);
CREATE INDEX idx_exception_category ON exception_case(category);
CREATE INDEX idx_exception_status   ON exception_case(status);
CREATE INDEX idx_exception_owner    ON exception_case(owner_id);
CREATE INDEX idx_exception_priority ON exception_case(priority);

CREATE TABLE return_item (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT        NOT NULL,
    reason_code     VARCHAR(20)   NOT NULL,
    reason_desc     VARCHAR(500),
    amount          DECIMAL(18,2) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'INR',
    status          ENUM('NOTIFIED', 'PROCESSING', 'POSTED', 'CLOSED') NOT NULL DEFAULT 'NOTIFIED',
    version         BIGINT        NOT NULL DEFAULT 0,
    return_date     DATE,
    processed_at    TIMESTAMP     NULL,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_return_payment ON return_item(payment_id);
CREATE INDEX idx_return_status  ON return_item(status);
CREATE INDEX idx_return_date    ON return_item(return_date);

CREATE TABLE reconciliation_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    source          ENUM('RAIL', 'LEDGER') NOT NULL,
    reference_id    BIGINT       NOT NULL,
    counterpart_id  BIGINT,
    recon_date      DATE         NOT NULL,
    result          ENUM('MATCHED', 'UNMATCHED', 'PARTIAL', 'DISCREPANCY') NOT NULL,
    amount          DECIMAL(18,2),
    currency        CHAR(3)      DEFAULT 'INR',
    notes           TEXT,
    resolved        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_recon_source    ON reconciliation_record(source);
CREATE INDEX idx_recon_result    ON reconciliation_record(result);
CREATE INDEX idx_recon_date      ON reconciliation_record(recon_date);
CREATE INDEX idx_recon_reference ON reconciliation_record(reference_id);
CREATE INDEX idx_recon_resolved  ON reconciliation_record(resolved);
