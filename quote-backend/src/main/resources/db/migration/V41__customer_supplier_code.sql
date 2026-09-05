-- Add QPO-local supplier code on top of the U9 customer cache.

ALTER TABLE customer_master
    ADD COLUMN IF NOT EXISTS supplier_code VARCHAR(64);

CREATE INDEX IF NOT EXISTS idx_customer_master_supplier_code
    ON customer_master (supplier_code);

INSERT INTO system_permission (
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
)
SELECT
    parent.id,
    'BUTTON',
    'system:customer:supplier-code-update',
    '维护客户供应商代码',
    NULL,
    NULL,
    NULL,
    847,
    false,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    true
FROM system_permission parent
WHERE parent.permission_code = 'system:customer:view'
ON CONFLICT (permission_code) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
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
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER')
  AND permission.permission_code = 'system:customer:supplier-code-update'
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 216), true);
