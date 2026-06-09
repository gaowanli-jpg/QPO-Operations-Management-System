CREATE TABLE IF NOT EXISTS aps_part_inventory_pending_stock (
    id BIGSERIAL PRIMARY KEY,
    part_inventory_id BIGINT NOT NULL REFERENCES aps_part_inventory(id) ON DELETE CASCADE,
    pending_ref_no VARCHAR(100) NOT NULL,
    pending_qty INTEGER NOT NULL DEFAULT 0,
    locked_qty INTEGER NOT NULL DEFAULT 0,
    available_qty INTEGER GENERATED ALWAYS AS (pending_qty - locked_qty) STORED,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_part_pending_inventory_ref UNIQUE (part_inventory_id, pending_ref_no),
    CONSTRAINT ck_aps_part_pending_qty_nonnegative CHECK (
        pending_qty >= 0
        AND locked_qty >= 0
        AND locked_qty <= pending_qty
    )
);

CREATE INDEX IF NOT EXISTS idx_aps_part_pending_inventory ON aps_part_inventory_pending_stock(part_inventory_id, id);

INSERT INTO aps_part_inventory_pending_stock (part_inventory_id, pending_ref_no, pending_qty, locked_qty)
SELECT id, '在产汇总', in_production_qty, locked_in_production_qty
FROM aps_part_inventory
WHERE in_production_qty > 0
  AND NOT EXISTS (
      SELECT 1
      FROM aps_part_inventory_pending_stock p
      WHERE p.part_inventory_id = aps_part_inventory.id
  );
