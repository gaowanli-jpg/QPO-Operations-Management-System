CREATE TABLE IF NOT EXISTS sales_order_header (
    id BIGSERIAL PRIMARY KEY,
    sales_order_no VARCHAR(100) NOT NULL,
    document_no VARCHAR(100),
    document_type_code VARCHAR(50) NOT NULL DEFAULT 'SO01',
    customer_id BIGINT,
    customer_code VARCHAR(200),
    customer_short_name VARCHAR(200),
    customer_name VARCHAR(200),
    sales_order_date DATE,
    customer_order_no VARCHAR(120),
    customer_project_no VARCHAR(120),
    customer_order_title VARCHAR(200),
    order_type VARCHAR(50),
    internal_project_no VARCHAR(120),
    status VARCHAR(30) NOT NULL DEFAULT '已报价',
    u9_sync_status VARCHAR(30),
    u9_order_id VARCHAR(100),
    u9_order_code VARCHAR(100),
    import_batch_id BIGINT,
    import_record_id BIGINT,
    total_item_count INTEGER DEFAULT 0,
    matched_item_count INTEGER DEFAULT 0,
    unmatched_item_count INTEGER DEFAULT 0,
    warning_message TEXT,
    created_by BIGINT,
    owner_user_id INTEGER,
    owner_dept_id BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales_order_item (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id) ON DELETE CASCADE,
    line_no INTEGER,
    part_no VARCHAR(100) NOT NULL,
    description TEXT,
    qty INTEGER NOT NULL,
    version VARCHAR(50),
    order_type VARCHAR(50),
    customer_order_no VARCHAR(120),
    customer_project_no VARCHAR(120),
    internal_project_no VARCHAR(120),
    sales_material_no VARCHAR(120),
    material_no VARCHAR(120),
    customer_delivery_date DATE,
    internal_delivery_date DATE,
    material_name VARCHAR(255),
    engineering_material_name VARCHAR(255),
    surface_treatment VARCHAR(255),
    tax_included_unit_price NUMERIC(14, 4),
    untaxed_unit_price NUMERIC(14, 4),
    tax_included_total_price NUMERIC(14, 2),
    untaxed_total_price NUMERIC(14, 2),
    final_unit_price NUMERIC(14, 4),
    final_total_price NUMERIC(14, 2),
    shipped_qty INTEGER DEFAULT 0,
    unshipped_qty INTEGER DEFAULT 0,
    released_qty INTEGER DEFAULT 0,
    remaining_qty INTEGER DEFAULT 0,
    actual_ship_date DATE,
    drawing_url VARCHAR(500),
    has_drawing BOOLEAN DEFAULT FALSE,
    material_gross_weight NUMERIC(14, 4),
    part_net_weight NUMERIC(14, 4),
    quote_mode VARCHAR(20),
    assembly_fee NUMERIC(12, 2),
    base_cost NUMERIC(12, 2),
    item_type VARCHAR(20),
    material_data JSONB DEFAULT '{}'::jsonb,
    process_route JSONB DEFAULT '[]'::jsonb,
    fee_data JSONB DEFAULT '[]'::jsonb,
    matched_rfq_id INTEGER,
    matched_item_id INTEGER,
    match_status VARCHAR(30),
    match_message TEXT,
    remark TEXT,
    status VARCHAR(30) NOT NULL DEFAULT '已报价',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_order_item_qty CHECK (qty > 0)
);

ALTER TABLE sales_order_import_batch ADD COLUMN IF NOT EXISTS import_mode VARCHAR(30) DEFAULT 'U9';

ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS sales_order_id BIGINT;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS sales_order_no VARCHAR(100);
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS matched_item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS unmatched_item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS warning_message TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_header_no
    ON sales_order_header(sales_order_no);

