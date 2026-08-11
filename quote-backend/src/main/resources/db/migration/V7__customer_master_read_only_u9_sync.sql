UPDATE system_permission
SET enabled = false,
    assignable = false,
    visible = false,
    parent_id = COALESCE((SELECT id FROM system_permission WHERE permission_code = 'system:customer:view'), parent_id),
    permission_name = CASE permission_code
        WHEN 'system:customer:create' THEN '新增客户（已下线）'
        WHEN 'system:customer:update' THEN '编辑客户（已下线）'
        WHEN 'system:customer:delete' THEN '删除客户（已下线）'
        ELSE permission_name
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN ('system:customer:create', 'system:customer:update', 'system:customer:delete');

DELETE FROM system_role_permission role_permission
USING system_permission permission
WHERE role_permission.permission_id = permission.id
  AND permission.permission_code IN ('system:customer:create', 'system:customer:update', 'system:customer:delete');

INSERT INTO system_permission (
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
SELECT view_permission.id,
       'BUTTON',
       'system:customer:sync',
       '同步U9客户',
       NULL,
       NULL,
       NULL,
       846,
       false,
       true,
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP,
       true
FROM system_permission view_permission
WHERE view_permission.permission_code = 'system:customer:view'
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_name = EXCLUDED.permission_name,
    route_path = NULL,
    component_key = NULL,
    icon = NULL,
    sort_order = EXCLUDED.sort_order,
    visible = false,
    enabled = true,
    assignable = true,
    updated_at = CURRENT_TIMESTAMP;

DELETE FROM system_role_permission role_permission
USING system_permission permission, system_role role
WHERE role_permission.permission_id = permission.id
  AND role_permission.role_id = role.id
  AND permission.permission_code = 'system:customer:sync'
  AND role.role_code <> 'SUPER_ADMIN';

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code = 'system:customer:sync'
ON CONFLICT (role_id, permission_id) DO NOTHING;
