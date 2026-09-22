ALTER TABLE sales_order_header
    ADD COLUMN IF NOT EXISTS linked_discarded boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS linked_discarded_at timestamp,
    ADD COLUMN IF NOT EXISTS linked_discarded_by bigint,
    ADD COLUMN IF NOT EXISTS linked_discard_reason character varying(500),
    ADD COLUMN IF NOT EXISTS linked_restored_at timestamp,
    ADD COLUMN IF NOT EXISTS linked_restored_by bigint,
    ADD COLUMN IF NOT EXISTS linked_restore_reason character varying(500);

CREATE INDEX IF NOT EXISTS idx_sales_order_header_linked_discard
    ON sales_order_header(linked_discarded, status, updated_at DESC, id);
