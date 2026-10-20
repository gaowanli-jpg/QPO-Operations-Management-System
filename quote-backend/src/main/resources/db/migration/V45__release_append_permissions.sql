-- Dedicated market module for appending imported sales-order lines to an existing U9-unsynced sales order.
-- No common:* permissions are introduced.

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
    (218, NULL, 'MENU', 'market:release-append:view', '追加提单', '/release-append', 'ReleaseAppendManage', NULL, 145, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (219, 218, 'API', 'market:release-append:customer-select', '追加提单选择客户', NULL, NULL, NULL, 1451, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (220, 218, 'API', 'market:release-append:order-select', '追加提单选择销售订单', NULL, NULL, NULL, 1452, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (221, 218, 'BUTTON', 'market:release-append:create', '追加销售提单', NULL, NULL, NULL, 1453, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
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
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
  AND permission.permission_code IN (
      'market:release-append:view',
      'market:release-append:customer-select',
      'market:release-append:order-select'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
  AND permission.permission_code = 'market:release-append:create'
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 221), true);
