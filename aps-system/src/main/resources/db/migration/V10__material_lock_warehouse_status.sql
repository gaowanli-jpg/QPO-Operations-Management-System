ALTER TABLE aps_material_lock
    ADD COLUMN IF NOT EXISTS warehouse_status VARCHAR(50) NOT NULL DEFAULT 'PENDING';

ALTER TABLE aps_material_lock
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_aps_material_lock_warehouse_status ON aps_material_lock(warehouse_status, id);
CREATE INDEX IF NOT EXISTS idx_aps_material_lock_customer_warehouse ON aps_material_lock(customer_name, warehouse_status, id);
