WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'market:u9-sync:view', 'U9同步', '/u9-sync', 'SalesOrderU9Sync', 'Document', 146, TRUE, TRUE),
        ('market:u9-sync:view', 'API', 'market:u9-sync:customer-select', 'U9同步选择客户', NULL, NULL, NULL, 147, FALSE, TRUE),
        ('market:u9-sync:view', 'API', 'market:u9-sync:order-select', 'U9同步选择销售订单', NULL, NULL, NULL, 148, FALSE, TRUE),
        ('market:u9-sync:view', 'BUTTON', 'market:u9-sync:submit', '同步U9', NULL, NULL, NULL, 149, FALSE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    COALESCE(parent.id, 0),
    permission_rows.permission_type,
    permission_rows.permission_code,
    permission_rows.permission_name,
    permission_rows.route_path,
    permission_rows.component_key,
    permission_rows.icon,
    permission_rows.sort_order,
    permission_rows.visible,
    TRUE,
    permission_rows.assignable
FROM permission_rows
LEFT JOIN system_permission parent ON parent.permission_code = permission_rows.parent_code
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = TRUE,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

UPDATE system_permission
SET sort_order = CASE permission_code
    WHEN 'market:delivery-document:view' THEN 150
    WHEN 'market:delivery-document:customer-select' THEN 151
    WHEN 'market:delivery-document:order-select' THEN 152
    WHEN 'market:delivery-document:export' THEN 153
    WHEN 'market:template:view' THEN 160
    WHEN 'market:template:upload' THEN 161
    ELSE sort_order
END,
updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'market:delivery-document:view',
    'market:delivery-document:customer-select',
    'market:delivery-document:order-select',
    'market:delivery-document:export',
    'market:template:view',
    'market:template:upload'
);

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code IN (
    'market:u9-sync:view',
    'market:u9-sync:customer-select',
    'market:u9-sync:order-select'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = 'market:u9-sync:submit'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM system_role_permission srp
USING system_role r, system_permission p
WHERE srp.role_id = r.id
  AND srp.permission_id = p.id
  AND r.role_code = 'READONLY'
  AND p.permission_code = 'market:u9-sync:submit';

ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS material_no_status VARCHAR(30) NOT NULL DEFAULT '未确认',
    ADD COLUMN IF NOT EXISTS material_no_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS material_no_source_ticket_id BIGINT,
    ADD COLUMN IF NOT EXISTS material_no_synced_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS material_no_message TEXT;

UPDATE sales_order_item
SET material_no_status = '未确认'
WHERE material_no_status IS NULL OR TRIM(material_no_status) = '';

CREATE INDEX IF NOT EXISTS idx_sales_order_item_material_status
    ON sales_order_item(material_no_status, sales_order_id, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_material_source_ticket
    ON sales_order_item(material_no_source_ticket_id)
    WHERE material_no_source_ticket_id IS NOT NULL;

COMMENT ON COLUMN sales_order_item.material_no_status IS 'Material number confirmation status for sales order U9 sync: 未确认, 已确认, 获取失败. Imported material_no remains 未确认 until MES/main data confirmation.';
COMMENT ON COLUMN sales_order_item.material_no_source IS 'Material number confirmation source, for example MES_TICKET after delivery ticket material sync.';
COMMENT ON COLUMN sales_order_item.material_no_source_ticket_id IS 'Source order_delivery_ticket.id that confirmed and wrote back the sales order item material number.';
COMMENT ON COLUMN sales_order_item.material_no_synced_at IS 'Time when confirmed material number was written back to sales_order_item.';
COMMENT ON COLUMN sales_order_item.material_no_message IS 'Short material number confirmation or failure summary; do not store full external responses.';
