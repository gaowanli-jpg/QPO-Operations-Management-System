DROP INDEX IF EXISTS idx_delivery_allocation_wms_order;

ALTER TABLE order_delivery_ticket_allocation
    DROP COLUMN IF EXISTS wms_production_order_no;
