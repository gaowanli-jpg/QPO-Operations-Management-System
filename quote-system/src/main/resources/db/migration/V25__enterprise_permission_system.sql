CREATE TABLE IF NOT EXISTS system_dept (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    dept_code VARCHAR(80) NOT NULL,
    dept_name VARCHAR(100) NOT NULL,
    leader_user_id BIGINT,
    sort_order INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_system_dept_code UNIQUE (dept_code)
);

CREATE TABLE IF NOT EXISTS system_post (
    id BIGSERIAL PRIMARY KEY,
    post_code VARCHAR(80) NOT NULL,
    post_name VARCHAR(100) NOT NULL,
    sort_order INTEGER DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_system_post_code UNIQUE (post_code)
);

CREATE TABLE IF NOT EXISTS system_role (
    id BIGSERIAL PRIMARY KEY,
    role_code VARCHAR(80) NOT NULL,
    role_name VARCHAR(100) NOT NULL,
    description VARCHAR(255),
    super_admin BOOLEAN DEFAULT FALSE,
    data_scope VARCHAR(40) DEFAULT 'ALL',
    enabled BOOLEAN DEFAULT TRUE,
    sort_order INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_system_role_code UNIQUE (role_code)
);

CREATE TABLE IF NOT EXISTS system_user (
    id BIGSERIAL PRIMARY KEY,
    legacy_user_id INTEGER,
    username VARCHAR(80) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    display_name VARCHAR(100),
    real_name VARCHAR(100),
    phone VARCHAR(50),
    email VARCHAR(120),
    dept_id BIGINT REFERENCES system_dept(id),
    post_id BIGINT REFERENCES system_post(id),
    legacy_role VARCHAR(30),
    enabled BOOLEAN DEFAULT TRUE,
    force_password_change BOOLEAN DEFAULT FALSE,
    failed_login_attempts INTEGER DEFAULT 0,
    locked_until TIMESTAMP,
    last_login_at TIMESTAMP,
    password_changed_at TIMESTAMP,
    created_by BIGINT,
    updated_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN DEFAULT FALSE,
    CONSTRAINT uk_system_user_username UNIQUE (username),
    CONSTRAINT uk_system_user_legacy_user_id UNIQUE (legacy_user_id)
);

CREATE INDEX IF NOT EXISTS idx_system_user_dept ON system_user(dept_id);
CREATE INDEX IF NOT EXISTS idx_system_user_enabled ON system_user(enabled);
CREATE INDEX IF NOT EXISTS idx_system_user_deleted ON system_user(deleted);

CREATE TABLE IF NOT EXISTS system_user_role (
    user_id BIGINT NOT NULL REFERENCES system_user(id) ON DELETE CASCADE,
    role_id BIGINT NOT NULL REFERENCES system_role(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_user_role UNIQUE (user_id, role_id)
);

CREATE TABLE IF NOT EXISTS system_permission (
    id BIGSERIAL PRIMARY KEY,
    parent_id BIGINT DEFAULT 0,
    permission_type VARCHAR(20) NOT NULL,
    permission_code VARCHAR(120) NOT NULL,
    permission_name VARCHAR(100) NOT NULL,
    route_path VARCHAR(200),
    component_key VARCHAR(120),
    icon VARCHAR(80),
    sort_order INTEGER DEFAULT 0,
    visible BOOLEAN DEFAULT FALSE,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_permission_code UNIQUE (permission_code)
);

CREATE INDEX IF NOT EXISTS idx_system_permission_parent ON system_permission(parent_id);
CREATE INDEX IF NOT EXISTS idx_system_permission_type ON system_permission(permission_type);

CREATE TABLE IF NOT EXISTS system_role_permission (
    role_id BIGINT NOT NULL REFERENCES system_role(id) ON DELETE CASCADE,
    permission_id BIGINT NOT NULL REFERENCES system_permission(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_role_permission UNIQUE (role_id, permission_id)
);

CREATE TABLE IF NOT EXISTS system_role_data_scope_dept (
    role_id BIGINT NOT NULL REFERENCES system_role(id) ON DELETE CASCADE,
    dept_id BIGINT NOT NULL REFERENCES system_dept(id) ON DELETE CASCADE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_role_data_scope_dept UNIQUE (role_id, dept_id)
);

CREATE TABLE IF NOT EXISTS system_role_data_scope_customer (
    role_id BIGINT NOT NULL REFERENCES system_role(id) ON DELETE CASCADE,
    customer_name VARCHAR(200) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_system_role_data_scope_customer UNIQUE (role_id, customer_name)
);

INSERT INTO system_dept (dept_code, dept_name, parent_id, sort_order)
VALUES
    ('QPO', 'QPO', 0, 10),
    ('MARKET', '市场部', 0, 20),
    ('ENGINEER', '工程部', 0, 30),
    ('SYSTEM', '系统管理', 0, 40)
ON CONFLICT (dept_code) DO UPDATE
SET dept_name = EXCLUDED.dept_name,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

UPDATE system_dept child
SET parent_id = parent.id,
    updated_at = CURRENT_TIMESTAMP
FROM system_dept parent
WHERE parent.dept_code = 'QPO'
  AND child.dept_code IN ('MARKET', 'ENGINEER', 'SYSTEM');

INSERT INTO system_post (post_code, post_name, sort_order)
VALUES
    ('MARKET_SPECIALIST', '市场专员', 10),
    ('MARKET_MANAGER', '市场主管', 20),
    ('ENGINEER_QUOTER', '工程核价员', 30),
    ('ENGINEER_MANAGER', '工程主管', 40),
    ('SYSTEM_ADMIN', '系统管理员', 50)
ON CONFLICT (post_code) DO UPDATE
SET post_name = EXCLUDED.post_name,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role (role_code, role_name, description, super_admin, data_scope, sort_order)
VALUES
    ('SUPER_ADMIN', '超级管理员', '全部功能、全部数据', TRUE, 'ALL', 10),
    ('MARKET_USER', '市场专员', '市场主流程默认角色', FALSE, 'ALL', 20),
    ('MARKET_MANAGER', '市场主管', '市场全功能与部门数据', FALSE, 'ALL', 30),
    ('ENGINEER_USER', '工程核价员', '工程核价主流程默认角色', FALSE, 'ALL', 40),
    ('ENGINEER_MANAGER', '工程主管', '工程全功能与字典维护', FALSE, 'ALL', 50),
    ('AUDITOR', '审计员', '操作日志只读和导出', FALSE, 'ALL', 60),
    ('READONLY', '只读查看员', '业务数据只读查看', FALSE, 'ALL', 70)
ON CONFLICT (role_code) DO UPDATE
SET role_name = EXCLUDED.role_name,
    description = EXCLUDED.description,
    super_admin = EXCLUDED.super_admin,
    data_scope = EXCLUDED.data_scope,
    sort_order = EXCLUDED.sort_order,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name, route_path,
    component_key, icon, sort_order, visible
)
VALUES
    (0, 'MENU', 'system:user:view', '查看用户', '/users', 'UserManage', 'User', 810, TRUE),
    (0, 'BUTTON', 'system:user:create', '新增用户', NULL, NULL, NULL, 811, FALSE),
    (0, 'BUTTON', 'system:user:update', '编辑用户', NULL, NULL, NULL, 812, FALSE),
    (0, 'BUTTON', 'system:user:disable', '启停用户', NULL, NULL, NULL, 813, FALSE),
    (0, 'BUTTON', 'system:user:reset-password', '重置密码', NULL, NULL, NULL, 814, FALSE),
    (0, 'BUTTON', 'system:user:delete', '删除用户', NULL, NULL, NULL, 815, FALSE),
    (0, 'MENU', 'system:role:view', '查看角色', NULL, NULL, 'Lock', 820, FALSE),
    (0, 'BUTTON', 'system:role:create', '新增角色', NULL, NULL, NULL, 821, FALSE),
    (0, 'BUTTON', 'system:role:update', '编辑角色', NULL, NULL, NULL, 822, FALSE),
    (0, 'BUTTON', 'system:role:delete', '删除角色', NULL, NULL, NULL, 823, FALSE),
    (0, 'BUTTON', 'system:role:assign-permission', '分配角色权限', NULL, NULL, NULL, 824, FALSE),
    (0, 'BUTTON', 'system:role:assign-data-scope', '分配数据权限', NULL, NULL, NULL, 825, FALSE),
    (0, 'MENU', 'system:dept:view', '查看部门', NULL, NULL, 'OfficeBuilding', 830, FALSE),
    (0, 'BUTTON', 'system:dept:manage', '维护部门', NULL, NULL, NULL, 831, FALSE),
    (0, 'MENU', 'system:post:view', '查看岗位', NULL, NULL, 'Postcard', 840, FALSE),
    (0, 'BUTTON', 'system:post:manage', '维护岗位', NULL, NULL, NULL, 841, FALSE),
    (0, 'MENU', 'system:audit:view', '查看操作日志', '/audit-logs', 'AuditLogManage', 'DocumentChecked', 850, TRUE),
    (0, 'BUTTON', 'system:audit:export', '导出操作日志', NULL, NULL, NULL, 851, FALSE),
    (0, 'BUTTON', 'system:audit:retention', '配置日志保留策略', NULL, NULL, NULL, 852, FALSE),
    (0, 'MENU', 'market:rfq:view', '查看新单导入', '/market', 'MarketDashboard', 'Upload', 110, TRUE),
    (0, 'BUTTON', 'market:rfq:import', '导入 RFQ', NULL, NULL, NULL, 111, FALSE),
    (0, 'BUTTON', 'market:rfq:upload-drawing', '上传图纸', NULL, NULL, NULL, 112, FALSE),
    (0, 'MENU', 'market:order:view', '查看订单', '/orders', 'OrderManage', 'Tickets', 120, TRUE),
    (0, 'BUTTON', 'market:order:supplement-drawing', '补传图纸', NULL, NULL, NULL, 121, FALSE),
    (0, 'BUTTON', 'market:order:delete', '删除订单', NULL, NULL, NULL, 122, FALSE),
    (0, 'MENU', 'market:quote-process:view', '查看报价处理', '/quote-process', 'QuoteProcess', 'Money', 130, TRUE),
    (0, 'BUTTON', 'market:quote-process:update-lead-time', '维护报价交期', NULL, NULL, NULL, 131, FALSE),
    (0, 'BUTTON', 'market:quote-process:export', '导出报价单', NULL, NULL, NULL, 132, FALSE),
    (0, 'MENU', 'market:release:view', '查看议价提单', '/release', 'MarketRelease', 'Finished', 140, TRUE),
    (0, 'BUTTON', 'market:release:create', '生成提单', NULL, NULL, NULL, 141, FALSE),
    (0, 'BUTTON', 'market:release:reset', '重置提单工艺', NULL, NULL, NULL, 142, FALSE),
    (0, 'MENU', 'market:template:view', '查看报价模板', '/quote-template', 'QuoteTemplateManage', 'Files', 150, TRUE),
    (0, 'BUTTON', 'market:template:upload', '上传报价模板', NULL, NULL, NULL, 151, FALSE),
    (0, 'MENU', 'engineer:setup:view', '查看零件核价订单选择', '/engineering', 'GlobalSetup', 'Tools', 210, TRUE),
    (0, 'BUTTON', 'engineer:setup:save-config', '保存全局设定', NULL, NULL, NULL, 211, FALSE),
    (0, 'MENU', 'engineer:pricing:view', '查看逐件核价', '/pricing', 'ItemPricing', 'Cpu', 220, FALSE),
    (0, 'BUTTON', 'engineer:pricing:save-item', '保存单件核价', NULL, NULL, NULL, 221, FALSE),
    (0, 'BUTTON', 'engineer:pricing:refresh', '一键刷新核价', NULL, NULL, NULL, 222, FALSE),
    (0, 'BUTTON', 'engineer:pricing:complete-order', '完成整单报价', NULL, NULL, NULL, 223, FALSE),
    (0, 'MENU', 'engineer:dict:view', '查看材料工艺字典', '/material-process', 'MaterialProcessManage', 'Collection', 230, TRUE),
    (0, 'BUTTON', 'engineer:dict:material-manage', '维护材料', NULL, NULL, NULL, 231, FALSE),
    (0, 'BUTTON', 'engineer:dict:process-manage', '维护工艺', NULL, NULL, NULL, 232, FALSE),
    (0, 'BUTTON', 'engineer:dict:fee-manage', '维护费用', NULL, NULL, NULL, 233, FALSE),
    (0, 'API', 'common:customer:select', '选择客户', NULL, NULL, NULL, 910, FALSE),
    (0, 'API', 'common:order:select', '选择订单', NULL, NULL, NULL, 911, FALSE),
    (0, 'API', 'common:file:preview', '预览文件', NULL, NULL, NULL, 912, FALSE),
    (0, 'API', 'common:task:view', '查看异步任务', NULL, NULL, NULL, 913, FALSE)
ON CONFLICT (permission_code) DO UPDATE
SET permission_name = EXCLUDED.permission_name,
    permission_type = EXCLUDED.permission_type,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = TRUE,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_user (
    legacy_user_id, username, password_hash, display_name, legacy_role,
    dept_id, post_id, enabled, force_password_change, failed_login_attempts,
    locked_until, last_login_at, password_changed_at, created_at, updated_at
)
SELECT
    e.id,
    e.username,
    e.password_hash,
    COALESCE(e.display_name, e.username),
    e.role,
    d.id,
    p.id,
    COALESCE(e.enabled, TRUE),
    COALESCE(e.force_password_change, FALSE),
    COALESCE(e.failed_login_attempts, 0),
    e.locked_until,
    e.last_login_at,
    e.password_changed_at,
    COALESCE(e.created_at, CURRENT_TIMESTAMP),
    CURRENT_TIMESTAMP
FROM erp_user e
LEFT JOIN system_dept d ON d.dept_code = CASE UPPER(e.role)
    WHEN 'ADMIN' THEN 'SYSTEM'
    WHEN 'MARKET' THEN 'MARKET'
    WHEN 'ENGINEER' THEN 'ENGINEER'
    ELSE 'QPO'
END
LEFT JOIN system_post p ON p.post_code = CASE UPPER(e.role)
    WHEN 'ADMIN' THEN 'SYSTEM_ADMIN'
    WHEN 'MARKET' THEN 'MARKET_SPECIALIST'
    WHEN 'ENGINEER' THEN 'ENGINEER_QUOTER'
    ELSE 'SYSTEM_ADMIN'
END
ON CONFLICT (username) DO UPDATE
SET legacy_user_id = EXCLUDED.legacy_user_id,
    password_hash = EXCLUDED.password_hash,
    display_name = EXCLUDED.display_name,
    legacy_role = EXCLUDED.legacy_role,
    dept_id = EXCLUDED.dept_id,
    post_id = EXCLUDED.post_id,
    enabled = EXCLUDED.enabled,
    force_password_change = EXCLUDED.force_password_change,
    failed_login_attempts = EXCLUDED.failed_login_attempts,
    locked_until = EXCLUDED.locked_until,
    last_login_at = EXCLUDED.last_login_at,
    password_changed_at = EXCLUDED.password_changed_at,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_user_role (user_id, role_id)
SELECT su.id, sr.id
FROM system_user su
JOIN erp_user e ON e.id = su.legacy_user_id
JOIN system_role sr ON sr.role_code = CASE UPPER(e.role)
    WHEN 'ADMIN' THEN 'SUPER_ADMIN'
    WHEN 'MARKET' THEN 'MARKET_USER'
    WHEN 'ENGINEER' THEN 'ENGINEER_USER'
    ELSE 'READONLY'
END
ON CONFLICT (user_id, role_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
CROSS JOIN system_permission p
WHERE r.role_code = 'SUPER_ADMIN'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = ANY (ARRAY[
    'market:rfq:view', 'market:rfq:import', 'market:rfq:upload-drawing',
    'market:order:view', 'market:order:supplement-drawing',
    'market:quote-process:view', 'market:quote-process:update-lead-time', 'market:quote-process:export',
    'market:release:view', 'market:release:create',
    'common:customer:select', 'common:order:select', 'common:file:preview', 'common:task:view'
])
WHERE r.role_code = 'MARKET_USER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = ANY (ARRAY[
    'market:rfq:view', 'market:rfq:import', 'market:rfq:upload-drawing',
    'market:order:view', 'market:order:supplement-drawing', 'market:order:delete',
    'market:quote-process:view', 'market:quote-process:update-lead-time', 'market:quote-process:export',
    'market:release:view', 'market:release:create', 'market:release:reset',
    'common:customer:select', 'common:order:select', 'common:file:preview', 'common:task:view'
])
WHERE r.role_code = 'MARKET_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = ANY (ARRAY[
    'engineer:setup:view', 'engineer:setup:save-config',
    'engineer:pricing:view', 'engineer:pricing:save-item', 'engineer:pricing:refresh', 'engineer:pricing:complete-order',
    'engineer:dict:view',
    'common:customer:select', 'common:order:select', 'common:file:preview', 'common:task:view'
])
WHERE r.role_code = 'ENGINEER_USER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = ANY (ARRAY[
    'engineer:setup:view', 'engineer:setup:save-config',
    'engineer:pricing:view', 'engineer:pricing:save-item', 'engineer:pricing:refresh', 'engineer:pricing:complete-order',
    'engineer:dict:view', 'engineer:dict:material-manage', 'engineer:dict:process-manage', 'engineer:dict:fee-manage',
    'common:customer:select', 'common:order:select', 'common:file:preview', 'common:task:view'
])
WHERE r.role_code = 'ENGINEER_MANAGER'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = ANY (ARRAY[
    'system:audit:view', 'system:audit:export'
])
WHERE r.role_code = 'AUDITOR'
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = ANY (ARRAY[
    'market:rfq:view', 'market:order:view', 'market:quote-process:view', 'market:release:view',
    'engineer:setup:view', 'engineer:pricing:view', 'engineer:dict:view',
    'common:customer:select', 'common:order:select', 'common:file:preview', 'common:task:view'
])
WHERE r.role_code = 'READONLY'
ON CONFLICT (role_id, permission_id) DO NOTHING;
