DROP INDEX IF EXISTS idx_order_delivery_ticket_rfq_id;
DROP INDEX IF EXISTS idx_order_delivery_ticket_rfq_item;
DROP INDEX IF EXISTS idx_order_delivery_ticket_rfq_item_id;

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_id_id
    ON order_delivery_ticket(rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_item_id
    ON order_delivery_ticket(rfq_id, item_id);
