ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) DEFAULT '普通件';

UPDATE order_delivery_ticket_allocation alloc
SET item_type = COALESCE(NULLIF(TRIM(alloc.item_type), ''), ticket.item_type, '普通件')
FROM order_delivery_ticket ticket
WHERE alloc.ticket_id = ticket.id
  AND (
      alloc.item_type IS NULL
      OR TRIM(alloc.item_type) = ''
  );

UPDATE order_delivery_ticket_allocation
SET item_type = '普通件'
WHERE item_type IS NULL OR TRIM(item_type) = '';

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_item_type
    ON order_delivery_ticket_allocation(item_type, id);
