CREATE TABLE IF NOT EXISTS aps_process (
    id BIGSERIAL PRIMARY KEY,
    process_name VARCHAR(100) NOT NULL,
    process_type VARCHAR(50) NOT NULL,
    calc_type VARCHAR(50),
    erp_source_id INTEGER,
    sort_no INTEGER,
    synced_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_process_type CHECK (process_type IN ('WORK_HOUR', 'NON_WORK_HOUR')),
    CONSTRAINT uq_aps_process_name_type UNIQUE (process_name, process_type)
);

CREATE INDEX IF NOT EXISTS idx_aps_process_type_sort ON aps_process(process_type, sort_no, id);
CREATE INDEX IF NOT EXISTS idx_aps_process_erp_source ON aps_process(process_type, erp_source_id);

ALTER TABLE aps_machine_process_capability
    ADD COLUMN IF NOT EXISTS process_id BIGINT REFERENCES aps_process(id) ON DELETE RESTRICT;

INSERT INTO aps_process (process_name, process_type, calc_type, synced_at)
SELECT DISTINCT
    TRIM(process_name),
    CASE
        WHEN calc_type = U&'\975E\5DE5\65F6\8D39\7528' THEN 'NON_WORK_HOUR'
        ELSE 'WORK_HOUR'
    END,
    calc_type,
    CURRENT_TIMESTAMP
FROM aps_machine_process_capability
WHERE process_name IS NOT NULL AND TRIM(process_name) <> ''
ON CONFLICT (process_name, process_type) DO UPDATE SET
    calc_type = COALESCE(EXCLUDED.calc_type, aps_process.calc_type),
    updated_at = CURRENT_TIMESTAMP;

UPDATE aps_machine_process_capability c
SET process_id = p.id
FROM aps_process p
WHERE c.process_id IS NULL
  AND TRIM(c.process_name) = p.process_name
  AND (
      (c.calc_type = U&'\975E\5DE5\65F6\8D39\7528' AND p.process_type = 'NON_WORK_HOUR')
      OR ((c.calc_type IS NULL OR c.calc_type <> U&'\975E\5DE5\65F6\8D39\7528') AND p.process_type = 'WORK_HOUR')
  );

CREATE INDEX IF NOT EXISTS idx_aps_machine_process_process_id ON aps_machine_process_capability(process_id);
