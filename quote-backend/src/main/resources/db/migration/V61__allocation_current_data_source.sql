-- New allocations use the linked sales-order item as the current quantity source.
-- Keep the legacy column for historical rows and compatibility reads, but allow
-- new relationship/status rows to omit the redundant quantity snapshot.
ALTER TABLE order_delivery_ticket_allocation
    ALTER COLUMN qty DROP NOT NULL;

COMMENT ON COLUMN order_delivery_ticket_allocation.qty IS
    'Legacy quantity snapshot; new linked allocations read quantity from sales_order_item';

COMMENT ON COLUMN order_delivery_ticket_allocation.ticket_qty IS
    'Legacy delivery-ticket quantity snapshot; new linked allocations do not write it';
