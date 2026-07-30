ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) NOT NULL DEFAULT 'RFQ';
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS customer_code VARCHAR(200);
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS customer_short_name VARCHAR(200);
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS sales_document_type_code VARCHAR(50);
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS sales_order_date DATE;
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(120);
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS customer_project_no VARCHAR(120);
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(200);
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS internal_project_no VARCHAR(120);
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS sales_order_import_batch_id BIGINT;
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS sales_order_import_record_id BIGINT;

ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS sales_order_line_no INTEGER;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(120);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_project_no VARCHAR(120);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS internal_project_no VARCHAR(120);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS sales_material_no VARCHAR(120);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_delivery_date DATE;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS internal_delivery_date DATE;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS tax_included_unit_price NUMERIC(12, 2);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS untaxed_unit_price NUMERIC(12, 2);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS tax_included_total_price NUMERIC(12, 2);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS untaxed_total_price NUMERIC(12, 2);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS shipped_qty INTEGER DEFAULT 0;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS unshipped_qty INTEGER DEFAULT 0;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS actual_ship_date DATE;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS sales_order_import_record_id BIGINT;

ALTER TABLE sales_order_import_batch ADD COLUMN IF NOT EXISTS import_mode VARCHAR(20) DEFAULT 'U9';

ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS rfq_id INTEGER;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS rfq_no VARCHAR(100);
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS qpo_status VARCHAR(30);
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS matched_item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS unmatched_item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS warning_message TEXT;

CREATE INDEX IF NOT EXISTS idx_order_rfq_source_status_created
    ON order_rfq(source_type, status, created_at);

CREATE INDEX IF NOT EXISTS idx_order_rfq_customer_order_no
    ON order_rfq(customer_order_no);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_sales_order_record
    ON item_quote_version(sales_order_import_record_id);
