-- Add the order detail item maintenance permission.
-- This controls saving item fields, table data replacement, and drawing replacement from the order detail page.

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
) VALUES (
    209,
    3,
    'BUTTON',
    'market:order:update-item',
    '保存询价订单零件信息',
    NULL,
    NULL,
    NULL,
    122,
    false,
    true,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    true
)
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
    (1, 209),
    (3, 209)
) AS grants(role_id, permission_id)
WHERE EXISTS (SELECT 1 FROM system_role WHERE id = grants.role_id AND role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER'))
  AND EXISTS (SELECT 1 FROM system_permission WHERE id = grants.permission_id AND permission_code = 'market:order:update-item')
ON CONFLICT (role_id, permission_id) DO NOTHING;
