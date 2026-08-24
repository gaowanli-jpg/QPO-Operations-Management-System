-- Sales order local change audit trail.
-- These tables store only QPO-local correction metadata and compact field snapshots.

CREATE TABLE IF NOT EXISTS sales_order_change_record (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    sales_order_no VARCHAR(100) NOT NULL,
    change_type VARCHAR(40) NOT NULL,
    reason VARCHAR(500) NOT NULL,
    updated_count INTEGER NOT NULL DEFAULT 0,
    changed_fields_json TEXT,
    created_by BIGINT,
    created_by_name VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_order_change_record_order
    ON sales_order_change_record(sales_order_id, created_at DESC);

CREATE TABLE IF NOT EXISTS sales_order_change_record_item (
    id BIGSERIAL PRIMARY KEY,
    change_record_id BIGINT NOT NULL,
    sales_order_item_id BIGINT NOT NULL,
    line_no INTEGER,
    part_no VARCHAR(200),
    version VARCHAR(80),
    changed_fields_json TEXT,
    before_snapshot_json TEXT,
    after_snapshot_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_order_change_record_item_record
    ON sales_order_change_record_item(change_record_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_change_record_item_item
    ON sales_order_change_record_item(sales_order_item_id);
