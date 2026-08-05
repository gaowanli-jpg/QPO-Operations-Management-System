WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'market:production-item:view', '待产零件', '/production-items', 'ProductionItemManage', NULL, 127, TRUE, TRUE),
        ('market:production-item:view', 'API', 'market:production-item:customer-select', '待产零件选择客户', NULL, NULL, NULL, 128, FALSE, TRUE),
        ('market:production-item:view', 'BUTTON', 'market:production-item:mes-production-submit', '待产零件下发MES生产', NULL, NULL, NULL, 129, FALSE, TRUE)
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
    'market:production-item:view',
    'market:production-item:customer-select'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = 'market:production-item:mes-production-submit'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM system_role_permission srp
USING system_permission p
WHERE srp.permission_id = p.id
  AND p.permission_code = 'market:locked-item:mes-production-submit';

UPDATE system_permission
SET enabled = FALSE,
    assignable = FALSE,
    visible = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code = 'market:locked-item:mes-production-submit';

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_production_active_id
    ON order_delivery_ticket_allocation(id DESC)
    WHERE allocation_type = 'PRODUCTION_REQUIRED'
      AND (mes_production_status IS NULL OR TRIM(mes_production_status) = '' OR mes_production_status <> 'SUCCESS');

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_locked_stock_id
    ON order_delivery_ticket_allocation(allocation_type, id DESC)
    WHERE allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK');