CREATE TABLE IF NOT EXISTS system_file_storage_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    storage_type VARCHAR(20) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    config_json JSONB NOT NULL,
    remark VARCHAR(500),
    last_test_status VARCHAR(20),
    last_test_message VARCHAR(500),
    last_test_at TIMESTAMP,
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_file_storage_config_type CHECK (storage_type IN ('LOCAL', 'MINIO')),
    CONSTRAINT ck_system_file_storage_config_test_status CHECK (
        last_test_status IS NULL OR last_test_status IN ('SUCCESS', 'FAILED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_system_file_storage_config_active
    ON system_file_storage_config(active)
    WHERE active = TRUE AND enabled = TRUE;

CREATE INDEX IF NOT EXISTS idx_system_file_storage_config_type
    ON system_file_storage_config(storage_type);

CREATE INDEX IF NOT EXISTS idx_system_file_storage_config_enabled
    ON system_file_storage_config(enabled);

CREATE TABLE IF NOT EXISTS system_file_object (
    id BIGSERIAL PRIMARY KEY,
    public_path VARCHAR(800) NOT NULL,
    storage_type VARCHAR(20) NOT NULL,
    storage_config_id BIGINT REFERENCES system_file_storage_config(id) ON DELETE SET NULL,
    object_key VARCHAR(800) NOT NULL,
    original_filename VARCHAR(255),
    content_type VARCHAR(100),
    size_bytes BIGINT,
    sha256 VARCHAR(64),
    external_url VARCHAR(1000),
    biz_type VARCHAR(50),
    biz_id VARCHAR(100),
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_file_object_storage_type CHECK (storage_type IN ('LOCAL', 'MINIO')),
    CONSTRAINT ck_system_file_object_size_nonnegative CHECK (size_bytes IS NULL OR size_bytes >= 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_system_file_object_public_path
    ON system_file_object(public_path);

CREATE INDEX IF NOT EXISTS idx_system_file_object_storage
    ON system_file_object(storage_type, storage_config_id);

CREATE INDEX IF NOT EXISTS idx_system_file_object_object_key
    ON system_file_object(object_key);

CREATE INDEX IF NOT EXISTS idx_system_file_object_biz
    ON system_file_object(biz_type, biz_id);

WITH default_local AS (
    INSERT INTO system_file_storage_config (
        name, storage_type, active, enabled, config_json, remark, created_by, updated_by
    )
    SELECT
        'Default local upload storage',
        'LOCAL',
        TRUE,
        TRUE,
        jsonb_build_object(
            'rootDir', '/data/uploads',
            'envKey', 'ERP_UPLOAD_DIR'
        ),
        'Default LOCAL storage initialized for QPO uploads; ERP_UPLOAD_DIR remains the deployment fallback.',
        'system',
        'system'
    WHERE NOT EXISTS (
        SELECT 1 FROM system_file_storage_config WHERE active = TRUE AND enabled = TRUE
    )
    RETURNING id
)
SELECT id FROM default_local;

WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'system:config:view', '系统配置', '/system-config', 'SystemConfigManage', 'Setting', 849, TRUE, TRUE),
        ('system:config:view', 'BUTTON', 'system:config:storage:update', '维护文件存储配置', NULL, NULL, NULL, 8491, FALSE, TRUE),
        ('system:config:view', 'BUTTON', 'system:config:storage:test', '测试文件存储配置', NULL, NULL, NULL, 8492, FALSE, TRUE),
        ('system:config:view', 'BUTTON', 'system:config:storage:activate', '激活文件存储配置', NULL, NULL, NULL, 8493, FALSE, TRUE)
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
    'system:config:view',
    'system:config:storage:update',
    'system:config:storage:test',
    'system:config:storage:activate'
)
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
