-- Add the sales-order external sync query to configurable inbound interface metadata.
-- It shares the global QPO_OPEN_API_TOKEN with MES callbacks; no plaintext token is seeded here.

ALTER TABLE system_inbound_callback_config
    DROP CONSTRAINT IF EXISTS ck_system_inbound_callback_source;

ALTER TABLE system_inbound_callback_config
    ADD CONSTRAINT ck_system_inbound_callback_source
    CHECK (source_system_code IN ('MES', 'EXTERNAL_SALES_ORDER_SYNC'));

INSERT INTO system_inbound_callback_config (
    callback_code,
    source_system_code,
    name,
    enabled,
    token_enabled,
    public_base_url,
    callback_path,
    token_hash,
    token_mask,
    token_updated_at,
    last_validate_status,
    last_validate_message,
    last_validate_at,
    remark,
    created_by,
    updated_by,
    created_at,
    updated_at
) VALUES (
    'SALES_ORDER_SYNC_QUERY',
    'EXTERNAL_SALES_ORDER_SYNC',
    '销售订单外部同步查询',
    true,
    true,
    NULL,
    '/api/integrations/sales-orders',
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    'Sales order sync inbound metadata only; uses the shared QPO_OPEN_API_TOKEN.',
    NULL,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (callback_code) DO UPDATE SET
    source_system_code = EXCLUDED.source_system_code,
    name = EXCLUDED.name,
    callback_path = EXCLUDED.callback_path,
    token_enabled = EXCLUDED.token_enabled,
    updated_at = CURRENT_TIMESTAMP;
