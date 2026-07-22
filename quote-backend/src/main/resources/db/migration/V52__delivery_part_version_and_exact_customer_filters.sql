CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_part_version
    ON order_delivery_ticket(part_no, version, id);
