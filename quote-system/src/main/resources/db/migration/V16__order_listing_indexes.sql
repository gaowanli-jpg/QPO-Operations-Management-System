CREATE INDEX IF NOT EXISTS idx_order_rfq_customer_id ON order_rfq(customer_name, id DESC);
CREATE INDEX IF NOT EXISTS idx_order_rfq_status_id ON order_rfq(status, id DESC);
CREATE INDEX IF NOT EXISTS idx_order_rfq_rfq_no ON order_rfq(rfq_no);
CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq ON item_quote_version(rfq_id);
CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq ON order_delivery_ticket(rfq_id);
CREATE INDEX IF NOT EXISTS idx_order_process_sheet_rfq_v16 ON order_process_sheet(rfq_id);
