ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS quote_mode VARCHAR(20) DEFAULT 'NORMAL';
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS assembly_fee NUMERIC(12, 2) DEFAULT 0;

UPDATE item_quote_version SET quote_mode = 'NORMAL' WHERE quote_mode IS NULL OR TRIM(quote_mode) = '';
UPDATE item_quote_version SET assembly_fee = 0 WHERE assembly_fee IS NULL;

CREATE TABLE IF NOT EXISTS item_assembly_relation (
    id SERIAL PRIMARY KEY,
    rfq_id INTEGER NOT NULL REFERENCES order_rfq(id) ON DELETE CASCADE,
    parent_item_id INTEGER NOT NULL REFERENCES item_quote_version(id) ON DELETE CASCADE,
    child_item_id INTEGER NOT NULL REFERENCES item_quote_version(id) ON DELETE CASCADE,
    child_qty_per_parent NUMERIC(14, 4) NOT NULL DEFAULT 1,
    sort_no INTEGER DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_assembly_distinct_items CHECK (parent_item_id <> child_item_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_assembly_parent_child
    ON item_assembly_relation(parent_item_id, child_item_id);
CREATE INDEX IF NOT EXISTS idx_assembly_parent
    ON item_assembly_relation(parent_item_id);
CREATE INDEX IF NOT EXISTS idx_assembly_child
    ON item_assembly_relation(child_item_id);
CREATE INDEX IF NOT EXISTS idx_assembly_rfq
    ON item_assembly_relation(rfq_id);
