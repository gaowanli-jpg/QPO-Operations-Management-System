ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS process_route JSONB,
    ADD COLUMN IF NOT EXISTS material_data JSONB,
    ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4),
    ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_item_id
    ON order_delivery_ticket(rfq_id, item_id, id);
