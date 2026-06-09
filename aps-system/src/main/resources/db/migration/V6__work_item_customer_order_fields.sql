ALTER TABLE aps_work_item ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(100);
ALTER TABLE aps_work_item ADD COLUMN IF NOT EXISTS customer_project_no VARCHAR(100);

CREATE INDEX IF NOT EXISTS idx_aps_work_item_customer_order_no ON aps_work_item(customer_order_no);
