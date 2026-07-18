UPDATE system_permission
SET route_path = '/roles',
    component_key = 'SystemRoleManage',
    visible = TRUE,
    icon = 'Lock',
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code = 'system:role:view';