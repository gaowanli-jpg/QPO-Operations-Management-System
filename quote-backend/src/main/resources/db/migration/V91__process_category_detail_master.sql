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

INSERT INTO dict_process_category (
    process_code,
    category_name,
    hourly_rate,
    remarks,
    source_type,
    enabled,
    sort_no
)
SELECT
    legacy.process_code,
    legacy.category_name,
    legacy.hourly_rate,
    legacy.remarks,
    'LOCAL',
    TRUE,
    legacy.sort_no
FROM (
    SELECT
        'LEGACY_' || p.id AS process_code,
        LEFT(COALESCE(NULLIF(TRIM(p.process_name), ''), 'Legacy Process ' || p.id), 120) AS category_name,
        COALESCE(
            p.price_1_5,
            p.price_5_10,
            p.price_10_50,
            p.price_50_100,
            p.price_100_500,
            p.price_500_1000,
            p.price_1000_3000,
            p.price_3000_plus,
            0
        )::NUMERIC(18, 4) AS hourly_rate,
        'Temporary backfill from legacy dict_process' AS remarks,
        COALESCE(p.sort_no, 0) AS sort_no
    FROM dict_process p
) legacy
ON CONFLICT (process_code) DO NOTHING;

INSERT INTO dict_process_detail (
    category_id,
    detail_name,
    detail_remarks,
    enabled,
    sort_no
)
SELECT
    c.id,
    LEFT(COALESCE(NULLIF(TRIM(p.process_name), ''), 'Legacy Process ' || p.id), 120) AS detail_name,
    'Temporary same-name detail from legacy dict_process' AS detail_remarks,
    TRUE,
    COALESCE(p.sort_no, 0) AS sort_no
FROM dict_process p
JOIN dict_process_category c ON c.process_code = 'LEGACY_' || p.id
ON CONFLICT (category_id, detail_name) DO NOTHING;
