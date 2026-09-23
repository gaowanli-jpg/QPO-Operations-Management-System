ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS linked_discarded boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS linked_discarded_at timestamp,
    ADD COLUMN IF NOT EXISTS linked_discarded_by bigint,
    ADD COLUMN IF NOT EXISTS linked_discard_reason character varying(500),
    ADD COLUMN IF NOT EXISTS linked_u9_change_excluded boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS linked_restored_at timestamp,
    ADD COLUMN IF NOT EXISTS linked_restored_by bigint,
    ADD COLUMN IF NOT EXISTS linked_restore_reason character varying(500);

ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS discard_source character varying(50),
    ADD COLUMN IF NOT EXISTS discard_source_sales_order_item_id bigint;

ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS discard_source character varying(50),
    ADD COLUMN IF NOT EXISTS discard_source_sales_order_item_id bigint;

CREATE INDEX IF NOT EXISTS idx_sales_order_item_linked_discard
    ON sales_order_item(sales_order_id, linked_discarded, line_no, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_discard_source
    ON order_delivery_ticket(discard_source, discard_source_sales_order_item_id, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_discard_source
    ON order_delivery_ticket_allocation(discard_source, discard_source_sales_order_item_id, id);
