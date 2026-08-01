WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'market:sales-order:view', '销售订单', '/sales-orders', 'SalesOrderManage', 'Files', 125, TRUE, TRUE),
        ('market:sales-order:view', 'API', 'market:sales-order:customer-select', '销售订单选择客户', NULL, NULL, NULL, 126, FALSE, TRUE),
        ('market:sales-order:view', 'API', 'market:sales-order:file-preview', '销售订单预览文件', NULL, NULL, NULL, 127, FALSE, TRUE),
        ('market:sales-order:view', 'BUTTON', 'market:sales-order:supplement-drawing', '销售订单补传图纸', NULL, NULL, NULL, 128, FALSE, TRUE),
        ('market:sales-order:view', 'BUTTON', 'market:sales-order:discard', '废弃销售订单', NULL, NULL, NULL, 129, FALSE, TRUE),
        ('market:sales-order:view', 'BUTTON', 'market:sales-order:restore', '恢复销售订单', NULL, NULL, NULL, 130, FALSE, TRUE)
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

UPDATE system_permission
SET visible = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code = 'market:history-order:view';

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code IN (
    'market:sales-order:view',
    'market:sales-order:customer-select',
    'market:sales-order:file-preview'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = 'market:sales-order:supplement-drawing'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code IN (
    'market:sales-order:discard',
    'market:sales-order:restore'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM system_role_permission srp
USING system_role r, system_permission p
WHERE srp.role_id = r.id
  AND srp.permission_id = p.id
  AND r.role_code = 'READONLY'
  AND p.permission_code IN (
      'market:sales-order:supplement-drawing',
      'market:sales-order:discard',
      'market:sales-order:restore'
  );
