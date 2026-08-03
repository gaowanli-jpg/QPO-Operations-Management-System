CREATE TABLE IF NOT EXISTS system_data_center_config (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT FALSE,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    base_url VARCHAR(500),
    token_url VARCHAR(500),
    client_id VARCHAR(200),
    client_secret_cipher TEXT,
    grant_type VARCHAR(50) NOT NULL DEFAULT 'client_credentials',
    connect_timeout_ms INTEGER NOT NULL DEFAULT 5000,
    read_timeout_ms INTEGER NOT NULL DEFAULT 30000,
    token_expire_skew_seconds INTEGER NOT NULL DEFAULT 60,
    last_test_status VARCHAR(20),
    last_test_message VARCHAR(500),
    last_test_at TIMESTAMP,
    remark VARCHAR(500),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_data_center_grant_type CHECK (grant_type IN ('client_credentials')),
    CONSTRAINT ck_system_data_center_test_status CHECK (
        last_test_status IS NULL OR last_test_status IN ('SUCCESS', 'FAILED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_system_data_center_active
    ON system_data_center_config(active)
    WHERE active = TRUE;

CREATE INDEX IF NOT EXISTS idx_system_data_center_enabled
    ON system_data_center_config(enabled);

CREATE TABLE IF NOT EXISTS system_data_center_endpoint_config (
    id BIGSERIAL PRIMARY KEY,
    endpoint_code VARCHAR(80) NOT NULL,
    display_name VARCHAR(100) NOT NULL,
    target_system_code VARCHAR(30) NOT NULL,
    method VARCHAR(10) NOT NULL DEFAULT 'POST',
    path VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    timeout_ms INTEGER,
    request_adapter VARCHAR(80) NOT NULL,
    response_adapter VARCHAR(80) NOT NULL,
    request_schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    response_schema_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    last_test_status VARCHAR(20),
    last_test_message VARCHAR(500),
    last_test_at TIMESTAMP,
    remark VARCHAR(500),
    created_by VARCHAR(100),
    updated_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_data_center_endpoint_code UNIQUE (endpoint_code),
    CONSTRAINT ck_system_data_center_endpoint_target CHECK (target_system_code IN ('MES', 'WMS', 'U9')),
    CONSTRAINT ck_system_data_center_endpoint_method CHECK (method IN ('GET', 'POST')),
    CONSTRAINT ck_system_data_center_endpoint_test_status CHECK (
        last_test_status IS NULL OR last_test_status IN ('SUCCESS', 'FAILED')
    )
);

CREATE INDEX IF NOT EXISTS idx_system_data_center_endpoint_target
    ON system_data_center_endpoint_config(target_system_code);

INSERT INTO system_data_center_endpoint_config (
    endpoint_code, display_name, target_system_code, method, path, enabled,
    request_adapter, response_adapter, request_schema_json, response_schema_json,
    remark, created_by, updated_by
) VALUES
    (
        'MES_MATERIAL_RESOLVE', '获取物料号', 'MES', 'POST', NULL, TRUE,
        'MES_MATERIAL_V1', 'MES_MATERIAL_RESPONSE_V1', '{}'::jsonb, '{}'::jsonb,
        '已提零件获取物料号，支持即时返回、待回调、去重 follower 和历史复用。', 'migration', 'migration'
    ),
    (
        'WMS_INVENTORY_ALLOCATE', '获取库存信息', 'WMS', 'POST', NULL, TRUE,
        'WMS_ALLOCATION_V1', 'WMS_ALLOCATION_RESPONSE_V1', '{}'::jsonb, '{}'::jsonb,
        '已提零件获取库存锁定、在产锁定和待生产数量。', 'migration', 'migration'
    ),
    (
        'U9_SALES_ORDER_SYNC', 'U9同步', 'U9', 'POST', NULL, TRUE,
        'U9_SALES_ORDER_V1', 'U9_SALES_ORDER_RESPONSE_V1', '{}'::jsonb, '{}'::jsonb,
        '销售订单整单同步 U9。', 'migration', 'migration'
    ),
    (
        'MES_PRODUCTION_DISPATCH', '下发MES生产', 'MES', 'POST', NULL, TRUE,
        'MES_PRODUCTION_DISPATCH_V1', 'MES_PRODUCTION_DISPATCH_RESPONSE_V1', '{}'::jsonb, '{}'::jsonb,
        '锁库零件模块将待生产行下发 MES 生产流程。', 'migration', 'migration'
    )
ON CONFLICT (endpoint_code) DO UPDATE
SET display_name = EXCLUDED.display_name,
    target_system_code = EXCLUDED.target_system_code,
    method = EXCLUDED.method,
    path = COALESCE(system_data_center_endpoint_config.path, EXCLUDED.path),
    enabled = system_data_center_endpoint_config.enabled,
    request_adapter = EXCLUDED.request_adapter,
    response_adapter = EXCLUDED.response_adapter,
    request_schema_json = COALESCE(system_data_center_endpoint_config.request_schema_json, EXCLUDED.request_schema_json),
    response_schema_json = COALESCE(system_data_center_endpoint_config.response_schema_json, EXCLUDED.response_schema_json),
    remark = COALESCE(system_data_center_endpoint_config.remark, EXCLUDED.remark),
    updated_at = CURRENT_TIMESTAMP,
    updated_by = 'migration';

UPDATE system_permission
SET permission_name = CASE permission_code
    WHEN 'system:config:integration:update' THEN '维护出站接口/数据中心配置'
    WHEN 'system:config:integration:test' THEN '测试出站接口/数据中心配置'
    WHEN 'system:config:integration:activate' THEN '激活出站接口/数据中心配置'
    ELSE permission_name
END,
updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'system:config:integration:update',
    'system:config:integration:test',
    'system:config:integration:activate'
);

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
