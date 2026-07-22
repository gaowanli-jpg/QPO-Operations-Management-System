ALTER TABLE inventory_master ADD COLUMN IF NOT EXISTS version VARCHAR(20);
UPDATE inventory_master SET version = 'V1' WHERE version IS NULL OR TRIM(version) = '';
ALTER TABLE inventory_master ALTER COLUMN version SET DEFAULT 'V1';
ALTER TABLE inventory_master ALTER COLUMN version SET NOT NULL;

DROP INDEX IF EXISTS idx_inventory_customer_part;
CREATE UNIQUE INDEX IF NOT EXISTS idx_inventory_customer_part_version
    ON inventory_master(customer_name, part_no, version);

ALTER TABLE order_process_sheet ADD COLUMN IF NOT EXISTS version VARCHAR(20);
UPDATE order_process_sheet SET version = 'V1' WHERE version IS NULL OR TRIM(version) = '';
