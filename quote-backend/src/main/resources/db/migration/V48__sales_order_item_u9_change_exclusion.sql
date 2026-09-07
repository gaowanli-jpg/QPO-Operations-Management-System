ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS u9_change_excluded boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS u9_change_excluded_at timestamp,
    ADD COLUMN IF NOT EXISTS u9_change_excluded_by bigint,
    ADD COLUMN IF NOT EXISTS u9_change_excluded_reason character varying(500),
    ADD COLUMN IF NOT EXISTS u9_change_restored_at timestamp,
    ADD COLUMN IF NOT EXISTS u9_change_restored_by bigint,
    ADD COLUMN IF NOT EXISTS u9_change_restore_reason character varying(500);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_u9_change_excluded_order
    ON sales_order_item(sales_order_id, u9_change_excluded, line_no, id);
