-- Data Center SSO login configuration and replay-protection nonce store.
-- Append-only migration; do not modify baseline migrations.

CREATE TABLE system_data_center_sso_config (
    id bigserial PRIMARY KEY,
    config_name varchar(100) NOT NULL,
    active boolean NOT NULL DEFAULT false,
    enabled boolean NOT NULL DEFAULT true,
    app_code varchar(40) NOT NULL DEFAULT 'QPO',
    shared_secret_cipher text,
    timestamp_skew_seconds integer NOT NULL DEFAULT 120,
    nonce_ttl_minutes integer NOT NULL DEFAULT 10,
    default_redirect varchar(255) NOT NULL DEFAULT '/dashboard',
    allowed_redirect_prefixes text NOT NULL DEFAULT '/,/dashboard,/orders',
    last_test_status varchar(20),
    last_test_message varchar(500),
    last_test_at timestamp without time zone,
    remark varchar(500),
    created_by varchar(64),
    updated_by varchar(64),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_system_data_center_sso_test_status
        CHECK (last_test_status IS NULL OR last_test_status IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_system_data_center_sso_timestamp_skew
        CHECK (timestamp_skew_seconds BETWEEN 30 AND 3600),
    CONSTRAINT ck_system_data_center_sso_nonce_ttl
        CHECK (nonce_ttl_minutes BETWEEN 1 AND 1440),
    CONSTRAINT ck_system_data_center_sso_app_code_not_blank
        CHECK (btrim(app_code) <> ''),
    CONSTRAINT ck_system_data_center_sso_default_redirect_internal
        CHECK (default_redirect LIKE '/%' AND default_redirect NOT LIKE '//%')
);

CREATE UNIQUE INDEX uk_system_data_center_sso_active
    ON system_data_center_sso_config(active)
    WHERE active = true;

CREATE INDEX idx_system_data_center_sso_enabled
    ON system_data_center_sso_config(enabled);

CREATE TABLE auth_data_center_sso_nonce (
    id bigserial PRIMARY KEY,
    nonce varchar(200),
    nonce_hash char(64) NOT NULL,
    username varchar(80) NOT NULL,
    app_code varchar(40) NOT NULL,
    request_timestamp bigint NOT NULL,
    used_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at timestamp without time zone NOT NULL,
    result varchar(20),
    failure_reason varchar(500),
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_auth_data_center_sso_nonce_result
        CHECK (result IS NULL OR result IN ('SUCCESS', 'FAILED')),
    CONSTRAINT ck_auth_data_center_sso_nonce_hash_not_blank
        CHECK (btrim(nonce_hash) <> '')
);

CREATE UNIQUE INDEX uk_auth_data_center_sso_nonce
    ON auth_data_center_sso_nonce(nonce_hash);

CREATE INDEX idx_auth_data_center_sso_nonce_expires_at
    ON auth_data_center_sso_nonce(expires_at);

WITH parent AS (
    SELECT id
    FROM system_permission
    WHERE permission_code = 'system:config:view'
    LIMIT 1
)
INSERT INTO system_permission (
    id,
    parent_id,
    permission_type,
    permission_code,
    permission_name,
    route_path,
    component_key,
    icon,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at,
    assignable
) VALUES
    (206, COALESCE((SELECT id FROM parent), 0), 'BUTTON', 'system:config:sso:update', 'Maintain Data Center SSO config', NULL, NULL, NULL, 8499, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (207, COALESCE((SELECT id FROM parent), 0), 'BUTTON', 'system:config:sso:test', 'Test Data Center SSO config', NULL, NULL, NULL, 8500, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (208, COALESCE((SELECT id FROM parent), 0), 'BUTTON', 'system:config:sso:activate', 'Activate Data Center SSO config', NULL, NULL, NULL, 8501, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_code = EXCLUDED.permission_code,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role_id, permission_id, CURRENT_TIMESTAMP
FROM (VALUES
    (1, 206), (1, 207), (1, 208)
) AS grants(role_id, permission_id)
WHERE EXISTS (SELECT 1 FROM system_role WHERE id = grants.role_id)
  AND EXISTS (SELECT 1 FROM system_permission WHERE id = grants.permission_id)
ON CONFLICT (role_id, permission_id) DO NOTHING;
