CREATE TABLE IF NOT EXISTS system_inbound_callback_config (
    id BIGSERIAL PRIMARY KEY,
    callback_code VARCHAR(60) NOT NULL,
    source_system_code VARCHAR(30) NOT NULL,
    name VARCHAR(100) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    token_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    public_base_url VARCHAR(500),
    callback_path VARCHAR(300) NOT NULL,
    token_hash VARCHAR(128),
    token_mask VARCHAR(30),
    token_updated_at TIMESTAMP,
    last_validate_status VARCHAR(20),
    last_validate_message VARCHAR(500),
    last_validate_at TIMESTAMP,
    remark VARCHAR(500),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_inbound_callback_code UNIQUE (callback_code),
    CONSTRAINT ck_system_inbound_callback_source CHECK (source_system_code IN ('MES')),
    CONSTRAINT ck_system_inbound_callback_validate_status CHECK (
        last_validate_status IS NULL OR last_validate_status IN ('SUCCESS', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_system_inbound_callback_source
    ON system_inbound_callback_config(source_system_code);

CREATE INDEX IF NOT EXISTS idx_system_inbound_callback_enabled
    ON system_inbound_callback_config(enabled);

WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        ('system:config:view', 'BUTTON', 'system:config:inbound:update', '维护入站接口配置', NULL, NULL, NULL, 8497, FALSE, TRUE),
        ('system:config:view', 'BUTTON', 'system:config:inbound:token-rotate', '轮换入站回调Token', NULL, NULL, NULL, 8498, FALSE, TRUE)
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
    'system:config:inbound:update',
    'system:config:inbound:token-rotate'
)
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
