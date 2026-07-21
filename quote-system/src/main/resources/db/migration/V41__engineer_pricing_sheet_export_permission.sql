WITH permission_row(parent_code, permission_type, permission_code, permission_name, sort_order, visible, assignable) AS (
    VALUES
        ('engineer:pricing:view', 'BUTTON', 'engineer:pricing:export-sheet', '生成核价单', 225, FALSE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    parent.id,
    permission_row.permission_type,
    permission_row.permission_code,
    permission_row.permission_name,
    NULL,
    NULL,
    NULL,
    permission_row.sort_order,
    permission_row.visible,
    TRUE,
    permission_row.assignable
FROM permission_row
JOIN system_permission parent ON parent.permission_code = permission_row.parent_code
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_name = EXCLUDED.permission_name,
    permission_type = EXCLUDED.permission_type,
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
JOIN system_permission p ON p.permission_code = 'engineer:pricing:export-sheet'
WHERE r.role_code IN ('SUPER_ADMIN', 'ENGINEER_USER', 'ENGINEER_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;
