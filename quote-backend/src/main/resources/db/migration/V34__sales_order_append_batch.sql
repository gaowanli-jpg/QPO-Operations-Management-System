-- Idempotency table for appending RFQ release lines to an existing unsynced sales order.
-- Stores compact identifiers only; no full request body, price details, drawings, or external credentials.

CREATE TABLE IF NOT EXISTS sales_order_append_batch (
    id BIGSERIAL PRIMARY KEY,
    append_batch_key VARCHAR(100) NOT NULL,
    target_sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id) ON DELETE CASCADE,
    request_hash VARCHAR(64) NOT NULL,
    source_rfq_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    ticket_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    sales_order_item_ids JSONB NOT NULL DEFAULT '[]'::jsonb,
    item_count INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_append_batch_key
    ON sales_order_append_batch(append_batch_key);

CREATE INDEX IF NOT EXISTS idx_sales_order_append_batch_target
    ON sales_order_append_batch(target_sales_order_id, created_at DESC);
