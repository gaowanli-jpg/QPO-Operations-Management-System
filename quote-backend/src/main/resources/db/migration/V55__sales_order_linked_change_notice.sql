CREATE TABLE sales_order_linked_change_notice (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    sales_order_no VARCHAR(100),
    line_no INTEGER,
    customer_id BIGINT,
    customer_code VARCHAR(100),
    customer_name VARCHAR(255),
    part_no VARCHAR(255),
    version VARCHAR(100),
    description VARCHAR(500),
    material_name VARCHAR(500),
    trigger_scope VARCHAR(30) NOT NULL,
    linked_discard_event_key VARCHAR(100) NOT NULL,
    linked_discarded_at TIMESTAMP NOT NULL,
    linked_discarded_by BIGINT,
    linked_discard_reason VARCHAR(500),
    wms_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    wms_status VARCHAR(100) NOT NULL DEFAULT 'NOT_OCCURRED',
    wms_request_id TEXT,
    wms_action_at TIMESTAMP,
    mes_production_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    mes_production_status VARCHAR(200) NOT NULL DEFAULT 'NOT_OCCURRED',
    mes_production_request_id TEXT,
    mes_production_action_at TIMESTAMP,
    stock_picking_submitted BOOLEAN NOT NULL DEFAULT FALSE,
    stock_picking_status VARCHAR(200) NOT NULL DEFAULT 'NOT_OCCURRED',
    stock_picking_biz_id TEXT,
    stock_picking_action_at TIMESTAMP,
    downstream_impact_count INTEGER NOT NULL DEFAULT 0,
    handling_status VARCHAR(30) NOT NULL DEFAULT 'UNHANDLED',
    handled_at TIMESTAMP,
    handled_by BIGINT,
    handle_remark VARCHAR(500),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_sales_order_linked_change_trigger_scope CHECK (trigger_scope IN ('ITEM', 'ORDER')),
    CONSTRAINT ck_sales_order_linked_change_handling_status CHECK (handling_status IN ('UNHANDLED', 'HANDLED'))
);

CREATE UNIQUE INDEX uk_sales_order_linked_change_event_item
    ON sales_order_linked_change_notice(linked_discard_event_key, sales_order_item_id);
CREATE INDEX idx_linked_change_order_time
    ON sales_order_linked_change_notice(sales_order_id, linked_discarded_at DESC, id DESC);
CREATE INDEX idx_linked_change_customer_status_time
    ON sales_order_linked_change_notice(customer_id, handling_status, linked_discarded_at DESC);
CREATE INDEX idx_linked_change_status_impact_time
    ON sales_order_linked_change_notice(handling_status, downstream_impact_count, linked_discarded_at DESC);
CREATE INDEX idx_linked_change_item_event
    ON sales_order_linked_change_notice(sales_order_item_id, linked_discard_event_key);
CREATE INDEX idx_linked_change_order_part_version
    ON sales_order_linked_change_notice(sales_order_no, part_no, version);

WITH permission_rows AS (
    SELECT 226::BIGINT AS id, NULL::BIGINT AS parent_id, 'MENU'::VARCHAR AS permission_type,
           'change:linked-change:view'::VARCHAR AS permission_code, '关联变更'::VARCHAR AS permission_name,
           '/linked-changes'::VARCHAR AS route_path, 'LinkedChangeManage'::VARCHAR AS component_key,
           699 AS sort_order, TRUE AS visible, TRUE AS assignable
    UNION ALL
    SELECT 227, 226, 'API', 'change:linked-change:customer-select', '关联变更选择客户', NULL, NULL, 700, FALSE, TRUE
    UNION ALL
    SELECT 228, 226, 'BUTTON', 'change:linked-change:handle', '处理关联变更通知', NULL, NULL, 701, FALSE, TRUE
)
INSERT INTO system_permission (
    id, parent_id, permission_type, permission_code, permission_name, route_path, component_key,
    icon, sort_order, visible, enabled, created_at, updated_at, assignable
)
SELECT id, parent_id, permission_type, permission_code, permission_name, route_path, component_key,
       NULL, sort_order, visible, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, assignable
FROM permission_rows
ON CONFLICT (permission_code) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
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
WHERE permission.permission_code IN ('change:linked-change:view', 'change:linked-change:customer-select')
  AND role.role_code IN ('MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE permission.permission_code IN ('change:linked-change:view', 'change:linked-change:customer-select', 'change:linked-change:handle')
  AND role.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 228), TRUE);
