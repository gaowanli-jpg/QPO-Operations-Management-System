ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS remark TEXT;
