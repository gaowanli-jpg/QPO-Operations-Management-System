ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_requirement TEXT;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS customer_requirement TEXT;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS customer_requirement TEXT;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS customer_requirement TEXT;

COMMENT ON COLUMN item_quote_version.customer_requirement IS 'Customer requirement maintained in release workspace.';
COMMENT ON COLUMN sales_order_item.customer_requirement IS 'Customer requirement maintained in release workspace.';
COMMENT ON COLUMN order_delivery_ticket.customer_requirement IS 'Snapshot of release item customer requirement.';
COMMENT ON COLUMN order_delivery_ticket_allocation.customer_requirement IS 'Snapshot of delivered ticket customer requirement for locked and production items.';
