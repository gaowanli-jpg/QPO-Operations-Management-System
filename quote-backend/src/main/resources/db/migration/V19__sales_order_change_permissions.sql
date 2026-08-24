-- Sales order change module permissions.
-- The module is a local QPO correction entry for sales orders already synced to U9.

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
    (202, 0, 'MENU', 'change:sales-order:view', '销售变更', '/sales-order-change', 'SalesOrderChangeManage', NULL, 700, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (203, 202, 'API', 'change:sales-order:customer-select', '销售变更选择客户', NULL, NULL, NULL, 701, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (204, 202, 'API', 'change:sales-order:order-select', '销售变更选择订单', NULL, NULL, NULL, 702, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (205, 202, 'BUTTON', 'change:sales-order:update', '保存销售变更', NULL, NULL, NULL, 703, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
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
    (1, 202), (1, 203), (1, 204), (1, 205),
    (2, 202), (2, 203), (2, 204), (2, 205),
    (3, 202), (3, 203), (3, 204), (3, 205),
    (7, 202), (7, 203), (7, 204)
) AS grants(role_id, permission_id)
WHERE EXISTS (SELECT 1 FROM system_role WHERE id = grants.role_id)
  AND EXISTS (SELECT 1 FROM system_permission WHERE id = grants.permission_id)
ON CONFLICT (role_id, permission_id) DO NOTHING;
