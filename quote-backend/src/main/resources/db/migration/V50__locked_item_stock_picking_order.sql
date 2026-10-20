-- Locked item stock-picking work order dispatch through Data Center -> MES.
-- This is independent from production-item MES dispatch and does not introduce shared permissions.

ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS stock_picking_status character varying(30) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS stock_picking_biz_id character varying(128),
    ADD COLUMN IF NOT EXISTS stock_picking_external_id character varying(128),
    ADD COLUMN IF NOT EXISTS stock_picking_synced_at timestamp,
    ADD COLUMN IF NOT EXISTS stock_picking_message character varying(500);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_stock_picking_status_id
    ON order_delivery_ticket_allocation(stock_picking_status, id DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_locked_stock_active_id
    ON order_delivery_ticket_allocation(id DESC)
    WHERE allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK')
      AND discarded = false
      AND COALESCE(NULLIF(TRIM(stock_picking_status), ''), 'PENDING') <> 'SUCCESS';

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
    'MES_STOCK_PICKING_ORDER_CREATE',
    'MES提库存工单创建',
    'MES',
    'POST',
    '/api/v1/mes/openapi/production-order/create',
    true,
    60000,
    'MES_PRODUCTION_ORDER_CREATE_V1',
    'MES_PRODUCTION_ORDER_CREATE_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center forwards QPO locked stock picking work order create requests to MES.',
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

INSERT INTO system_permission (
    id,
    parent_id,
    permission_type,
    permission_code,
    permission_name,
    route_path,
    component_key,
    icon,
    sort_order,
    visible,
    enabled,
    created_at,
    updated_at,
    assignable
) VALUES
    (222, 162, 'BUTTON', 'market:locked-item:stock-picking-submit', '锁库零件下发提库存工单', NULL, NULL, NULL, 129, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (223, 162, 'BUTTON', 'market:locked-item:discard', '废弃锁库零件', NULL, NULL, NULL, 130, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (224, 162, 'BUTTON', 'market:locked-item:restore', '恢复锁库零件', NULL, NULL, NULL, 131, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_code = EXCLUDED.permission_code,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE role.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
  AND permission.permission_code IN (
      'market:locked-item:stock-picking-submit',
      'market:locked-item:discard',
      'market:locked-item:restore'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 224), true);
