ALTER TABLE erp_user ADD COLUMN IF NOT EXISTS force_password_change BOOLEAN DEFAULT FALSE;
ALTER TABLE erp_user ADD COLUMN IF NOT EXISTS failed_login_attempts INTEGER DEFAULT 0;
ALTER TABLE erp_user ADD COLUMN IF NOT EXISTS locked_until TIMESTAMP;
ALTER TABLE erp_user ADD COLUMN IF NOT EXISTS last_login_at TIMESTAMP;
ALTER TABLE erp_user ADD COLUMN IF NOT EXISTS password_changed_at TIMESTAMP;

UPDATE erp_user
SET force_password_change = COALESCE(force_password_change, FALSE),
    failed_login_attempts = COALESCE(failed_login_attempts, 0);

UPDATE erp_user
SET force_password_change = TRUE
WHERE LOWER(username) IN ('admin', 'market', 'engineer', 'warehouse');

ALTER TABLE erp_user ALTER COLUMN force_password_change SET DEFAULT FALSE;
ALTER TABLE erp_user ALTER COLUMN force_password_change SET NOT NULL;
ALTER TABLE erp_user ALTER COLUMN failed_login_attempts SET DEFAULT 0;
ALTER TABLE erp_user ALTER COLUMN failed_login_attempts SET NOT NULL;
