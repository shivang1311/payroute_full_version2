-- =====================================================
-- V3: Payment Order & Validation Tables
-- Owner: payment-service
-- =====================================================

CREATE TABLE payment_order (
    id                  BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key     VARCHAR(64),
    debtor_account_id   BIGINT       NOT NULL,
    creditor_account_id BIGINT       NOT NULL,
    amount              DECIMAL(18,2) NOT NULL,
    currency            CHAR(3)      NOT NULL DEFAULT 'INR',
    purpose_code        VARCHAR(255),
    initiation_channel  ENUM('BRANCH', 'MOBILE', 'ONLINE', 'API') NOT NULL DEFAULT 'ONLINE',
    status              ENUM('INITIATED', 'VALIDATED', 'VALIDATION_FAILED', 'SCREENING', 'HELD',
                             'ROUTED', 'PROCESSING', 'COMPLETED', 'FAILED', 'REVERSED') NOT NULL DEFAULT 'INITIATED',
    initiated_by        VARCHAR(255),
    version             BIGINT       NOT NULL DEFAULT 0,
    created_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by          VARCHAR(255),
    updated_by          VARCHAR(255),

    CONSTRAINT uk_idempotency_key UNIQUE (idempotency_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_payment_status       ON payment_order(status);
CREATE INDEX idx_payment_debtor       ON payment_order(debtor_account_id);
CREATE INDEX idx_payment_creditor     ON payment_order(creditor_account_id);
CREATE INDEX idx_payment_initiated_by ON payment_order(initiated_by);
CREATE INDEX idx_payment_created      ON payment_order(created_at);
CREATE INDEX idx_payment_channel      ON payment_order(initiation_channel);

CREATE TABLE validation_result (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT       NOT NULL,
    rule_name       VARCHAR(100) NOT NULL,
    result          ENUM('PASS', 'FAIL') NOT NULL,
    message         VARCHAR(500),
    checked_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_validation_payment FOREIGN KEY (payment_id) REFERENCES payment_order(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_validation_payment ON validation_result(payment_id);
CREATE INDEX idx_validation_result  ON validation_result(result);
