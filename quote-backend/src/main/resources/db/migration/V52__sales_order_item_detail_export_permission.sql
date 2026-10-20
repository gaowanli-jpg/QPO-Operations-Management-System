-- Add full sales order item detail export permission under Sales Order Management.

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM system_permission
        WHERE permission_code = 'market:sales-order:view'
    ) THEN
        RAISE EXCEPTION 'Cannot add sales-order item detail export permission: market:sales-order:view is missing';
    END IF;
END $$;

WITH parent_permission AS (
    SELECT id
    FROM system_permission
    WHERE permission_code = 'market:sales-order:view'
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
)
SELECT
    225,
    parent_permission.id,
    'BUTTON',
    'market:sales-order:item-detail-export',
    '导出明细表',
    NULL,
    NULL,
    NULL,
    133,
    false,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    true
FROM parent_permission
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
WHERE role.role_code IN ('MARKET_USER', 'MARKET_MANAGER')
  AND permission.permission_code = 'market:sales-order:item-detail-export'
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 225), true);