CREATE INDEX IF NOT EXISTS idx_sales_order_header_status_created
    ON sales_order_header(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sales_order_header_customer
    ON sales_order_header(customer_name, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_header_customer_order_no
    ON sales_order_header(customer_order_no);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_order_id_id
    ON sales_order_item(sales_order_id, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_status_id
    ON sales_order_item(status, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_part_version_id
    ON sales_order_item(part_no, version, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_material_no_id
    ON sales_order_item(material_no, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_import_record_sales_order_id
    ON sales_order_import_record(sales_order_id);

COMMENT ON TABLE sales_order_header IS 'QPO independent sales order header; sales orders must not be written into order_rfq.';
COMMENT ON TABLE sales_order_item IS 'QPO independent sales order item; sales order items must not be written into item_quote_version.';
COMMENT ON COLUMN order_rfq.source_type IS 'DEPRECATED: sales order isolation uses sales_order_header; do not read or write this field for sales order imports.';
COMMENT ON COLUMN order_rfq.customer_code IS 'DEPRECATED for sales order imports: use sales_order_header.customer_code.';
COMMENT ON COLUMN order_rfq.customer_short_name IS 'DEPRECATED for sales order imports: use sales_order_header.customer_short_name.';
COMMENT ON COLUMN order_rfq.sales_document_type_code IS 'DEPRECATED: use sales_order_header.document_type_code.';
COMMENT ON COLUMN order_rfq.sales_order_date IS 'DEPRECATED: use sales_order_header.sales_order_date.';
COMMENT ON COLUMN order_rfq.customer_order_no IS 'DEPRECATED for sales order imports: use sales_order_header.customer_order_no.';
COMMENT ON COLUMN order_rfq.customer_project_no IS 'DEPRECATED for sales order imports: use sales_order_header.customer_project_no.';
COMMENT ON COLUMN order_rfq.customer_order_title IS 'DEPRECATED for sales order imports: use sales_order_header.customer_order_title.';
COMMENT ON COLUMN order_rfq.internal_project_no IS 'DEPRECATED for sales order imports: use sales_order_header.internal_project_no.';
COMMENT ON COLUMN order_rfq.sales_order_import_batch_id IS 'DEPRECATED: use sales_order_header.import_batch_id.';
COMMENT ON COLUMN order_rfq.sales_order_import_record_id IS 'DEPRECATED: use sales_order_header.import_record_id.';
COMMENT ON COLUMN item_quote_version.sales_order_line_no IS 'DEPRECATED: use sales_order_item.line_no.';
COMMENT ON COLUMN item_quote_version.customer_order_no IS 'DEPRECATED for sales order imports: use sales_order_item.customer_order_no.';
COMMENT ON COLUMN item_quote_version.customer_project_no IS 'DEPRECATED for sales order imports: use sales_order_item.customer_project_no.';
COMMENT ON COLUMN item_quote_version.internal_project_no IS 'DEPRECATED for sales order imports: use sales_order_item.internal_project_no.';
COMMENT ON COLUMN item_quote_version.sales_material_no IS 'DEPRECATED: use sales_order_item.sales_material_no.';
COMMENT ON COLUMN item_quote_version.customer_delivery_date IS 'DEPRECATED: use sales_order_item.customer_delivery_date.';
COMMENT ON COLUMN item_quote_version.internal_delivery_date IS 'DEPRECATED: use sales_order_item.internal_delivery_date.';
COMMENT ON COLUMN item_quote_version.shipped_qty IS 'DEPRECATED: use sales_order_item.shipped_qty.';
COMMENT ON COLUMN item_quote_version.unshipped_qty IS 'DEPRECATED: use sales_order_item.unshipped_qty.';
COMMENT ON COLUMN item_quote_version.actual_ship_date IS 'DEPRECATED: use sales_order_item.actual_ship_date.';
COMMENT ON COLUMN item_quote_version.sales_order_import_record_id IS 'DEPRECATED: use sales_order_import_record.sales_order_id and sales_order_item.';
