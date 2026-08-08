CREATE TABLE IF NOT EXISTS order_rfq (
    id SERIAL PRIMARY KEY,
    rfq_no VARCHAR(100) NOT NULL UNIQUE,
    customer_name VARCHAR(200),
    project_name VARCHAR(255),
    order_type VARCHAR(20),
    source_type VARCHAR(30) NOT NULL DEFAULT 'RFQ',
    customer_code VARCHAR(200),
    customer_short_name VARCHAR(200),
    sales_document_type_code VARCHAR(50),
    sales_order_date DATE,
    customer_order_no VARCHAR(120),
    customer_project_no VARCHAR(120),
    customer_order_title VARCHAR(200),
    internal_project_no VARCHAR(120),
    sales_order_import_batch_id BIGINT,
    sales_order_import_record_id BIGINT,
    status VARCHAR(50),
    engineer_config JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS rfq_no_sequence (
    rfq_date DATE PRIMARY KEY,
    last_seq INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS engineer_config JSONB;
ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS order_type VARCHAR(20);
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
ALTER TABLE order_rfq
    ALTER COLUMN engineer_config TYPE JSONB USING NULLIF(engineer_config::TEXT, '')::JSONB;

CREATE TABLE IF NOT EXISTS item_quote_version (
    id SERIAL PRIMARY KEY,
    rfq_id INTEGER REFERENCES order_rfq(id),
    part_no VARCHAR(100) NOT NULL,
    description TEXT,
    qty INTEGER NOT NULL,
    version VARCHAR(20) DEFAULT 'V1',
    order_type VARCHAR(20),
    sales_order_date DATE,
    sales_order_line_no INTEGER,
    customer_order_no VARCHAR(120),
    customer_project_no VARCHAR(120),
    customer_order_title VARCHAR(255),
    internal_project_no VARCHAR(120),
    sales_material_no VARCHAR(120),
    customer_delivery_date DATE,
    internal_delivery_date DATE,
    material_name VARCHAR(255),
    material_shape VARCHAR(30),
    surface_treatment VARCHAR(255),
    unit VARCHAR(50),
    lead_time VARCHAR(100),
    quote_lead_time VARCHAR(50),
    internal_target_lead_time VARCHAR(50),
    remark TEXT,
    customer_requirement TEXT,
    material_gross_weight NUMERIC(14, 4),
    part_net_weight NUMERIC(14, 4) DEFAULT 0,
    material_data JSONB,
    process_route JSONB,
    fee_data JSONB,
    base_cost NUMERIC(12, 2),
    tax_included_unit_price NUMERIC(12, 2),
    untaxed_unit_price NUMERIC(12, 2),
    tax_included_total_price NUMERIC(12, 2),
    untaxed_total_price NUMERIC(12, 2),
    shipped_qty INTEGER DEFAULT 0,
    unshipped_qty INTEGER DEFAULT 0,
    sales_order_import_record_id BIGINT,
    status VARCHAR(50),
    drawing_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS fee_data JSONB;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS quote_lead_time VARCHAR(50);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS internal_target_lead_time VARCHAR(50);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4) DEFAULT 0;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS sales_order_line_no INTEGER;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS sales_order_date DATE;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(120);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_project_no VARCHAR(120);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255);
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
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS sales_order_import_record_id BIGINT;
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS customer_requirement TEXT;
ALTER TABLE item_quote_version
    ALTER COLUMN material_data TYPE JSONB USING NULLIF(material_data::TEXT, '')::JSONB,
    ALTER COLUMN process_route TYPE JSONB USING NULLIF(process_route::TEXT, '')::JSONB,
    ALTER COLUMN fee_data TYPE JSONB USING NULLIF(fee_data::TEXT, '')::JSONB;

CREATE INDEX IF NOT EXISTS idx_item_quote_version_repeat_released_key
    ON item_quote_version(part_no, version, id DESC)
    WHERE status = '已提单'
      AND tax_included_unit_price IS NOT NULL
      AND material_data IS NOT NULL
      AND process_route IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_id_id
    ON item_quote_version(rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_status_id
    ON item_quote_version(rfq_id, status, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_part_version_id
    ON item_quote_version(rfq_id, part_no, version, id);

CREATE INDEX IF NOT EXISTS idx_order_rfq_source_status_created
    ON order_rfq(source_type, status, created_at);

CREATE INDEX IF NOT EXISTS idx_order_rfq_customer_order_no
    ON order_rfq(customer_order_no);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_sales_order_record
    ON item_quote_version(sales_order_import_record_id);

CREATE TABLE IF NOT EXISTS sales_order_import_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(80) NOT NULL UNIQUE,
    source_file_name VARCHAR(255),
    import_mode VARCHAR(30) DEFAULT 'U9',
    status VARCHAR(30) NOT NULL,
    total_order_count INTEGER DEFAULT 0,
    success_order_count INTEGER DEFAULT 0,
    failed_order_count INTEGER DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales_order_import_record (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES sales_order_import_batch(id) ON DELETE CASCADE,
    other_id VARCHAR(100),
    customer_order_no VARCHAR(120),
    order_by VARCHAR(120),
    document_type VARCHAR(50),
    line_count INTEGER DEFAULT 0,
    request_summary_json JSONB,
    u9_res_code INTEGER,
    u9_success BOOLEAN,
    u9_id VARCHAR(120),
    u9_code VARCHAR(120),
    u9_error_msg TEXT,
    rfq_id INTEGER,
    rfq_no VARCHAR(100),
    qpo_status VARCHAR(30),
    matched_item_count INTEGER DEFAULT 0,
    unmatched_item_count INTEGER DEFAULT 0,
    warning_message TEXT,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE sales_order_import_batch ADD COLUMN IF NOT EXISTS import_mode VARCHAR(30) DEFAULT 'U9';
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS rfq_id INTEGER;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS rfq_no VARCHAR(100);
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS qpo_status VARCHAR(30);
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS matched_item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS unmatched_item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS warning_message TEXT;

CREATE INDEX IF NOT EXISTS idx_sales_order_import_record_batch_id
    ON sales_order_import_record(batch_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_import_record_customer_order_no
    ON sales_order_import_record(customer_order_no);

CREATE TABLE IF NOT EXISTS sales_order_header (
    id BIGSERIAL PRIMARY KEY,
    sales_order_no VARCHAR(100) NOT NULL,
    document_no VARCHAR(100),
    document_type_code VARCHAR(50) NOT NULL DEFAULT 'SO01',
    customer_id BIGINT,
    customer_code VARCHAR(200),
    customer_short_name VARCHAR(200),
    customer_name VARCHAR(200),
    source_type VARCHAR(30) NOT NULL DEFAULT 'DIRECT_IMPORT',
    source_release_batch_key VARCHAR(100),
    source_release_request_hash VARCHAR(64),
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
    u9_sync_message TEXT,
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
    source_type VARCHAR(30) NOT NULL DEFAULT 'DIRECT_IMPORT',
    source_order_id BIGINT,
    source_item_id BIGINT,
    source_ticket_id INTEGER,
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
    material_shape VARCHAR(30),
    surface_treatment VARCHAR(255),
    tax_included_unit_price NUMERIC(14, 4),
    untaxed_unit_price NUMERIC(14, 4),
    tax_included_total_price NUMERIC(14, 2),
    untaxed_total_price NUMERIC(14, 2),
    shipped_qty INTEGER DEFAULT 0,
    unshipped_qty INTEGER DEFAULT 0,
    released_qty INTEGER DEFAULT 0,
    remaining_qty INTEGER DEFAULT 0,
    drawing_url VARCHAR(500),
    has_drawing BOOLEAN DEFAULT FALSE,
    material_gross_weight NUMERIC(14, 4),
    part_net_weight NUMERIC(14, 4),
    quote_mode VARCHAR(20),
    assembly_fee NUMERIC(12, 2),
    base_cost NUMERIC(12, 2),
    item_type VARCHAR(20),
    assembly_relation_status VARCHAR(30) DEFAULT '不适用',
    material_data JSONB DEFAULT '{}'::jsonb,
    process_route JSONB DEFAULT '[]'::jsonb,
    fee_data JSONB DEFAULT '[]'::jsonb,
    matched_rfq_id INTEGER,
    matched_item_id INTEGER,
    match_status VARCHAR(30),
    match_message TEXT,
    remark TEXT,
    customer_requirement TEXT,
    status VARCHAR(30) NOT NULL DEFAULT '已报价',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_order_item_qty CHECK (qty > 0)
);

ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS sales_order_id BIGINT;
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS sales_order_no VARCHAR(100);
ALTER TABLE sales_order_import_record ADD COLUMN IF NOT EXISTS item_count INTEGER DEFAULT 0;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS customer_requirement TEXT;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS sales_order_date DATE;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255);
ALTER TABLE sales_order_header ADD COLUMN IF NOT EXISTS source_type VARCHAR(30);
ALTER TABLE sales_order_header ADD COLUMN IF NOT EXISTS source_release_batch_key VARCHAR(100);
ALTER TABLE sales_order_header ADD COLUMN IF NOT EXISTS source_release_request_hash VARCHAR(64);
UPDATE sales_order_header
SET source_type = 'DIRECT_IMPORT'
WHERE source_type IS NULL OR BTRIM(source_type) = '';
ALTER TABLE sales_order_header
    ALTER COLUMN source_type SET DEFAULT 'DIRECT_IMPORT',
    ALTER COLUMN source_type SET NOT NULL;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS source_type VARCHAR(30);
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS source_order_id BIGINT;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS source_item_id BIGINT;
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS source_ticket_id INTEGER;
UPDATE sales_order_item
SET source_type = 'DIRECT_IMPORT'
WHERE source_type IS NULL OR BTRIM(source_type) = '';
ALTER TABLE sales_order_item
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

CREATE TABLE IF NOT EXISTS sales_order_source_rfq (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id) ON DELETE CASCADE,
    rfq_id INTEGER NOT NULL REFERENCES order_rfq(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_order_source_rfq UNIQUE (sales_order_id, rfq_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_header_no
    ON sales_order_header(sales_order_no);

CREATE INDEX IF NOT EXISTS idx_sales_order_header_status_created
    ON sales_order_header(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sales_order_header_customer
    ON sales_order_header(customer_name, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_header_customer_order_no
    ON sales_order_header(customer_order_no);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_header_release_batch
    ON sales_order_header(source_release_batch_key)
    WHERE source_release_batch_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sales_order_header_source_type_created
    ON sales_order_header(source_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_order_id_id
    ON sales_order_item(sales_order_id, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_status_id
    ON sales_order_item(status, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_part_version_id
    ON sales_order_item(part_no, version, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_material_no_id
    ON sales_order_item(material_no, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_source_order_item
    ON sales_order_item(source_type, source_order_id, source_item_id, id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_item_source_ticket
    ON sales_order_item(source_ticket_id)
    WHERE source_ticket_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_sales_order_source_rfq_rfq
    ON sales_order_source_rfq(rfq_id, sales_order_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_source_rfq_sales_order
    ON sales_order_source_rfq(sales_order_id, rfq_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_import_record_sales_order_id
    ON sales_order_import_record(sales_order_id);

CREATE TABLE IF NOT EXISTS sales_order_item_assembly_relation (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    parent_item_id BIGINT NOT NULL REFERENCES sales_order_item(id) ON DELETE CASCADE,
    child_item_id BIGINT NOT NULL REFERENCES sales_order_item(id) ON DELETE CASCADE,
    qty_per_parent NUMERIC(14, 4) NOT NULL DEFAULT 1,
    sort_no INTEGER DEFAULT 0,
    source_parent_item_id INTEGER,
    source_child_item_id INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_order_assembly_distinct_items CHECK (parent_item_id <> child_item_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_item_assembly_parent_child
    ON sales_order_item_assembly_relation(parent_item_id, child_item_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_assembly_sales_order
    ON sales_order_item_assembly_relation(sales_order_id, parent_item_id, sort_no, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_assembly_child
    ON sales_order_item_assembly_relation(child_item_id);

CREATE TABLE IF NOT EXISTS dict_material (
    id SERIAL PRIMARY KEY,
    material_name VARCHAR(100) NOT NULL,
    material_type VARCHAR(50),
    density NUMERIC(12, 4),
    plate_density NUMERIC(12, 4),
    plate_price NUMERIC(12, 2),
    bar_density NUMERIC(12, 4),
    bar_price NUMERIC(12, 2),
    tube_density NUMERIC(12, 4),
    tube_price NUMERIC(12, 2),
    remarks TEXT
);

ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS plate_density NUMERIC(12, 4);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS bar_density NUMERIC(12, 4);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS tube_density NUMERIC(12, 4);

DROP INDEX IF EXISTS idx_dict_material_name;
CREATE INDEX IF NOT EXISTS idx_dict_material_name ON dict_material(material_name);
CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_material_name_remarks ON dict_material(material_name, remarks);

CREATE TABLE IF NOT EXISTS dict_process_category (
    id BIGSERIAL PRIMARY KEY,
    process_code VARCHAR(64) NOT NULL,
    category_name VARCHAR(120) NOT NULL,
    hourly_rate NUMERIC(18, 4) NOT NULL DEFAULT 0,
    remarks VARCHAR(500),
    source_type VARCHAR(30) NOT NULL DEFAULT 'LOCAL',
    mes_external_id VARCHAR(120),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_no INTEGER NOT NULL DEFAULT 0,
    mes_synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dict_process_category_code UNIQUE (process_code),
    CONSTRAINT chk_dict_process_category_rate CHECK (hourly_rate >= 0),
    CONSTRAINT chk_dict_process_category_source CHECK (source_type IN ('LOCAL', 'MES'))
);

CREATE TABLE IF NOT EXISTS dict_process_detail (
    id BIGSERIAL PRIMARY KEY,
    category_id BIGINT NOT NULL REFERENCES dict_process_category(id) ON DELETE RESTRICT,
    detail_name VARCHAR(120) NOT NULL,
    detail_remarks VARCHAR(500),
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_dict_process_detail_category_name UNIQUE (category_id, detail_name)
);

CREATE INDEX IF NOT EXISTS idx_dict_process_category_enabled_sort
    ON dict_process_category(enabled, sort_no, id);

CREATE INDEX IF NOT EXISTS idx_dict_process_category_name
    ON dict_process_category(category_name, id);

CREATE INDEX IF NOT EXISTS idx_dict_process_category_mes_external
    ON dict_process_category(mes_external_id)
    WHERE mes_external_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_dict_process_detail_category_enabled_sort
    ON dict_process_detail(category_id, enabled, sort_no, id);

CREATE INDEX IF NOT EXISTS idx_dict_process_detail_name
    ON dict_process_detail(detail_name, id);

CREATE TABLE IF NOT EXISTS dict_markup_fee (
    id SERIAL PRIMARY KEY,
    fee_name VARCHAR(100) NOT NULL UNIQUE,
    rate_percent NUMERIC(10, 2) DEFAULT 0,
    sort_no INTEGER,
    enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS dict_assembly_markup_fee (
    id SERIAL PRIMARY KEY,
    fee_name VARCHAR(100) NOT NULL UNIQUE,
    rate_percent NUMERIC(10, 2) DEFAULT 0,
    sort_no INTEGER,
    enabled BOOLEAN DEFAULT TRUE
);

CREATE TABLE IF NOT EXISTS dict_extra_fee_item (
    id SERIAL PRIMARY KEY,
    fee_name VARCHAR(100) NOT NULL UNIQUE,
    sort_no INTEGER,
    enabled BOOLEAN DEFAULT TRUE,
    remarks TEXT
);

CREATE TABLE IF NOT EXISTS order_delivery_ticket (
    id SERIAL PRIMARY KEY,
    rfq_id INTEGER REFERENCES order_rfq(id),
    rfq_no VARCHAR(100),
    customer_name VARCHAR(255),
    item_id INTEGER REFERENCES item_quote_version(id),
    source_type VARCHAR(30) NOT NULL DEFAULT 'RFQ',
    source_order_id BIGINT,
    source_item_id BIGINT,
    order_type VARCHAR(20),
    internal_project_no VARCHAR(100),
    customer_order_no VARCHAR(100),
    customer_project_no VARCHAR(100),
    project_no VARCHAR(100),
    item_type VARCHAR(20),
    part_no VARCHAR(100),
    description TEXT,
    qty INTEGER,
    material_name VARCHAR(100),
    engineering_material_name VARCHAR(255),
    material_shape VARCHAR(30),
    surface_treatment VARCHAR(100),
    version VARCHAR(20),
    start_date DATE,
    delivery_date DATE,
    remark TEXT,
    customer_requirement TEXT,
    arrange_type VARCHAR(50),
    drawing_url VARCHAR(500),
    process_route JSONB DEFAULT '[]'::jsonb,
    material_data JSONB DEFAULT '{}'::jsonb,
    material_gross_weight NUMERIC(14, 4),
    part_net_weight NUMERIC(14, 4),
    material_no VARCHAR(100),
    mes_sync_status VARCHAR(30) DEFAULT 'PENDING',
    mes_sync_message TEXT,
    mes_synced_at TIMESTAMP,
    wms_allocate_status VARCHAR(30) DEFAULT 'PENDING',
    wms_allocate_message TEXT,
    wms_allocated_at TIMESTAMP
);

ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS item_id INTEGER REFERENCES item_quote_version(id);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS rfq_no VARCHAR(100);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS customer_name VARCHAR(255);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) NOT NULL DEFAULT 'RFQ';
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS source_order_id BIGINT;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS source_item_id BIGINT;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS internal_project_no VARCHAR(100);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS drawing_url VARCHAR(500);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS process_route JSONB;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS material_data JSONB;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS material_no VARCHAR(100);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS mes_sync_status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS mes_sync_message TEXT;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS mes_synced_at TIMESTAMP;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS wms_allocate_status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS wms_allocate_message TEXT;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS wms_allocated_at TIMESTAMP;
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS customer_requirement TEXT;
ALTER TABLE order_delivery_ticket
    ALTER COLUMN process_route TYPE JSONB USING NULLIF(process_route::TEXT, '')::JSONB,
    ALTER COLUMN material_data TYPE JSONB USING NULLIF(material_data::TEXT, '')::JSONB;
ALTER TABLE order_delivery_ticket
    ALTER COLUMN process_route SET DEFAULT '[]'::jsonb,
    ALTER COLUMN material_data SET DEFAULT '{}'::jsonb;

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

CREATE TABLE IF NOT EXISTS order_delivery_ticket_allocation (
    id SERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    rfq_id INTEGER REFERENCES order_rfq(id),
    item_id INTEGER REFERENCES item_quote_version(id),
    source_type VARCHAR(30),
    source_order_id BIGINT,
    source_item_id BIGINT,
    allocation_type VARCHAR(40) NOT NULL,
    qty INTEGER NOT NULL,
    material_no VARCHAR(100),
    order_type VARCHAR(20),
    item_type VARCHAR(20) DEFAULT '普通件',
    part_no VARCHAR(100),
    version VARCHAR(20),
    rfq_no VARCHAR(100),
    customer_name VARCHAR(255),
    internal_project_no VARCHAR(100),
    customer_order_no VARCHAR(100),
    customer_project_no VARCHAR(100),
    material_name VARCHAR(100),
    engineering_material_name VARCHAR(255),
    material_shape VARCHAR(30),
    surface_treatment VARCHAR(100),
    remark TEXT,
    customer_requirement TEXT,
    description TEXT,
    delivery_date DATE,
    process_route JSONB DEFAULT '[]'::jsonb,
    material_data JSONB DEFAULT '{}'::jsonb,
    material_gross_weight NUMERIC(14, 4),
    part_net_weight NUMERIC(14, 4),
    wms_production_order_no VARCHAR(100),
    source_payload JSONB DEFAULT '{}'::jsonb,
    ticket_qty INTEGER,
    mes_production_status VARCHAR(30) DEFAULT 'PENDING',
    mes_production_request_id VARCHAR(80),
    mes_production_external_id VARCHAR(120),
    mes_production_synced_at TIMESTAMP,
    mes_production_message TEXT,
    status VARCHAR(30) DEFAULT 'ACTIVE',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT chk_order_delivery_ticket_allocation_qty CHECK (qty > 0),
    CONSTRAINT chk_order_delivery_ticket_allocation_type CHECK (
        allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK', 'PRODUCTION_REQUIRED')
    )
);

ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS rfq_no VARCHAR(100);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS source_type VARCHAR(30);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS source_order_id BIGINT;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS source_item_id BIGINT;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS order_type VARCHAR(20);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) DEFAULT '普通件';
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(100);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS customer_project_no VARCHAR(100);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS material_name VARCHAR(100);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS engineering_material_name VARCHAR(255);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS material_shape VARCHAR(30);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS surface_treatment VARCHAR(100);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS remark TEXT;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS customer_requirement TEXT;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS description TEXT;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS delivery_date DATE;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS process_route JSONB DEFAULT '[]'::jsonb;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS material_data JSONB DEFAULT '{}'::jsonb;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS ticket_qty INTEGER;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS mes_production_status VARCHAR(30) DEFAULT 'PENDING';
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS mes_production_request_id VARCHAR(80);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS mes_production_external_id VARCHAR(120);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS mes_production_synced_at TIMESTAMP;
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS mes_production_message TEXT;
ALTER TABLE order_delivery_ticket_allocation
    ALTER COLUMN process_route TYPE JSONB USING NULLIF(process_route::TEXT, '')::JSONB,
    ALTER COLUMN material_data TYPE JSONB USING NULLIF(material_data::TEXT, '')::JSONB;
ALTER TABLE order_delivery_ticket_allocation
    ALTER COLUMN process_route SET DEFAULT '[]'::jsonb,
    ALTER COLUMN material_data SET DEFAULT '{}'::jsonb;

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_part_version
    ON order_delivery_ticket(part_no, version, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_material_no
    ON order_delivery_ticket(material_no, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_mes_status
    ON order_delivery_ticket(mes_sync_status, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_wms_status
    ON order_delivery_ticket(wms_allocate_status, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_internal_project
    ON order_delivery_ticket(internal_project_no, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_no
    ON order_delivery_ticket(rfq_no, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_delivery_date
    ON order_delivery_ticket(delivery_date, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_id_id
    ON order_delivery_ticket(rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_item_id
    ON order_delivery_ticket(rfq_id, item_id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_source
    ON order_delivery_ticket(source_type, source_order_id, source_item_id);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_active_flow_id
    ON order_delivery_ticket(id DESC)
    WHERE NULLIF(BTRIM(material_no), '') IS NULL
       OR COALESCE(mes_sync_status, 'PENDING') <> 'SUCCESS'
       OR COALESCE(wms_allocate_status, 'PENDING') <> 'SUCCESS';

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_completed_flow_id
    ON order_delivery_ticket(mes_sync_status, wms_allocate_status, id DESC)
    WHERE NULLIF(BTRIM(material_no), '') IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_ticket
    ON order_delivery_ticket_allocation(ticket_id, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_rfq
    ON order_delivery_ticket_allocation(rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_material
    ON order_delivery_ticket_allocation(material_no, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_part_version
    ON order_delivery_ticket_allocation(part_no, version, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_type
    ON order_delivery_ticket_allocation(allocation_type, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_wms_order
    ON order_delivery_ticket_allocation(wms_production_order_no, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_rfq_no
    ON order_delivery_ticket_allocation(rfq_no, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_internal_project
    ON order_delivery_ticket_allocation(internal_project_no, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_delivery_date
    ON order_delivery_ticket_allocation(delivery_date, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_item_type
    ON order_delivery_ticket_allocation(item_type, id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_source
    ON order_delivery_ticket_allocation(source_type, source_order_id, source_item_id);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_mes_production_status
    ON order_delivery_ticket_allocation(mes_production_status, allocation_type, id DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_mes_production_status_id
    ON order_delivery_ticket_allocation(mes_production_status, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_allocation_mes_production_request
    ON order_delivery_ticket_allocation(mes_production_request_id)
    WHERE mes_production_request_id IS NOT NULL AND TRIM(mes_production_request_id) <> '';

INSERT INTO dict_material (
    material_name, material_type, remarks, density, plate_density, plate_price,
    bar_density, bar_price, tube_density, tube_price
)
VALUES
    ('45#钢', '黑色金属', '', 7.8500, 7.8500, 5.50, 7.8500, 6.20, 7.8500, 7.00),
    ('AL6061铝合金', '有色金属', '', 2.7000, 2.7000, 28.00, 2.7000, 30.00, 2.7000, 32.00),
    ('SUS304不锈钢', '不锈钢', '', 7.9300, 7.9300, 35.00, 7.9300, 38.00, 7.9300, 40.00),
    ('黄铜', '有色金属', '', 8.5000, 8.5000, 45.00, 8.5000, 48.00, 8.5000, 50.00)
ON CONFLICT (material_name, remarks) DO NOTHING;

INSERT INTO dict_process_category (
    process_code, category_name, hourly_rate, remarks, source_type, enabled, sort_no
)
VALUES
    ('CNC', 'CNC', 120.0000, '本地初始化工艺大类', 'LOCAL', TRUE, 1),
    ('WIRE_CUT', '线割', 80.0000, '本地初始化工艺大类', 'LOCAL', TRUE, 2),
    ('LATHE', '车床', 100.0000, '本地初始化工艺大类', 'LOCAL', TRUE, 3)
ON CONFLICT (process_code) DO NOTHING;

INSERT INTO dict_process_detail (category_id, detail_name, detail_remarks, enabled, sort_no)
SELECT c.id, seed.detail_name, '本地初始化具体工艺', TRUE, seed.sort_no
FROM (
    VALUES
        ('CNC', 'CNC', 1),
        ('WIRE_CUT', '线割', 2),
        ('LATHE', '车床', 3)
) AS seed(process_code, detail_name, sort_no)
JOIN dict_process_category c ON c.process_code = seed.process_code
ON CONFLICT (category_id, detail_name) DO NOTHING;

INSERT INTO dict_markup_fee (fee_name, rate_percent, sort_no, enabled)
VALUES
    ('运输费', 5.00, 1, TRUE),
    ('管理费', 5.00, 2, TRUE),
    ('利润', 15.00, 3, TRUE),
    ('税费', 13.00, 4, TRUE)
ON CONFLICT (fee_name) DO NOTHING;

INSERT INTO dict_assembly_markup_fee (fee_name, rate_percent, sort_no, enabled)
SELECT fee_name, rate_percent, sort_no, enabled
FROM dict_markup_fee
WHERE fee_name NOT LIKE '%运输%'
  AND fee_name NOT LIKE '%运费%'
ON CONFLICT (fee_name) DO NOTHING;

INSERT INTO dict_extra_fee_item (fee_name, sort_no, enabled, remarks)
VALUES
    ('EDM', 1, TRUE, ''),
    ('热处理', 2, TRUE, ''),
    ('表面处理', 3, TRUE, ''),
    ('QA', 4, TRUE, ''),
    ('焊钳工\焊缝处理\测漏\校平', 5, TRUE, '')
ON CONFLICT (fee_name) DO NOTHING;
