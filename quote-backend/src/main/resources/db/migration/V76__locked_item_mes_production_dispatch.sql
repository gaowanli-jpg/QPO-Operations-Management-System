ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS ticket_qty INTEGER,
    ADD COLUMN IF NOT EXISTS mes_production_status VARCHAR(30) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS mes_production_request_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS mes_production_external_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS mes_production_synced_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS mes_production_message TEXT;

UPDATE order_delivery_ticket_allocation alloc
SET ticket_qty = ticket.qty
FROM order_delivery_ticket ticket
WHERE alloc.ticket_id = ticket.id
  AND alloc.ticket_qty IS NULL;

UPDATE order_delivery_ticket_allocation
SET mes_production_status = COALESCE(NULLIF(TRIM(mes_production_status), ''), 'PENDING');

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_mes_production_status
    ON order_delivery_ticket_allocation(mes_production_status, allocation_type, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_allocation_mes_production_request
    ON order_delivery_ticket_allocation(mes_production_request_id)
    WHERE mes_production_request_id IS NOT NULL AND TRIM(mes_production_request_id) <> '';

WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        ('market:locked-item:view', 'BUTTON', 'market:locked-item:mes-production-submit', '锁库零件下发MES生产', NULL, NULL, NULL, 129, FALSE, TRUE)
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
JOIN system_permission parent ON parent.permission_code = permission_rows.parent_code
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
JOIN system_permission p ON p.permission_code = 'market:locked-item:mes-production-submit'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM system_role_permission srp
USING system_role r, system_permission p
WHERE srp.role_id = r.id
  AND srp.permission_id = p.id
  AND r.role_code = 'READONLY'
  AND p.permission_code = 'market:locked-item:mes-production-submit';
