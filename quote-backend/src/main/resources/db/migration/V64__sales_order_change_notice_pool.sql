SET search_path TO "${flyway:defaultSchema}";

DROP TABLE IF EXISTS sales_order_change_record_item;
DROP TABLE IF EXISTS sales_order_change_record;

CREATE TABLE sales_order_change_notice (
    id BIGSERIAL PRIMARY KEY,
    notice_id VARCHAR(100) NOT NULL,
    sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id),
    sales_order_item_id BIGINT NOT NULL REFERENCES sales_order_item(id),
    allocation_id BIGINT,
    target_type VARCHAR(30) NOT NULL,
    target_key VARCHAR(255) NOT NULL,
    target_external_id VARCHAR(255),
    source_biz_id VARCHAR(255),
    change_type VARCHAR(30) NOT NULL,
    reason VARCHAR(500),
    changed_fields_json TEXT,
    payload_snapshot_json TEXT NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    auto_dispatch_epoch BIGINT NOT NULL DEFAULT 0,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    last_attempt_at TIMESTAMP,
    sent_at TIMESTAMP,
    last_error VARCHAR(2000),
    source_event_key VARCHAR(150) NOT NULL,
    created_by BIGINT,
    created_by_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sales_order_change_notice_target_type
        CHECK (target_type IN ('PRODUCTION', 'STOCK_PICKING')),
    CONSTRAINT ck_sales_order_change_notice_change_type
        CHECK (change_type IN ('MODIFY', 'DISCARD', 'RESTORE')),
    CONSTRAINT ck_sales_order_change_notice_status
        CHECK (status IN ('PENDING', 'SENDING', 'SENT', 'FAILED', 'RESULT_UNKNOWN')),
    CONSTRAINT ck_sales_order_change_notice_reason
        CHECK (change_type = 'MODIFY' OR NULLIF(BTRIM(reason), '') IS NOT NULL),
    CONSTRAINT ck_sales_order_change_notice_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE UNIQUE INDEX uk_sales_order_change_notice_notice_id
    ON sales_order_change_notice(notice_id);

CREATE INDEX idx_sales_order_change_notice_status_time
    ON sales_order_change_notice(status, created_at DESC, id DESC);

CREATE INDEX idx_sales_order_change_notice_order_item_target
    ON sales_order_change_notice(sales_order_id, sales_order_item_id, target_type, target_key, created_at DESC);

CREATE INDEX idx_sales_order_change_notice_item_target
    ON sales_order_change_notice(sales_order_item_id, target_type, target_key, change_type, status);

CREATE INDEX idx_sales_order_change_notice_event
    ON sales_order_change_notice(source_event_key, sales_order_item_id, target_type, target_key, change_type);

CREATE UNIQUE INDEX uk_sales_order_change_notice_pending_modify
    ON sales_order_change_notice(sales_order_item_id, target_type, target_key)
    WHERE change_type = 'MODIFY' AND status = 'PENDING';

CREATE TABLE sales_order_change_dispatch_setting (
    id BIGINT PRIMARY KEY,
    dispatch_mode VARCHAR(20) NOT NULL DEFAULT 'MANUAL',
    auto_dispatch_epoch BIGINT NOT NULL DEFAULT 0,
    auto_enabled_at TIMESTAMP,
    updated_by BIGINT,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sales_order_change_dispatch_mode
        CHECK (dispatch_mode IN ('MANUAL', 'AUTO')),
    CONSTRAINT ck_sales_order_change_dispatch_id CHECK (id = 1)
);

INSERT INTO sales_order_change_dispatch_setting (id, dispatch_mode, auto_dispatch_epoch)
VALUES (1, 'MANUAL', 0)
ON CONFLICT (id) DO NOTHING;

INSERT INTO system_data_center_endpoint_config (
    endpoint_code,
    display_name,
    target_system_code,
    method,
    path,
    enabled,
    timeout_ms,
    request_adapter,
    response_adapter,
    request_schema_json,
    response_schema_json,
    remark,
    created_at,
    updated_at
) VALUES (
    'MES_SALES_ORDER_CHANGE_NOTICE',
    'MES销售变更通知',
    'MES',
    'POST',
    '/api/v1/mes/openapi/sales-order/change-notice',
    true,
    60000,
    'MES_SALES_ORDER_CHANGE_NOTICE_V1',
    'MES_SALES_ORDER_CHANGE_NOTICE_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center forwards QPO sales-order change notices to MES.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (endpoint_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    target_system_code = EXCLUDED.target_system_code,
    method = EXCLUDED.method,
    path = EXCLUDED.path,
    enabled = true,
    timeout_ms = GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 60000),
    request_adapter = EXCLUDED.request_adapter,
    response_adapter = EXCLUDED.response_adapter,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_permission (
    id, parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled,
    created_at, updated_at, assignable
) VALUES
    (233, 0, 'MENU', 'change:sales-change:view', '销售变更', '/sales-changes', 'SalesOrderChangeNoticeManage', NULL, 702, true, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (234, 233, 'API', 'change:sales-change:customer-select', '销售变更选择客户', NULL, NULL, NULL, 703, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (235, 233, 'BUTTON', 'change:sales-change:send', '发送销售变更通知', NULL, NULL, NULL, 704, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (236, 233, 'BUTTON', 'change:sales-change:mode-update', '修改销售变更发送模式', NULL, NULL, NULL, 705, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_code = EXCLUDED.permission_code,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE permission.permission_code IN ('change:sales-change:view', 'change:sales-change:customer-select')
  AND role.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE permission.permission_code IN ('change:sales-change:send', 'change:sales-change:mode-update')
  AND role.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 236), TRUE);
