ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS allocation_split_source VARCHAR(20),
    ADD COLUMN IF NOT EXISTS split_parent_allocation_id INTEGER;

ALTER TABLE order_delivery_ticket_allocation
    DROP CONSTRAINT IF EXISTS chk_delivery_allocation_split_source;

ALTER TABLE order_delivery_ticket_allocation
    ADD CONSTRAINT chk_delivery_allocation_split_source
        CHECK (allocation_split_source IS NULL OR allocation_split_source IN ('WMS', 'MANUAL'));

ALTER TABLE order_delivery_ticket_allocation
    DROP CONSTRAINT IF EXISTS fk_delivery_allocation_split_parent;

ALTER TABLE order_delivery_ticket_allocation
    ADD CONSTRAINT fk_delivery_allocation_split_parent
        FOREIGN KEY (split_parent_allocation_id) REFERENCES order_delivery_ticket_allocation(id);

UPDATE order_delivery_ticket_allocation
SET allocation_split_source = 'WMS'
WHERE allocation_split_source IS NULL
  AND (wms_reservation_ticket_id IS NOT NULL OR allocation_source = 'WMS_RESERVATION');

UPDATE order_delivery_ticket_allocation child
SET split_parent_allocation_id = reservation.allocation_id
FROM wms_reservation_ticket reservation
WHERE child.wms_reservation_ticket_id = reservation.id
  AND child.allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK')
  AND child.split_parent_allocation_id IS NULL
  AND reservation.allocation_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_split_source
    ON order_delivery_ticket_allocation(allocation_split_source, allocation_type, id DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_split_parent
    ON order_delivery_ticket_allocation(split_parent_allocation_id, allocation_type, id ASC)
    WHERE split_parent_allocation_id IS NOT NULL;

COMMENT ON COLUMN order_delivery_ticket_allocation.allocation_split_source IS
    'Initial split source: WMS or MANUAL; NULL means not initially split';

COMMENT ON COLUMN order_delivery_ticket_allocation.split_parent_allocation_id IS
    'Source PRODUCTION_REQUIRED allocation for a split lock row';
