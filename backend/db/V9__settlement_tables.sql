-- =====================================================
-- V9: Settlement Batch & Payment Report Tables
-- Owner: settlement-service
-- =====================================================

CREATE TABLE settlement_batch (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    rail            ENUM('BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE') NOT NULL,
    period_start    TIMESTAMP    NOT NULL,
    period_end      TIMESTAMP    NOT NULL,
    total_count     INT          NOT NULL DEFAULT 0,
    total_amount    DECIMAL(18,2) NOT NULL DEFAULT 0,
    net_amount      DECIMAL(18,2),
    total_fees      DECIMAL(18,2) DEFAULT 0,
    currency        CHAR(3)      NOT NULL DEFAULT 'INR',
    status          ENUM('PENDING', 'PROCESSING', 'POSTED', 'RECONCILED', 'FAILED') NOT NULL DEFAULT 'PENDING',
    posted_date     TIMESTAMP    NULL,
    version         BIGINT       NOT NULL DEFAULT 0,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(255),
    updated_by      VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_settlement_rail    ON settlement_batch(rail);
CREATE INDEX idx_settlement_status  ON settlement_batch(status);
CREATE INDEX idx_settlement_period  ON settlement_batch(period_start, period_end);
CREATE INDEX idx_settlement_posted  ON settlement_batch(posted_date);

CREATE TABLE payment_report (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    report_name     VARCHAR(200) NOT NULL,
    scope           ENUM('PRODUCT', 'RAIL', 'PERIOD', 'DAILY', 'MONTHLY', 'CUSTOM') NOT NULL,
    parameters      JSON,
    metrics         JSON         NOT NULL,
    status          ENUM('GENERATING', 'COMPLETED', 'FAILED') NOT NULL DEFAULT 'GENERATING',
    generated_at    TIMESTAMP    NULL,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(255)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_report_scope    ON payment_report(scope);
CREATE INDEX idx_report_status   ON payment_report(status);
CREATE INDEX idx_report_created  ON payment_report(created_at);
