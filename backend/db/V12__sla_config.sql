-- V12: SLA configuration per rail + breach tracking (spec §2.9, §7, §8)

CREATE TABLE sla_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    rail VARCHAR(10) NOT NULL UNIQUE,
    target_tat_seconds INT NOT NULL,
    warning_threshold_seconds INT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NULL,
    CONSTRAINT chk_sla_rail CHECK (rail IN ('BOOK','NEFT','RTGS','IMPS','ACH','WIRE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO sla_config (rail, target_tat_seconds, warning_threshold_seconds, active) VALUES
    ('BOOK', 5,   3,   TRUE),
    ('IMPS', 30,  20,  TRUE),
    ('RTGS', 60,  45,  TRUE),
    ('NEFT', 120, 90,  TRUE),
    ('ACH',  300, 240, TRUE),
    ('WIRE', 600, 480, TRUE);

-- Prevent duplicate breach notifications per instruction
ALTER TABLE rail_instruction
    ADD COLUMN breach_notified_at DATETIME(6) NULL AFTER failure_reason;

CREATE INDEX idx_rail_instruction_open ON rail_instruction (rail_status, sent_at);
