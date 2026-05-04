-- =====================================================
-- V7: Compliance Check & Hold Tables
-- Owner: compliance-service
-- =====================================================

CREATE TABLE compliance_check (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT       NOT NULL,
    check_type      ENUM('SANCTIONS', 'AML', 'PEP', 'GEO') NOT NULL,
    severity        ENUM('LOW', 'MEDIUM', 'HIGH', 'CRITICAL') NOT NULL DEFAULT 'LOW',
    result          ENUM('CLEAR', 'FLAG', 'HOLD') NOT NULL,
    details         JSON,
    checked_by      VARCHAR(100) NOT NULL DEFAULT 'SYSTEM',
    checked_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_compliance_payment  ON compliance_check(payment_id);
CREATE INDEX idx_compliance_type     ON compliance_check(check_type);
CREATE INDEX idx_compliance_result   ON compliance_check(result);
CREATE INDEX idx_compliance_severity ON compliance_check(severity);

CREATE TABLE hold (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT       NOT NULL,
    reason          VARCHAR(500) NOT NULL,
    placed_by       BIGINT       NOT NULL,
    status          ENUM('ACTIVE', 'RELEASED', 'ESCALATED') NOT NULL DEFAULT 'ACTIVE',
    release_notes   VARCHAR(500),
    released_by     BIGINT,
    version         BIGINT       NOT NULL DEFAULT 0,
    placed_at       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at     TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_hold_payment ON hold(payment_id);
CREATE INDEX idx_hold_status  ON hold(status);
CREATE INDEX idx_hold_placed  ON hold(placed_by);
