-- =====================================================
-- V5: Ledger Entry & Fee Schedule Tables
-- Owner: ledger-service
-- =====================================================

CREATE TABLE ledger_entry (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id      BIGINT        NOT NULL,
    account_id      BIGINT        NOT NULL,
    entry_type      ENUM('DEBIT', 'CREDIT', 'FEE', 'TAX', 'REVERSAL') NOT NULL,
    amount          DECIMAL(18,2) NOT NULL,
    currency        CHAR(3)       NOT NULL DEFAULT 'INR',
    narrative       VARCHAR(500),
    balance_after   DECIMAL(18,2),
    entry_date      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by      VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_ledger_payment  ON ledger_entry(payment_id);
CREATE INDEX idx_ledger_account  ON ledger_entry(account_id);
CREATE INDEX idx_ledger_type     ON ledger_entry(entry_type);
CREATE INDEX idx_ledger_date     ON ledger_entry(entry_date);
CREATE INDEX idx_ledger_currency ON ledger_entry(currency);

CREATE TABLE fee_schedule (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    product         VARCHAR(50)   NOT NULL,
    rail            ENUM('BOOK', 'NEFT', 'RTGS', 'IMPS', 'ACH', 'WIRE') NOT NULL,
    fee_type        ENUM('FLAT', 'PERCENTAGE') NOT NULL,
    value           DECIMAL(12,4) NOT NULL,
    min_fee         DECIMAL(12,2) DEFAULT 0,
    max_fee         DECIMAL(12,2),
    currency        CHAR(3)       NOT NULL DEFAULT 'INR',
    effective_from  DATE          NOT NULL,
    effective_to    DATE,
    active          BOOLEAN       NOT NULL DEFAULT TRUE,
    version         INT           NOT NULL DEFAULT 0,
    created_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    created_by      VARCHAR(100),
    updated_by      VARCHAR(100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_fee_product_rail ON fee_schedule(product, rail);
CREATE INDEX idx_fee_active       ON fee_schedule(active);
CREATE INDEX idx_fee_effective    ON fee_schedule(effective_from, effective_to);
