WITH permission_rows(parent_code, permission_type, permission_code, permission_name, sort_order, visible, assignable) AS (
    VALUES
        ('market:order:view', 'BUTTON', 'market:order:rollback-quoted', '已报价退回已核价', 128, FALSE, TRUE),
        ('market:order:view', 'BUTTON', 'market:order:rollback-priced', '已核价退回未核价', 129, FALSE, TRUE),
        ('market:order:view', 'BUTTON', 'market:order:discard', '废弃订单', 130, FALSE, TRUE),
        ('market:order:view', 'BUTTON', 'market:order:restore', '恢复废弃订单', 131, FALSE, TRUE)
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

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code IN (
    'market:order:rollback-quoted',
    'market:order:rollback-priced',
    'market:order:discard',
    'market:order:restore'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
