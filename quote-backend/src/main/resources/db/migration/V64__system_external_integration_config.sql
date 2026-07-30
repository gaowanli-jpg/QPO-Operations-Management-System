CREATE TABLE IF NOT EXISTS system_external_integration_config (
    id BIGSERIAL PRIMARY KEY,
    system_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    auth_type VARCHAR(30) NOT NULL DEFAULT 'NONE',
    config_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    remark VARCHAR(500),
    last_test_status VARCHAR(20),
    last_test_message VARCHAR(500),
    last_test_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_external_integration_system CHECK (system_code IN ('U9', 'MES', 'WMS')),
    CONSTRAINT ck_system_external_integration_auth CHECK (auth_type IN ('NONE', 'BEARER_TOKEN', 'U9_AUTH_LOGIN')),
    CONSTRAINT ck_system_external_integration_test_status CHECK (
        last_test_status IS NULL OR last_test_status IN ('SUCCESS', 'FAILED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_system_external_integration_active
    ON system_external_integration_config(system_code, active)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_system_external_integration_system
    ON system_external_integration_config(system_code);

CREATE INDEX IF NOT EXISTS idx_system_external_integration_enabled
    ON system_external_integration_config(enabled);

WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        ('system:config:view', 'BUTTON', 'system:config:integration:update', '维护系统对接配置', NULL, NULL, NULL, 8494, FALSE, TRUE),
        ('system:config:view', 'BUTTON', 'system:config:integration:test', '测试系统对接配置', NULL, NULL, NULL, 8495, FALSE, TRUE),
        ('system:config:view', 'BUTTON', 'system:config:integration:activate', '激活系统对接配置', NULL, NULL, NULL, 8496, FALSE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    COALESCE(parent.id, 0),
    permission_rows.permission_type,
    permission_rows.permission_code,
    permission_rows.permission_name,
    permission_rows.route_path,
    permission_rows.component_key,
    permission_rows.icon,
    permission_rows.sort_order,
    permission_rows.visible,
    TRUE,
    permission_rows.assignable
FROM permission_rows
LEFT JOIN system_permission parent ON parent.permission_code = permission_rows.parent_code
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = TRUE,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code IN (
    'system:config:integration:update',
    'system:config:integration:test',
    'system:config:integration:activate'
)
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
