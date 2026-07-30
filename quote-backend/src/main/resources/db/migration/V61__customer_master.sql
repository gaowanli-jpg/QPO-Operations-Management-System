CREATE TABLE IF NOT EXISTS customer_master (
    id BIGSERIAL PRIMARY KEY,
    customer_code VARCHAR(200) NOT NULL,
    customer_short_name VARCHAR(200) NOT NULL,
    customer_name VARCHAR(200) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_customer_master_code UNIQUE (customer_code),
    CONSTRAINT uk_customer_master_short_name UNIQUE (customer_short_name),
    CONSTRAINT uk_customer_master_name UNIQUE (customer_name)
);

CREATE INDEX IF NOT EXISTS idx_customer_master_enabled
    ON customer_master(enabled);

INSERT INTO customer_master (customer_code, customer_short_name, customer_name)
SELECT DISTINCT TRIM(customer_name), TRIM(customer_name), TRIM(customer_name)
FROM order_rfq
WHERE customer_name IS NOT NULL
  AND TRIM(customer_name) <> ''
ON CONFLICT DO NOTHING;

WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'system:customer:view', '客户资料', '/customers', 'CustomerManage', 'Users', 845, TRUE, TRUE),
        ('system:customer:view', 'BUTTON', 'system:customer:create', '新增客户', NULL, NULL, NULL, 846, FALSE, TRUE),
        ('system:customer:view', 'BUTTON', 'system:customer:update', '编辑客户', NULL, NULL, NULL, 847, FALSE, TRUE),
        ('system:customer:view', 'BUTTON', 'system:customer:delete', '删除客户', NULL, NULL, NULL, 848, FALSE, TRUE)
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
    'system:customer:view',
    'system:customer:create',
    'system:customer:update',
    'system:customer:delete'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
