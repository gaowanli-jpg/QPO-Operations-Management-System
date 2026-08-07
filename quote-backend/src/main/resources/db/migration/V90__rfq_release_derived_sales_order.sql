ALTER TABLE item_quote_version
    ADD COLUMN IF NOT EXISTS sales_order_date DATE,
    ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255);

COMMENT ON COLUMN item_quote_version.sales_order_date IS 'RFQ item-level sales order date used by release.';
COMMENT ON COLUMN item_quote_version.customer_order_no IS 'RFQ item-level customer order number used by release.';
COMMENT ON COLUMN item_quote_version.customer_project_no IS 'RFQ item-level customer project number used by release.';
COMMENT ON COLUMN item_quote_version.customer_order_title IS 'RFQ item-level customer order title used by release.';
COMMENT ON COLUMN item_quote_version.order_type IS 'RFQ item-level order type used by release.';
COMMENT ON COLUMN item_quote_version.internal_project_no IS 'RFQ item-level internal project number used by release.';

ALTER TABLE sales_order_header
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS source_release_batch_key VARCHAR(100),
    ADD COLUMN IF NOT EXISTS source_release_request_hash VARCHAR(64);

UPDATE sales_order_header
SET source_type = 'DIRECT_IMPORT'
WHERE source_type IS NULL OR BTRIM(source_type) = '';

ALTER TABLE sales_order_header
    ALTER COLUMN source_type SET DEFAULT 'DIRECT_IMPORT',
    ALTER COLUMN source_type SET NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_sales_order_header_release_source'
          AND conrelid = 'sales_order_header'::regclass
    ) THEN
        ALTER TABLE sales_order_header
            ADD CONSTRAINT chk_sales_order_header_release_source
            CHECK (
                source_type <> 'RFQ_RELEASE'
                OR (
                    NULLIF(BTRIM(source_release_batch_key), '') IS NOT NULL
                    AND NULLIF(BTRIM(source_release_request_hash), '') IS NOT NULL
                )
            );
    END IF;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_header_release_batch
    ON sales_order_header(source_release_batch_key)
    WHERE source_release_batch_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sales_order_header_source_type_created
    ON sales_order_header(source_type, created_at DESC);

CREATE TABLE IF NOT EXISTS sales_order_source_rfq (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id) ON DELETE CASCADE,
    rfq_id INTEGER NOT NULL REFERENCES order_rfq(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_order_source_rfq UNIQUE (sales_order_id, rfq_id)
);

CREATE INDEX IF NOT EXISTS idx_sales_order_source_rfq_rfq
    ON sales_order_source_rfq(rfq_id, sales_order_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_source_rfq_sales_order
    ON sales_order_source_rfq(sales_order_id, rfq_id);

ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS source_type VARCHAR(30),
    ADD COLUMN IF NOT EXISTS source_order_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_item_id BIGINT,
    ADD COLUMN IF NOT EXISTS source_ticket_id INTEGER;

UPDATE sales_order_item
SET source_type = 'DIRECT_IMPORT'
WHERE source_type IS NULL OR BTRIM(source_type) = '';

ALTER TABLE sales_order_item
    ALTER COLUMN source_type SET DEFAULT 'DIRECT_IMPORT',
    ALTER COLUMN source_type SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sales_order_item_source_order_item
    ON sales_order_item(source_type, source_order_id, source_item_id, id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_item_source_ticket
    ON sales_order_item(source_ticket_id)
    WHERE source_ticket_id IS NOT NULL;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'fk_sales_order_item_source_ticket'
          AND conrelid = 'sales_order_item'::regclass
    ) THEN
        ALTER TABLE sales_order_item
            ADD CONSTRAINT fk_sales_order_item_source_ticket
            FOREIGN KEY (source_ticket_id) REFERENCES order_delivery_ticket(id);
    END IF;
END $$;

COMMENT ON TABLE sales_order_source_rfq IS 'RFQ sources included in one derived sales order.';
COMMENT ON COLUMN sales_order_header.source_type IS 'Sales order source: DIRECT_IMPORT or RFQ_RELEASE.';
COMMENT ON COLUMN sales_order_header.source_release_batch_key IS 'Idempotency key of one RFQ release submission.';
COMMENT ON COLUMN sales_order_header.source_release_request_hash IS 'SHA-256 hash of the normalized RFQ release request.';
COMMENT ON COLUMN sales_order_item.source_type IS 'Sales order item source: DIRECT_IMPORT or RFQ.';
COMMENT ON COLUMN sales_order_item.source_order_id IS 'Source RFQ order id when source_type is RFQ.';
COMMENT ON COLUMN sales_order_item.source_item_id IS 'Source RFQ item id when source_type is RFQ.';
COMMENT ON COLUMN sales_order_item.source_ticket_id IS 'Unique RFQ delivery ticket linked to the derived sales order item.';
