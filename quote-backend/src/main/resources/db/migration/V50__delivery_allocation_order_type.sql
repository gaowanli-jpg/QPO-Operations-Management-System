ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(20);

UPDATE order_delivery_ticket_allocation alloc
SET order_type = COALESCE(NULLIF(TRIM(alloc.order_type), ''), ticket.order_type, rfq.order_type)
FROM order_delivery_ticket ticket
LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
WHERE alloc.ticket_id = ticket.id
  AND (alloc.order_type IS NULL OR TRIM(alloc.order_type) = '');
