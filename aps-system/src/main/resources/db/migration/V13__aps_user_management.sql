CREATE TABLE IF NOT EXISTS aps_user (
    id BIGSERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(128) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(30) NOT NULL DEFAULT 'ADMIN',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    force_password_change BOOLEAN NOT NULL DEFAULT TRUE,
    failed_login_attempts INTEGER NOT NULL DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    password_changed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_user_username UNIQUE (username)
);

CREATE INDEX IF NOT EXISTS idx_aps_user_role_enabled ON aps_user(role, enabled, id);

INSERT INTO aps_user (username, password_hash, display_name, role, enabled, force_password_change, failed_login_attempts)
VALUES ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '管理员', 'ADMIN', TRUE, TRUE, 0)
ON CONFLICT (username) DO NOTHING;
