-- 销售订单废弃同步撤销 MES 需求任务：登记数据中台端点。
-- 只插入端点配置，不改表结构（mes_production_status 为 varchar(30) 且无 CHECK 约束，
-- 新增的 CANCELLED / CANCEL_FAILED 不需要变更列定义）。

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
    'MES_DEMAND_DELETE',
    'MES撤销需求任务',
    'MES',
    'POST',
    '/api/v1/mes/openapi/demand/delete',
    true,
    15000,
    'MES_DEMAND_DELETE_V1',
    'MES_DEMAND_DELETE_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center asks MES to delete a production demand task when a sales order is discarded.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (endpoint_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    target_system_code = EXCLUDED.target_system_code,
    method = EXCLUDED.method,
    path = EXCLUDED.path,
    enabled = true,
    timeout_ms = GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 15000),
    request_adapter = EXCLUDED.request_adapter,
    response_adapter = EXCLUDED.response_adapter,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP;
