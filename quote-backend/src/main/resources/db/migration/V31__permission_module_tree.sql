ALTER TABLE system_permission
    ADD COLUMN IF NOT EXISTS assignable BOOLEAN DEFAULT TRUE;

UPDATE system_permission
SET assignable = COALESCE(assignable, TRUE),
    updated_at = CURRENT_TIMESTAMP
WHERE assignable IS NULL;

WITH menu_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'market:rfq:view', '新单导入', '/market', 'MarketDashboard', 'Upload', 110, TRUE, TRUE),
        (NULL, 'MENU', 'market:order:view', '订单管理', '/orders', 'OrderManage', 'Tickets', 120, TRUE, TRUE),
        (NULL, 'MENU', 'market:history-order:view', '历史订单', '/history-orders', 'HistoryOrderManage', 'Files', 125, TRUE, TRUE),
        (NULL, 'MENU', 'market:quote-process:view', '报价处理', '/quote-process', 'QuoteProcess', 'Money', 130, TRUE, TRUE),
        (NULL, 'MENU', 'market:release:view', '议价提单', '/release', 'MarketRelease', 'Finished', 140, TRUE, TRUE),
        (NULL, 'MENU', 'market:template:view', '报价模板', '/quote-template', 'QuoteTemplateManage', 'Files', 150, TRUE, TRUE),
        (NULL, 'MENU', 'engineer:setup:view', '零件核价', '/engineering', 'GlobalSetup', 'Tools', 210, TRUE, TRUE),
        ('engineer:setup:view', 'MENU', 'engineer:pricing:view', '逐件核价', '/pricing', 'ItemPricing', 'Cpu', 220, FALSE, TRUE),
        (NULL, 'MENU', 'engineer:dict:view', '材料工艺', '/material-process', 'MaterialProcessManage', 'Collection', 230, TRUE, TRUE),
        (NULL, 'MENU', 'system:user:view', '用户管理', '/users', 'UserManage', 'User', 810, TRUE, TRUE),
        (NULL, 'MENU', 'system:role:view', '角色管理', '/roles', 'SystemRoleManage', 'Lock', 820, TRUE, TRUE),
        (NULL, 'MENU', 'system:dept:view', '查看部门', NULL, NULL, 'OfficeBuilding', 830, FALSE, TRUE),
        (NULL, 'MENU', 'system:post:view', '查看岗位', NULL, NULL, 'Postcard', 840, FALSE, TRUE),
        (NULL, 'MENU', 'system:audit:view', '操作日志', '/audit-logs', 'AuditLogManage', 'DocumentChecked', 850, TRUE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    COALESCE(parent.id, 0),
    perm_row.permission_type,
    perm_row.permission_code,
    perm_row.permission_name,
    perm_row.route_path,
    perm_row.component_key,
    perm_row.icon,
    perm_row.sort_order,
    perm_row.visible,
    TRUE,
    perm_row.assignable
FROM menu_rows perm_row
LEFT JOIN system_permission parent ON parent.permission_code = perm_row.parent_code
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_name = EXCLUDED.permission_name,
    permission_type = EXCLUDED.permission_type,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = TRUE,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

WITH child_rows(parent_code, permission_type, permission_code, permission_name, sort_order, visible, assignable) AS (
    VALUES
        ('market:rfq:view', 'BUTTON', 'market:rfq:import', '导入 RFQ', 111, FALSE, TRUE),
        ('market:rfq:view', 'BUTTON', 'market:rfq:upload-drawing', '上传图纸', 112, FALSE, TRUE),
        ('market:rfq:view', 'API', 'market:rfq:customer-select', '新单导入选择客户', 113, FALSE, TRUE),
        ('market:rfq:view', 'API', 'market:rfq:file-preview', '新单导入预览文件', 114, FALSE, TRUE),
        ('market:order:view', 'BUTTON', 'market:order:supplement-drawing', '补传图纸', 121, FALSE, TRUE),
        ('market:order:view', 'BUTTON', 'market:order:delete', '删除订单', 122, FALSE, TRUE),
        ('market:order:view', 'API', 'market:order:customer-select', '订单管理选择客户', 123, FALSE, TRUE),
        ('market:order:view', 'API', 'market:order:file-preview', '订单管理预览文件', 124, FALSE, TRUE),
        ('market:history-order:view', 'API', 'market:history-order:customer-select', '历史订单选择客户', 126, FALSE, TRUE),
        ('market:history-order:view', 'API', 'market:history-order:file-preview', '历史订单预览文件', 127, FALSE, TRUE),
        ('market:quote-process:view', 'BUTTON', 'market:quote-process:update-lead-time', '维护报价交期', 131, FALSE, TRUE),
        ('market:quote-process:view', 'BUTTON', 'market:quote-process:export', '导出报价单', 132, FALSE, TRUE),
        ('market:quote-process:view', 'API', 'market:quote-process:customer-select', '报价处理选择客户', 133, FALSE, TRUE),
        ('market:quote-process:view', 'API', 'market:quote-process:order-select', '报价处理选择订单', 134, FALSE, TRUE),
        ('market:quote-process:view', 'API', 'market:quote-process:task-view', '报价处理查看异步任务', 135, FALSE, TRUE),
        ('market:release:view', 'BUTTON', 'market:release:create', '生成提单', 141, FALSE, TRUE),
        ('market:release:view', 'BUTTON', 'market:release:reset', '重置提单工艺', 142, FALSE, TRUE),
        ('market:release:view', 'API', 'market:release:customer-select', '议价提单选择客户', 143, FALSE, TRUE),
        ('market:release:view', 'API', 'market:release:order-select', '议价提单选择订单', 144, FALSE, TRUE),
        ('market:release:view', 'API', 'market:release:task-view', '议价提单查看异步任务', 145, FALSE, TRUE),
        ('market:template:view', 'BUTTON', 'market:template:upload', '上传报价模板', 151, FALSE, TRUE),
        ('engineer:setup:view', 'BUTTON', 'engineer:setup:save-config', '保存全局设定', 211, FALSE, TRUE),
        ('engineer:setup:view', 'API', 'engineer:setup:customer-select', '零件核价选择客户', 212, FALSE, TRUE),
        ('engineer:setup:view', 'API', 'engineer:setup:order-select', '零件核价选择订单', 213, FALSE, TRUE),
        ('engineer:setup:view', 'API', 'engineer:setup:file-preview', '零件核价预览图纸', 214, FALSE, TRUE),
        ('engineer:pricing:view', 'BUTTON', 'engineer:pricing:save-item', '保存单件核价', 221, FALSE, TRUE),
        ('engineer:pricing:view', 'BUTTON', 'engineer:pricing:refresh', '一键刷新核价', 222, FALSE, TRUE),
        ('engineer:pricing:view', 'BUTTON', 'engineer:pricing:complete-order', '完成整单报价', 223, FALSE, TRUE),
        ('engineer:pricing:view', 'API', 'engineer:pricing:task-view', '逐件核价查看异步任务', 224, FALSE, TRUE),
        ('engineer:dict:view', 'BUTTON', 'engineer:dict:material-manage', '维护材料', 231, FALSE, TRUE),
        ('engineer:dict:view', 'BUTTON', 'engineer:dict:process-manage', '维护工艺', 232, FALSE, TRUE),
        ('engineer:dict:view', 'BUTTON', 'engineer:dict:fee-manage', '维护费用', 233, FALSE, TRUE),
        ('system:user:view', 'BUTTON', 'system:user:create', '新增用户', 811, FALSE, TRUE),
        ('system:user:view', 'BUTTON', 'system:user:update', '编辑用户', 812, FALSE, TRUE),
        ('system:user:view', 'BUTTON', 'system:user:disable', '启停用户', 813, FALSE, TRUE),
        ('system:user:view', 'BUTTON', 'system:user:reset-password', '重置密码', 814, FALSE, TRUE),
        ('system:user:view', 'BUTTON', 'system:user:delete', '删除用户', 815, FALSE, TRUE),
        ('system:role:view', 'BUTTON', 'system:role:create', '新增角色', 821, FALSE, TRUE),
        ('system:role:view', 'BUTTON', 'system:role:update', '编辑角色', 822, FALSE, TRUE),
        ('system:role:view', 'BUTTON', 'system:role:delete', '删除角色', 823, FALSE, TRUE),
        ('system:role:view', 'BUTTON', 'system:role:assign-permission', '分配角色权限', 824, FALSE, TRUE),
        ('system:role:view', 'BUTTON', 'system:role:assign-data-scope', '分配数据权限', 825, FALSE, TRUE),
        ('system:dept:view', 'BUTTON', 'system:dept:manage', '维护部门', 831, FALSE, TRUE),
        ('system:post:view', 'BUTTON', 'system:post:manage', '维护岗位', 841, FALSE, TRUE),
        ('system:audit:view', 'BUTTON', 'system:audit:export', '导出操作日志', 851, FALSE, TRUE),
        ('system:audit:view', 'BUTTON', 'system:audit:retention', '配置日志保留策略', 852, FALSE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    parent.id,
    perm_row.permission_type,
    perm_row.permission_code,
    perm_row.permission_name,
    NULL,
    NULL,
    NULL,
    perm_row.sort_order,
    perm_row.visible,
    TRUE,
    perm_row.assignable
FROM child_rows perm_row
JOIN system_permission parent ON parent.permission_code = perm_row.parent_code
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

UPDATE system_permission
SET parent_id = 0,
    visible = FALSE,
    enabled = TRUE,
    assignable = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'common:customer:select',
    'common:order:select',
    'common:file:preview',
    'common:task:view'
);

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
CROSS JOIN system_permission p
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

WITH role_grants(role_code, permission_codes) AS (
    VALUES
        ('MARKET_USER', ARRAY[
            'market:rfq:view', 'market:rfq:import', 'market:rfq:upload-drawing',
            'market:rfq:customer-select', 'market:rfq:file-preview',
            'market:order:view', 'market:order:supplement-drawing',
            'market:order:customer-select', 'market:order:file-preview',
            'market:history-order:view', 'market:history-order:customer-select', 'market:history-order:file-preview',
            'market:quote-process:view', 'market:quote-process:update-lead-time', 'market:quote-process:export',
            'market:quote-process:customer-select', 'market:quote-process:order-select', 'market:quote-process:task-view',
            'market:release:view', 'market:release:create',
            'market:release:customer-select', 'market:release:order-select', 'market:release:task-view'
        ]::VARCHAR[]),
        ('MARKET_MANAGER', ARRAY[
            'market:rfq:view', 'market:rfq:import', 'market:rfq:upload-drawing',
            'market:rfq:customer-select', 'market:rfq:file-preview',
            'market:order:view', 'market:order:supplement-drawing', 'market:order:delete',
            'market:order:customer-select', 'market:order:file-preview',
            'market:history-order:view', 'market:history-order:customer-select', 'market:history-order:file-preview',
            'market:quote-process:view', 'market:quote-process:update-lead-time', 'market:quote-process:export',
            'market:quote-process:customer-select', 'market:quote-process:order-select', 'market:quote-process:task-view',
            'market:release:view', 'market:release:create', 'market:release:reset',
            'market:release:customer-select', 'market:release:order-select', 'market:release:task-view',
            'market:template:view', 'market:template:upload'
        ]::VARCHAR[]),
        ('ENGINEER_USER', ARRAY[
            'engineer:setup:view', 'engineer:setup:save-config',
            'engineer:setup:customer-select', 'engineer:setup:order-select', 'engineer:setup:file-preview',
            'engineer:pricing:view', 'engineer:pricing:save-item', 'engineer:pricing:refresh',
            'engineer:pricing:complete-order', 'engineer:pricing:task-view',
            'engineer:dict:view'
        ]::VARCHAR[]),
        ('ENGINEER_MANAGER', ARRAY[
            'engineer:setup:view', 'engineer:setup:save-config',
            'engineer:setup:customer-select', 'engineer:setup:order-select', 'engineer:setup:file-preview',
            'engineer:pricing:view', 'engineer:pricing:save-item', 'engineer:pricing:refresh',
            'engineer:pricing:complete-order', 'engineer:pricing:task-view',
            'engineer:dict:view', 'engineer:dict:material-manage',
            'engineer:dict:process-manage', 'engineer:dict:fee-manage'
        ]::VARCHAR[]),
        ('AUDITOR', ARRAY[
            'system:audit:view', 'system:audit:export'
        ]::VARCHAR[]),
        ('READONLY', ARRAY[
            'market:rfq:view', 'market:rfq:customer-select', 'market:rfq:file-preview',
            'market:order:view', 'market:order:customer-select', 'market:order:file-preview',
            'market:history-order:view', 'market:history-order:customer-select', 'market:history-order:file-preview',
            'market:quote-process:view', 'market:quote-process:customer-select',
            'market:quote-process:order-select', 'market:quote-process:task-view',
            'market:release:view', 'market:release:customer-select',
            'market:release:order-select', 'market:release:task-view',
            'engineer:setup:view', 'engineer:setup:customer-select',
            'engineer:setup:order-select', 'engineer:setup:file-preview',
            'engineer:pricing:view', 'engineer:pricing:task-view',
            'engineer:dict:view'
        ]::VARCHAR[])
)
INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM role_grants grant_row
JOIN system_role r ON r.role_code = grant_row.role_code
JOIN system_permission p ON p.permission_code = ANY (grant_row.permission_codes)
ON CONFLICT (role_id, permission_id) DO NOTHING;
