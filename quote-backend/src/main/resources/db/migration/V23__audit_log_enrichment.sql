ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS module VARCHAR(60);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS result VARCHAR(30) DEFAULT 'SUCCESS';
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS message VARCHAR(500);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS ip_address VARCHAR(80);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS user_agent TEXT;
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_method VARCHAR(20);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_path VARCHAR(300);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS request_id VARCHAR(80);
ALTER TABLE audit_log ADD COLUMN IF NOT EXISTS error_message TEXT;

CREATE INDEX IF NOT EXISTS idx_audit_log_module ON audit_log(module);
CREATE INDEX IF NOT EXISTS idx_audit_log_result ON audit_log(result);
CREATE INDEX IF NOT EXISTS idx_audit_log_username ON audit_log(username);
CREATE INDEX IF NOT EXISTS idx_audit_log_request_id ON audit_log(request_id);
