-- =====================================================
-- V10: Seed Data
-- Default admin user, system accounts, routing rules, fee schedules
-- =====================================================

-- Default Admin User (password: Admin@123 - BCrypt hash)
INSERT INTO users (username, email, phone, password_hash, role, active) VALUES
('admin', 'admin@payroute.com', '0000000000', '$2a$12$LJ3m4ys3uz4N9OKKz6rVz.E97t3CLk9X4fGqXHN85kUVGE8nJxKTi', 'ADMIN', TRUE);

-- System Party and Fee Collection Account
INSERT INTO party (name, type, country, risk_rating, status) VALUES
('PayRoute Hub System', 'CORPORATE', 'IND', 'STANDARD', 'ACTIVE');

INSERT INTO account_directory (party_id, account_number, ifsc_iban, alias, currency, account_type, active) VALUES
(1, 'SYSTEM-FEE-ACCOUNT-001', 'PAYROUTE0001', 'SYSTEM_FEES', 'INR', 'SYSTEM', TRUE),
(1, 'SYSTEM-SUSPENSE-001', 'PAYROUTE0001', 'SYSTEM_SUSPENSE', 'INR', 'SYSTEM', TRUE);

-- Default Routing Rules
INSERT INTO routing_rule (name, description, condition_json, preferred_rail, priority, active) VALUES
('High Value RTGS', 'Route payments >= 2,00,000 INR to RTGS', '{"field": "amount", "op": "gte", "value": 200000}', 'RTGS', 10, TRUE),
('Standard NEFT', 'Route payments < 2,00,000 INR to NEFT', '{"field": "amount", "op": "lt", "value": 200000}', 'NEFT', 20, TRUE),
('Instant IMPS', 'Route payments <= 5,00,000 INR with IMPS channel', '{"and": [{"field": "amount", "op": "lte", "value": 500000}, {"field": "channel", "op": "eq", "value": "MOBILE"}]}', 'IMPS', 15, TRUE),
('Cross-border Wire', 'Route USD currency payments to WIRE', '{"field": "currency", "op": "eq", "value": "USD"}', 'WIRE', 5, TRUE),
('Internal Book Transfer', 'Route internal transfers to BOOK', '{"field": "purpose_code", "op": "eq", "value": "BOOK"}', 'BOOK', 1, TRUE);

-- Default Fee Schedules
INSERT INTO fee_schedule (product, rail, fee_type, value, min_fee, max_fee, currency, effective_from, active) VALUES
('PAYMENT', 'NEFT', 'FLAT', 5.00, 5.00, NULL, 'INR', '2026-01-01', TRUE),
('PAYMENT', 'RTGS', 'FLAT', 25.00, 25.00, NULL, 'INR', '2026-01-01', TRUE),
('PAYMENT', 'IMPS', 'FLAT', 10.00, 10.00, NULL, 'INR', '2026-01-01', TRUE),
('PAYMENT', 'ACH', 'FLAT', 3.00, 3.00, NULL, 'INR', '2026-01-01', TRUE),
('PAYMENT', 'WIRE', 'PERCENTAGE', 0.15, 50.00, 5000.00, 'INR', '2026-01-01', TRUE),
('PAYMENT', 'BOOK', 'FLAT', 0.00, 0.00, NULL, 'INR', '2026-01-01', TRUE);
