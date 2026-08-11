ALTER TABLE sales_order_header
    ADD COLUMN u9_seller_code varchar(100),
    ADD COLUMN u9_seller_name varchar(200),
    ADD COLUMN u9_sale_department_code varchar(100),
    ADD COLUMN u9_sale_department_name varchar(200),
    ADD COLUMN u9_sync_attempt_id varchar(64),
    ADD COLUMN u9_sync_request_hash varchar(64),
    ADD COLUMN u9_sync_started_at timestamp without time zone,
    ADD COLUMN u9_sync_finished_at timestamp without time zone;

UPDATE sales_order_header
SET document_no = sales_order_no
WHERE NULLIF(btrim(document_no), '') IS NULL;

UPDATE sales_order_header
SET u9_sync_status = '未同步'
WHERE NULLIF(btrim(u9_sync_status), '') IS NULL;

ALTER TABLE sales_order_header
    ALTER COLUMN document_no SET NOT NULL,
    ALTER COLUMN u9_sync_status SET DEFAULT '未同步',
    ALTER COLUMN u9_sync_status SET NOT NULL;

CREATE UNIQUE INDEX uk_sales_order_header_document_no
    ON sales_order_header(document_no);

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
    'U9_COMMON_ENTITY_QUERY',
    'U9通用实体查询',
    'U9',
    'POST',
    '/api/v1/u9/CommonEntity/Query',
    true,
    60000,
    'U9_COMMON_ENTITY_QUERY_V1',
    'U9_COMMON_ENTITY_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'U9销售员、部门和销售订单对账查询。',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (endpoint_code) DO UPDATE
SET path = COALESCE(
        NULLIF(btrim(system_data_center_endpoint_config.path), ''),
        EXCLUDED.path
    ),
    enabled = true,
    timeout_ms = GREATEST(
        COALESCE(system_data_center_endpoint_config.timeout_ms, 0),
        60000
    ),
    updated_at = CURRENT_TIMESTAMP;

UPDATE system_data_center_endpoint_config
SET timeout_ms = GREATEST(COALESCE(timeout_ms, 0), 60000),
    updated_at = CURRENT_TIMESTAMP
WHERE endpoint_code = 'U9_SALES_ORDER_SYNC';
