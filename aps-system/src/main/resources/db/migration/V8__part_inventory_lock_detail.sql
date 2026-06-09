CREATE TABLE IF NOT EXISTS aps_part_inventory_lock_detail (
    id BIGSERIAL PRIMARY KEY,
    part_inventory_lock_id BIGINT NOT NULL REFERENCES aps_part_inventory_lock(id) ON DELETE CASCADE,
    work_item_id BIGINT NOT NULL REFERENCES aps_work_item(id) ON DELETE CASCADE,
    part_inventory_id BIGINT REFERENCES aps_part_inventory(id) ON DELETE SET NULL,
    lock_type VARCHAR(50) NOT NULL,
    pending_stock_id BIGINT REFERENCES aps_part_inventory_pending_stock(id) ON DELETE SET NULL,
    pending_ref_no VARCHAR(100),
    locked_qty INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_part_inventory_lock_detail_type CHECK (lock_type IN ('IN_STOCK', 'IN_PRODUCTION')),
    CONSTRAINT ck_aps_part_inventory_lock_detail_qty_positive CHECK (locked_qty > 0)
);

CREATE INDEX IF NOT EXISTS idx_aps_part_lock_detail_lock ON aps_part_inventory_lock_detail(part_inventory_lock_id, id);
CREATE INDEX IF NOT EXISTS idx_aps_part_lock_detail_item ON aps_part_inventory_lock_detail(work_item_id, id);
CREATE INDEX IF NOT EXISTS idx_aps_part_lock_detail_pending ON aps_part_inventory_lock_detail(pending_stock_id);
CREATE INDEX IF NOT EXISTS idx_aps_part_lock_detail_type ON aps_part_inventory_lock_detail(lock_type, id);

INSERT INTO aps_part_inventory_lock_detail (
    part_inventory_lock_id,
    work_item_id,
    part_inventory_id,
    lock_type,
    pending_stock_id,
    pending_ref_no,
    locked_qty
)
SELECT l.id, l.work_item_id, l.part_inventory_id, 'IN_STOCK', NULL, NULL, l.locked_in_stock_qty
FROM aps_part_inventory_lock l
WHERE l.locked_in_stock_qty > 0
  AND NOT EXISTS (
      SELECT 1
      FROM aps_part_inventory_lock_detail d
      WHERE d.part_inventory_lock_id = l.id
        AND d.lock_type = 'IN_STOCK'
  );

INSERT INTO aps_part_inventory_lock_detail (
    part_inventory_lock_id,
    work_item_id,
    part_inventory_id,
    lock_type,
    pending_stock_id,
    pending_ref_no,
    locked_qty
)
SELECT l.id, l.work_item_id, l.part_inventory_id, 'IN_PRODUCTION', NULL, NULL, l.locked_in_production_qty
FROM aps_part_inventory_lock l
WHERE l.locked_in_production_qty > 0
  AND NOT EXISTS (
      SELECT 1
      FROM aps_part_inventory_lock_detail d
      WHERE d.part_inventory_lock_id = l.id
        AND d.lock_type = 'IN_PRODUCTION'
  );
