UPDATE system_permission
SET permission_name = CASE permission_code
    WHEN 'system:config:integration:update' THEN '维护出站接口配置'
    WHEN 'system:config:integration:test' THEN '测试出站接口配置'
    WHEN 'system:config:integration:activate' THEN '激活出站接口配置'
    ELSE permission_name
END,
updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'system:config:integration:update',
    'system:config:integration:test',
    'system:config:integration:activate'
);
