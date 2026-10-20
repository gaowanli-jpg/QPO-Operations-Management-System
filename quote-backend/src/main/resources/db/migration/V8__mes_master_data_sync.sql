-- MES master data sync: read-only Data Center endpoints for process types and material pages.

SET search_path TO "${flyway:defaultSchema}";

ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS source_type VARCHAR(30) NOT NULL DEFAULT 'LOCAL';
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_external_id VARCHAR(120);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_material_type VARCHAR(40);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_purchase_type VARCHAR(60);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_production_type VARCHAR(60);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_doc_num VARCHAR(120);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_standard VARCHAR(255);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_weight VARCHAR(80);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_density_raw VARCHAR(80);
ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS mes_synced_at TIMESTAMP;

CREATE INDEX IF NOT EXISTS idx_dict_material_source_type ON dict_material (source_type);
CREATE INDEX IF NOT EXISTS idx_dict_material_mes_external_id ON dict_material (mes_external_id) WHERE mes_external_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS mes_master_data_sync_run (
    id BIGSERIAL PRIMARY KEY,
    sync_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    finished_at TIMESTAMP,
    fetched_count INTEGER NOT NULL DEFAULT 0,
    valid_count INTEGER NOT NULL DEFAULT 0,
    invalid_count INTEGER NOT NULL DEFAULT 0,
    duplicate_count INTEGER NOT NULL DEFAULT 0,
    upserted_count INTEGER NOT NULL DEFAULT 0,
    disabled_count INTEGER NOT NULL DEFAULT 0,
    message TEXT,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_mes_master_data_sync_run_type_started
    ON mes_master_data_sync_run (sync_type, started_at DESC);
CREATE INDEX IF NOT EXISTS idx_mes_master_data_sync_run_status
    ON mes_master_data_sync_run (status);

INSERT INTO system_data_center_endpoint_config (
    endpoint_code,
    display_name,
    target_system_code,
    method,
    path,
    enabled,
    timeout_ms,
    request_adapter,
    response_adapter,
    request_schema_json,
    response_schema_json,
    remark,
    created_at,
    updated_at
) VALUES (
    'MES_PROCESS_TYPE_LIST',
    'MES process type list',
    'MES',
    'GET',
    '/api/v1/mes/openapi/process-type/list',
    true,
    60000,
    'MES_PROCESS_TYPE_LIST_V1',
    'MES_PROCESS_TYPE_LIST_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center reads MES process type master data for QPO dictionary sync.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (endpoint_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    target_system_code = EXCLUDED.target_system_code,
    method = EXCLUDED.method,
    path = EXCLUDED.path,
    enabled = true,
    timeout_ms = GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 60000),
    request_adapter = EXCLUDED.request_adapter,
    response_adapter = EXCLUDED.response_adapter,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_data_center_endpoint_config (
    endpoint_code,
    display_name,
    target_system_code,
    method,
    path,
    enabled,
    timeout_ms,
    request_adapter,
    response_adapter,
    request_schema_json,
    response_schema_json,
    remark,
    created_at,
    updated_at
) VALUES (
    'MES_MATERIAL_PAGE',
    'MES material page',
    'MES',
    'GET',
    '/api/v1/mes/openapi/material/page',
    true,
    60000,
    'MES_MATERIAL_PAGE_V1',
    'MES_MATERIAL_PAGE_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center reads MES material master data pages for QPO dictionary sync.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (endpoint_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    target_system_code = EXCLUDED.target_system_code,
    method = EXCLUDED.method,
    path = EXCLUDED.path,
    enabled = true,
    timeout_ms = GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 60000),
    request_adapter = EXCLUDED.request_adapter,
    response_adapter = EXCLUDED.response_adapter,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP;
