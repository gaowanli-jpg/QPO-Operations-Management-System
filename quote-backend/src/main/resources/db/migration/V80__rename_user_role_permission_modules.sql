UPDATE system_permission
SET permission_name = CASE permission_code
    WHEN 'system:user:view' THEN '用户管理'
    WHEN 'system:role:view' THEN '角色权限'
    ELSE permission_name
END
WHERE permission_code IN ('system:user:view', 'system:role:view');
