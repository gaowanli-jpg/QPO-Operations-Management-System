CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_id_id
    ON item_quote_version(rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_status_id
    ON item_quote_version(rfq_id, status, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_part_version_id
    ON item_quote_version(rfq_id, part_no, version, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_id_id
    ON order_delivery_ticket(rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_item_id
    ON order_delivery_ticket(rfq_id, item_id);
