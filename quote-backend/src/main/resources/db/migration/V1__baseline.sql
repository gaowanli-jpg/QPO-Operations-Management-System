CREATE TABLE IF NOT EXISTS order_rfq (
    id SERIAL PRIMARY KEY,
    rfq_no VARCHAR(100) NOT NULL UNIQUE,
    customer_name VARCHAR(200),
    project_name VARCHAR(255),
    status VARCHAR(50),
    engineer_config JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE order_rfq ADD COLUMN IF NOT EXISTS engineer_config JSONB;
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
    material_name VARCHAR(255),
    surface_treatment VARCHAR(255),
    unit VARCHAR(50),
    lead_time VARCHAR(100),
    remark TEXT,
    material_data JSONB,
    process_route JSONB,
    fee_data JSONB,
    base_cost NUMERIC(12, 2),
    final_unit_price NUMERIC(12, 2),
    final_total_price NUMERIC(12, 2),
    status VARCHAR(50),
    drawing_url VARCHAR(500),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS fee_data JSONB;
ALTER TABLE item_quote_version
    ALTER COLUMN material_data TYPE JSONB USING NULLIF(material_data::TEXT, '')::JSONB,
    ALTER COLUMN process_route TYPE JSONB USING NULLIF(process_route::TEXT, '')::JSONB,
    ALTER COLUMN fee_data TYPE JSONB USING NULLIF(fee_data::TEXT, '')::JSONB;

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

CREATE TABLE IF NOT EXISTS dict_process (
    id SERIAL PRIMARY KEY,
    process_name VARCHAR(50),
    calc_type VARCHAR(20),
    sort_no INTEGER,
    price_1_5 NUMERIC(10, 2),
    price_5_10 NUMERIC(10, 2),
    price_10_50 NUMERIC(10, 2),
    price_50_100 NUMERIC(10, 2),
    price_100_500 NUMERIC(10, 2),
    price_500_1000 NUMERIC(10, 2),
    price_1000_3000 NUMERIC(10, 2),
    price_3000_plus NUMERIC(10, 2)
);

ALTER TABLE dict_process ADD COLUMN IF NOT EXISTS sort_no INTEGER;
CREATE UNIQUE INDEX IF NOT EXISTS idx_dict_process_name ON dict_process(process_name);

CREATE TABLE IF NOT EXISTS dict_markup_fee (
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

CREATE TABLE IF NOT EXISTS inventory_master (
    id SERIAL PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    part_no VARCHAR(100) NOT NULL,
    in_stock_qty INTEGER DEFAULT 0,
    pending_qty INTEGER DEFAULT 0,
    pending_ref_no VARCHAR(100),
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE inventory_master ADD COLUMN IF NOT EXISTS customer_name VARCHAR(200);
UPDATE inventory_master SET customer_name = '未指定客户' WHERE customer_name IS NULL OR customer_name = '';
ALTER TABLE inventory_master ALTER COLUMN customer_name SET NOT NULL;
ALTER TABLE inventory_master ALTER COLUMN in_stock_qty SET DEFAULT 0;
ALTER TABLE inventory_master ALTER COLUMN pending_qty SET DEFAULT 0;
ALTER TABLE inventory_master DROP CONSTRAINT IF EXISTS inventory_master_part_no_key;
DROP INDEX IF EXISTS inventory_master_part_no_key;
CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_customer_part ON inventory_master(customer_name, part_no);
CREATE INDEX IF NOT EXISTS idx_inventory_customer_name ON inventory_master(customer_name);

CREATE TABLE IF NOT EXISTS inventory_pending_stock (
    id SERIAL PRIMARY KEY,
    inventory_id INTEGER REFERENCES inventory_master(id) ON DELETE CASCADE,
    pending_ref_no VARCHAR(100) NOT NULL,
    pending_qty INTEGER DEFAULT 0,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(inventory_id, pending_ref_no)
);
CREATE INDEX IF NOT EXISTS idx_inventory_pending_inventory ON inventory_pending_stock(inventory_id);
CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_pending_inventory_ref ON inventory_pending_stock(inventory_id, pending_ref_no);

INSERT INTO inventory_pending_stock (inventory_id, pending_ref_no, pending_qty, updated_at)
SELECT id, pending_ref_no, pending_qty, updated_at
FROM inventory_master
WHERE pending_qty > 0 AND pending_ref_no IS NOT NULL AND TRIM(pending_ref_no) <> ''
ON CONFLICT (inventory_id, pending_ref_no) DO NOTHING;

UPDATE inventory_master
SET pending_ref_no = NULL,
    pending_qty = COALESCE((
        SELECT SUM(pending_qty)
        FROM inventory_pending_stock
        WHERE inventory_pending_stock.inventory_id = inventory_master.id
    ), 0);

CREATE TABLE IF NOT EXISTS order_delivery_ticket (
    id SERIAL PRIMARY KEY,
    rfq_id INTEGER REFERENCES order_rfq(id),
    item_id INTEGER REFERENCES item_quote_version(id),
    order_type VARCHAR(20),
    project_no VARCHAR(100),
    part_no VARCHAR(100),
    qty INTEGER,
    material_name VARCHAR(100),
    surface_treatment VARCHAR(100),
    version VARCHAR(20),
    start_date DATE,
    delivery_date DATE,
    remark TEXT,
    arrange_type VARCHAR(50)
);

ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS item_id INTEGER REFERENCES item_quote_version(id);

CREATE TABLE IF NOT EXISTS order_process_sheet (
    id SERIAL PRIMARY KEY,
    rfq_id INTEGER REFERENCES order_rfq(id),
    item_id INTEGER REFERENCES item_quote_version(id),
    part_no VARCHAR(100),
    description TEXT,
    qty INTEGER,
    material_name VARCHAR(100),
    substitute_material VARCHAR(100),
    surface_treatment VARCHAR(100),
    heat_treatment VARCHAR(100),
    delivery_date DATE,
    remark TEXT,
    process_route JSONB
);

ALTER TABLE order_process_sheet
    ALTER COLUMN process_route TYPE JSONB USING NULLIF(process_route::TEXT, '')::JSONB;

CREATE INDEX IF NOT EXISTS idx_order_process_sheet_rfq ON order_process_sheet(rfq_id);

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

INSERT INTO dict_process (
    process_name, calc_type, sort_no, price_1_5, price_5_10, price_10_50, price_50_100,
    price_100_500, price_500_1000, price_1000_3000, price_3000_plus
)
VALUES
    ('CNC', '工时', 1, 120.00, 100.00, 80.00, 70.00, 60.00, 55.00, 50.00, 45.00),
    ('线割', '工时', 2, 80.00, 75.00, 70.00, 65.00, 60.00, 55.00, 50.00, 45.00),
    ('车床', '工时', 3, 100.00, 90.00, 75.00, 65.00, 58.00, 52.00, 48.00, 42.00)
ON CONFLICT (process_name) DO NOTHING;

UPDATE dict_process
SET calc_type = '工时'
WHERE calc_type IS NULL OR calc_type <> '工时';

INSERT INTO dict_markup_fee (fee_name, rate_percent, sort_no, enabled)
VALUES
    ('运输费', 5.00, 1, TRUE),
    ('管理费', 5.00, 2, TRUE),
    ('利润', 15.00, 3, TRUE),
    ('税费', 13.00, 4, TRUE)
ON CONFLICT (fee_name) DO NOTHING;

INSERT INTO dict_extra_fee_item (fee_name, sort_no, enabled, remarks)
VALUES
    ('EDM', 1, TRUE, ''),
    ('热处理', 2, TRUE, ''),
    ('表面处理', 3, TRUE, ''),
    ('QA', 4, TRUE, ''),
    ('焊钳工\焊缝处理\测漏\校平', 5, TRUE, '')
ON CONFLICT (fee_name) DO NOTHING;
