-- MES phase 7: route official MES batch endpoints through Data Center with a 60s floor.

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
    'MES_MATERIAL_RESOLVE',
    'MES物料号获取',
    'MES',
    'POST',
    '/api/v1/mes/openapi/material-preparation/create',
    true,
    60000,
    'MES_MATERIAL_V1',
    'MES_MATERIAL_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center forwards QPO material batch requests to MES.',
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
    'MES_PRODUCTION_DISPATCH',
    '下发MES生产',
    'MES',
    'POST',
    '/api/v1/mes/openapi/demand/create',
    true,
    60000,
    'MES_PRODUCTION_DISPATCH_V1',
    'MES_PRODUCTION_DISPATCH_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center forwards QPO production demand batches to MES.',
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
