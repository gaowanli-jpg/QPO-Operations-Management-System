WITH permission_rows(parent_code, permission_type, permission_code, permission_name, sort_order, visible, assignable) AS (
    VALUES
        ('market:rfq:view', 'BUTTON', 'market:rfq:order-type-select', '选择订单类型', 113, FALSE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    parent.id,
    permission_rows.permission_type,
    permission_rows.permission_code,
    permission_rows.permission_name,
    NULL,
    NULL,
    NULL,
    permission_rows.sort_order,
    permission_rows.visible,
    TRUE,
    permission_rows.assignable
FROM permission_rows
JOIN system_permission parent ON parent.permission_code = permission_rows.parent_code
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_name = EXCLUDED.permission_name,
    route_path = NULL,
    component_key = NULL,
    icon = NULL,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = TRUE,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

UPDATE system_permission
SET sort_order = CASE permission_code
    WHEN 'market:rfq:upload-drawing' THEN 114
    WHEN 'market:rfq:customer-select' THEN 115
    WHEN 'market:rfq:file-preview' THEN 116
    ELSE sort_order
END,
updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'market:rfq:upload-drawing',
    'market:rfq:customer-select',
    'market:rfq:file-preview'
);

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = 'market:rfq:order-type-select'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
