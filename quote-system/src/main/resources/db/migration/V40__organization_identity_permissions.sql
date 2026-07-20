WITH org_menu AS (
    INSERT INTO system_permission (
        parent_id, permission_type, permission_code, permission_name,
        route_path, component_key, icon, sort_order, visible, enabled, assignable
    )
    VALUES (
        0, 'MENU', 'system:org:view', '组织档案',
        '/organization', 'SystemOrgManage', 'OfficeBuilding', 830, TRUE, TRUE, TRUE
    )
    ON CONFLICT (permission_code) DO UPDATE
    SET parent_id = 0,
        permission_type = EXCLUDED.permission_type,
        permission_name = EXCLUDED.permission_name,
        route_path = EXCLUDED.route_path,
        component_key = EXCLUDED.component_key,
        icon = EXCLUDED.icon,
        sort_order = EXCLUDED.sort_order,
        visible = EXCLUDED.visible,
        enabled = TRUE,
        assignable = TRUE,
        updated_at = CURRENT_TIMESTAMP
    RETURNING id
)
UPDATE system_permission child
SET parent_id = (SELECT id FROM org_menu),
    permission_type = 'MENU',
    permission_name = CASE child.permission_code
        WHEN 'system:dept:view' THEN '部门管理'
        WHEN 'system:post:view' THEN '岗位管理'
        ELSE child.permission_name
    END,
    route_path = NULL,
    component_key = NULL,
    visible = FALSE,
    enabled = TRUE,
    assignable = TRUE,
    sort_order = CASE child.permission_code
        WHEN 'system:dept:view' THEN 831
        WHEN 'system:post:view' THEN 840
        ELSE child.sort_order
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE child.permission_code IN ('system:dept:view', 'system:post:view');

WITH child_rows(parent_code, permission_type, permission_code, permission_name, sort_order) AS (
    VALUES
        ('system:dept:view', 'BUTTON', 'system:dept:manage', '维护部门', 832),
        ('system:post:view', 'BUTTON', 'system:post:manage', '维护岗位', 841)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    parent.id,
    child_rows.permission_type,
    child_rows.permission_code,
    child_rows.permission_name,
    NULL,
    NULL,
    NULL,
    child_rows.sort_order,
    FALSE,
    TRUE,
    TRUE
FROM child_rows
JOIN system_permission parent ON parent.permission_code = child_rows.parent_code
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_name = EXCLUDED.permission_name,
    route_path = NULL,
    component_key = NULL,
    icon = NULL,
    sort_order = EXCLUDED.sort_order,
    visible = FALSE,
    enabled = TRUE,
    assignable = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code IN (
    'system:org:view',
    'system:dept:view',
    'system:dept:manage',
    'system:post:view',
    'system:post:manage'
)
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;
