ALTER TABLE aps_part_inventory_lock_detail
    ADD COLUMN IF NOT EXISTS status VARCHAR(50) NOT NULL DEFAULT 'PENDING';

ALTER TABLE aps_part_inventory_lock_detail
    ADD COLUMN IF NOT EXISTS confirmed_at TIMESTAMP;

ALTER TABLE aps_part_inventory_lock_detail
    ADD COLUMN IF NOT EXISTS released_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_aps_part_lock_detail_status ON aps_part_inventory_lock_detail(status, id);
