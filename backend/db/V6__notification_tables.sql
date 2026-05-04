-- =====================================================
-- V6: Notification Table
-- Owner: notification-service
-- =====================================================

CREATE TABLE notification (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id         BIGINT       NOT NULL,
    title           VARCHAR(200) NOT NULL,
    message         TEXT,
    category        ENUM('PAYMENT', 'COMPLIANCE', 'EXCEPTION', 'SETTLEMENT', 'SYSTEM') NOT NULL DEFAULT 'SYSTEM',
    severity        ENUM('INFO', 'WARNING', 'ERROR', 'CRITICAL') NOT NULL DEFAULT 'INFO',
    reference_type  VARCHAR(50),
    reference_id    BIGINT,
    is_read         BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    read_at         TIMESTAMP    NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE INDEX idx_notif_user_read   ON notification(user_id, is_read);
CREATE INDEX idx_notif_category    ON notification(category);
CREATE INDEX idx_notif_created     ON notification(created_at);
CREATE INDEX idx_notif_reference   ON notification(reference_type, reference_id);
