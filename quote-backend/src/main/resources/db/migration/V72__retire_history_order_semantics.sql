UPDATE system_permission
SET visible = FALSE,
    assignable = FALSE,
    permission_name = CASE permission_code
        WHEN 'market:order:delete' THEN '删除订单兼容接口（已废弃）'
        WHEN 'market:history-order:view' THEN '历史订单兼容权限（已废弃）'
        WHEN 'market:history-order:customer-select' THEN '历史订单客户选择兼容接口（已废弃）'
        WHEN 'market:history-order:file-preview' THEN '历史订单文件预览兼容接口（已废弃）'
        ELSE permission_name
    END,
    component_key = CASE
        WHEN permission_code = 'market:history-order:view' THEN NULL
        ELSE component_key
    END,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'market:order:delete',
    'market:history-order:view',
    'market:history-order:customer-select',
    'market:history-order:file-preview'
);
