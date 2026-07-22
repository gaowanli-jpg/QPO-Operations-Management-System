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

CREATE TABLE IF NOT EXISTS erp_user (
    id SERIAL PRIMARY KEY,
    username VARCHAR(80) NOT NULL UNIQUE,
    password_hash VARCHAR(128) NOT NULL,
    display_name VARCHAR(100),
    role VARCHAR(30) NOT NULL,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS audit_log (
    id SERIAL PRIMARY KEY,
    username VARCHAR(80),
    role VARCHAR(30),
    action VARCHAR(100) NOT NULL,
    target_type VARCHAR(80),
    target_id VARCHAR(120),
    before_data TEXT,
    after_data TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_audit_log_created_at ON audit_log(created_at);
CREATE INDEX IF NOT EXISTS idx_audit_log_action ON audit_log(action);
CREATE INDEX IF NOT EXISTS idx_audit_log_target ON audit_log(target_type, target_id);

INSERT INTO erp_user (username, password_hash, display_name, role, enabled)
VALUES
    ('admin', '240be518fabd2724ddb6f04eeb1da5967448d7e831c08c8fa822809f74c720a9', '管理员', 'ADMIN', TRUE),
    ('market', 'bed5f6a42c9911986640161fe2934cb16fed6bcb2b03eb5a440b08db897dba0f', '市场部', 'MARKET', TRUE),
    ('engineer', '80ca306ac6e68366dd0a26125c9647e0c61fac6668cec6016f5fe30fb12e99bd', '工程部', 'ENGINEER', TRUE),
    ('warehouse', '0e842cbe0341154ee33e0ed3bc18282cd69e016a8d56fda05ec92e7ff20a0f31', '仓库部', 'WAREHOUSE', TRUE)
ON CONFLICT (username) DO NOTHING;
