ALTER TABLE order_rfq
    ADD COLUMN IF NOT EXISTS order_type VARCHAR(20);

UPDATE order_rfq
SET order_type = '正式订单'
WHERE order_type IS NULL OR TRIM(order_type) = '';

UPDATE item_quote_version item
SET order_type = COALESCE(NULLIF(TRIM(order_rfq.order_type), ''), '正式订单')
FROM order_rfq
WHERE item.rfq_id = order_rfq.id
  AND (item.order_type IS NULL OR TRIM(item.order_type) = '');

UPDATE order_delivery_ticket ticket
SET order_type = COALESCE(
    (SELECT NULLIF(TRIM(item.order_type), '')
     FROM item_quote_version item
     WHERE item.id = ticket.item_id),
    (SELECT NULLIF(TRIM(order_rfq.order_type), '')
     FROM order_rfq
     WHERE order_rfq.id = ticket.rfq_id),
    '正式订单'
)
WHERE ticket.order_type IS NULL OR TRIM(ticket.order_type) = '';
