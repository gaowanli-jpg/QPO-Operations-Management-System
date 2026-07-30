ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) NOT NULL DEFAULT 'RFQ',
    ADD COLUMN IF NOT EXISTS source_order_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_item_id BIGINT,
    ADD COLUMN IF NOT EXISTS drawing_url VARCHAR(500);

UPDATE order_delivery_ticket
SET source_type = 'RFQ',
    source_order_id = rfq_id,
    source_item_id = item_id
WHERE COALESCE(source_type, '') = ''
   OR source_order_id IS NULL
   OR source_item_id IS NULL;

UPDATE order_delivery_ticket ticket
SET drawing_url = item.drawing_url
FROM item_quote_version item
WHERE ticket.item_id = item.id
  AND ticket.drawing_url IS NULL;

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_source
    ON order_delivery_ticket(source_type, source_order_id, source_item_id);

ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS source_order_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_item_id BIGINT;

UPDATE order_delivery_ticket_allocation alloc
SET source_type = COALESCE(ticket.source_type, 'RFQ'),
    source_order_id = COALESCE(ticket.source_order_id, alloc.rfq_id),
    source_item_id = COALESCE(ticket.source_item_id, alloc.item_id)
FROM order_delivery_ticket ticket
WHERE alloc.ticket_id = ticket.id
  AND (alloc.source_type IS NULL
       OR alloc.source_order_id IS NULL
       OR alloc.source_item_id IS NULL);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_source
    ON order_delivery_ticket_allocation(source_type, source_order_id, source_item_id);
