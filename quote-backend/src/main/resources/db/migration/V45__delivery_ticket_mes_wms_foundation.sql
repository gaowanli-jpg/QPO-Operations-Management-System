ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS material_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS mes_sync_status VARCHAR(30) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS mes_sync_message TEXT,
    ADD COLUMN IF NOT EXISTS mes_synced_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS wms_allocate_status VARCHAR(30) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS wms_allocate_message TEXT,
    ADD COLUMN IF NOT EXISTS wms_allocated_at TIMESTAMP;

UPDATE order_delivery_ticket
SET mes_sync_status = COALESCE(NULLIF(TRIM(mes_sync_status), ''), 'PENDING'),
    wms_allocate_status = COALESCE(NULLIF(TRIM(wms_allocate_status), ''), 'PENDING');

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_material_no
    ON order_delivery_ticket(material_no, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_mes_status
    ON order_delivery_ticket(mes_sync_status, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_wms_status
    ON order_delivery_ticket(wms_allocate_status, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_internal_project
    ON order_delivery_ticket(internal_project_no, id);

CREATE TABLE IF NOT EXISTS order_delivery_ticket_allocation (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    rfq_id INTEGER REFERENCES order_rfq(id),
    item_id INTEGER REFERENCES item_quote_version(id),
    allocation_type VARCHAR(40) NOT NULL,
    qty INTEGER NOT NULL,
    material_no VARCHAR(100),
    part_no VARCHAR(100),
    version VARCHAR(20),
    customer_name VARCHAR(255),
    internal_project_no VARCHAR(100),
    wms_production_order_no VARCHAR(100),
    source_payload JSONB DEFAULT '{}'::jsonb,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_order_delivery_ticket_allocation_qty CHECK (qty > 0),
    CONSTRAINT chk_order_delivery_ticket_allocation_type CHECK (
        allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK', 'PRODUCTION_REQUIRED')
    )
);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_ticket
    ON order_delivery_ticket_allocation(ticket_id, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_rfq
    ON order_delivery_ticket_allocation(rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_material
    ON order_delivery_ticket_allocation(material_no, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_part_version
    ON order_delivery_ticket_allocation(part_no, version, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_type
    ON order_delivery_ticket_allocation(allocation_type, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_wms_order
    ON order_delivery_ticket_allocation(wms_production_order_no, id);

WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'market:delivered-item:view', '已提零件', '/delivered-items', 'DeliveredItemManage', 'Files', 126, TRUE, TRUE),
        ('market:delivered-item:view', 'API', 'market:delivered-item:file-preview', '已提零件预览图纸', NULL, NULL, NULL, 127, FALSE, TRUE)
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
    'market:delivered-item:view',
    'market:delivered-item:file-preview'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;
