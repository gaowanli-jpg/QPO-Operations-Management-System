-- Clean up indexes that duplicate existing unique or single-column indexes.
DROP INDEX IF EXISTS idx_order_process_sheet_rfq_v16;
DROP INDEX IF EXISTS idx_order_rfq_rfq_no;
DROP INDEX IF EXISTS idx_inventory_pending_inventory_ref;

-- Match RFQ picker and order management filters: customer + status + newest first.
CREATE INDEX IF NOT EXISTS idx_order_rfq_customer_status_id
    ON order_rfq(customer_name, status, id DESC);

-- Match per-RFQ detail/export queries that filter by RFQ and sort by row id.
CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_id
    ON item_quote_version(rfq_id, id);
CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_id
    ON order_delivery_ticket(rfq_id, id);
CREATE INDEX IF NOT EXISTS idx_order_process_sheet_rfq_id
    ON order_process_sheet(rfq_id, id);

-- Match duplicate generation checks for delivery tickets and process sheets.
CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_item
    ON order_delivery_ticket(rfq_id, item_id);
CREATE INDEX IF NOT EXISTS idx_order_process_sheet_rfq_item
    ON order_process_sheet(rfq_id, item_id);

-- Single-column RFQ indexes are now covered by the composite RFQ indexes above.
DROP INDEX IF EXISTS idx_item_quote_version_rfq;
DROP INDEX IF EXISTS idx_order_delivery_ticket_rfq;
DROP INDEX IF EXISTS idx_order_process_sheet_rfq;
