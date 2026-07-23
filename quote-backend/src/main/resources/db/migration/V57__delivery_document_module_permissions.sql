WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'market:delivery-document:view', '提单表据', '/delivery-documents', 'DeliveryDocumentManage', 'Document', 146, TRUE, TRUE),
        ('market:delivery-document:view', 'API', 'market:delivery-document:customer-select', '提单表据选择客户', NULL, NULL, NULL, 147, FALSE, TRUE),
        ('market:delivery-document:view', 'API', 'market:delivery-document:order-select', '提单表据选择订单', NULL, NULL, NULL, 148, FALSE, TRUE),
        ('market:delivery-document:view', 'BUTTON', 'market:delivery-document:export', '导出提单表据', NULL, NULL, NULL, 149, FALSE, TRUE)
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
JOIN system_permission p ON p.permission_code IN ('market:delivery-document:view', 'market:delivery-document:customer-select', 'market:delivery-document:order-select')
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = 'market:delivery-document:export'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM system_role_permission srp
USING system_role r, system_permission p
WHERE srp.role_id = r.id
  AND srp.permission_id = p.id
  AND r.role_code = 'READONLY'
  AND p.permission_code = 'market:delivery-document:export';
