-- V11: add phone and email alias columns to account_directory for VPA/Phone/Email alias routing (spec §2.5)
ALTER TABLE account_directory
    ADD COLUMN phone VARCHAR(20) NULL AFTER vpa_upi_id,
    ADD COLUMN email VARCHAR(120) NULL AFTER phone;

CREATE INDEX idx_account_directory_phone ON account_directory (phone);
CREATE INDEX idx_account_directory_email ON account_directory (email);
CREATE INDEX idx_account_directory_vpa ON account_directory (vpa_upi_id);
