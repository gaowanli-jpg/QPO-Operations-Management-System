-- MES master data sync: read-only Data Center endpoints for process types and material pages.

SET search_path TO "${flyway:defaultSchema}";

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
