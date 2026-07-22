CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_no
    ON order_delivery_ticket(rfq_no, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_delivery_date
    ON order_delivery_ticket(delivery_date, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_rfq_no
    ON order_delivery_ticket_allocation(rfq_no, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_internal_project
    ON order_delivery_ticket_allocation(internal_project_no, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_delivery_date
    ON order_delivery_ticket_allocation(delivery_date, id);

WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        ('market:delivered-item:view', 'API', 'market:delivered-item:customer-select', '已提零件选择客户', NULL, NULL, NULL, 130, FALSE, TRUE),
        ('market:locked-item:view', 'API', 'market:locked-item:customer-select', '锁库零件选择客户', NULL, NULL, NULL, 128, FALSE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    parent.id,
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

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code IN (
    'market:delivered-item:customer-select',
    'market:locked-item:customer-select'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;
