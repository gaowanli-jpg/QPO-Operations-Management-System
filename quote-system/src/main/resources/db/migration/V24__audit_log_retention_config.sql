CREATE TABLE IF NOT EXISTS audit_log_retention_config (
    id INTEGER PRIMARY KEY,
    enabled BOOLEAN NOT NULL DEFAULT FALSE,
    retention_days INTEGER NOT NULL DEFAULT 180 CHECK (retention_days BETWEEN 1 AND 3650),
    archive_before_cleanup BOOLEAN NOT NULL DEFAULT TRUE,
    cleanup_time VARCHAR(5) NOT NULL DEFAULT '02:00',
    last_run_at TIMESTAMP,
    last_run_deleted_count INTEGER NOT NULL DEFAULT 0,
    last_run_status VARCHAR(30) NOT NULL DEFAULT 'NEVER',
    last_run_message VARCHAR(500),
    last_archive_file VARCHAR(500),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO audit_log_retention_config (
    id,
    enabled,
    retention_days,
    archive_before_cleanup,
    cleanup_time,
    last_run_deleted_count,
    last_run_status,
    updated_at
) VALUES (
    1,
    FALSE,
    180,
    TRUE,
    '02:00',
    0,
    'NEVER',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
