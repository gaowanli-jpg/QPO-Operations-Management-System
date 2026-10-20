-- Sales order optional WMS allocation: material-number success immediately enters the production pool.
ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS allocation_source VARCHAR(40);

COMMENT ON COLUMN order_delivery_ticket_allocation.allocation_source IS
    'Allocation origin: MES_MATERIAL for the default full production row, WMS_RESERVATION for WMS split rows';

-- Classify existing allocations before backfilling the default production rows.
UPDATE order_delivery_ticket_allocation
SET allocation_source = CASE
    WHEN wms_reservation_ticket_id IS NOT NULL THEN 'WMS_RESERVATION'
    ELSE 'LEGACY'
END
WHERE allocation_source IS NULL;

-- Backfill a full production allocation for every ticket whose material number is already confirmed.
-- Tickets covered by an in-flight WMS batch are skipped so recovery can reconcile them first.
WITH pending_wms AS (
    SELECT DISTINCT ticket_id
    FROM wms_reservation_ticket
    WHERE status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')
),
eligible AS (
    SELECT ticket.id, ticket.qty
    FROM order_delivery_ticket ticket
    WHERE COALESCE(ticket.discarded, FALSE) = FALSE
      AND NULLIF(BTRIM(ticket.material_no), '') IS NOT NULL
      AND COALESCE(ticket.mes_sync_status, 'PENDING') = 'SUCCESS'
      AND ticket.qty IS NOT NULL
      AND ticket.qty > 0
      AND UPPER(COALESCE(ticket.wms_allocate_status, 'PENDING')) NOT IN
          ('SUCCESS', 'PROCESSING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')
      AND NOT EXISTS (
          SELECT 1 FROM order_delivery_ticket_allocation alloc WHERE alloc.ticket_id = ticket.id
      )
      AND NOT EXISTS (
          SELECT 1 FROM pending_wms WHERE pending_wms.ticket_id = ticket.id
      )
)
INSERT INTO order_delivery_ticket_allocation (
    ticket_id, rfq_id, item_id, source_type, source_order_id, source_item_id,
    allocation_source, allocation_type, qty, ticket_qty, material_no, order_type, item_type,
    part_no, version, rfq_no, customer_name, internal_project_no, customer_order_no, customer_project_no,
    material_name, engineering_material_code, engineering_material_name, engineering_material_type_code,
    engineering_material_type_name, engineering_material_category, material_shape, surface_treatment,
    remark, description, delivery_date, process_route, material_data, material_gross_weight, part_net_weight,
    source_payload, mes_production_status, status, created_at, updated_at
)
SELECT
    ticket.id, ticket.rfq_id, ticket.item_id, ticket.source_type, ticket.source_order_id, ticket.source_item_id,
    'MES_MATERIAL', 'PRODUCTION_REQUIRED', eligible.qty, eligible.qty, ticket.material_no, ticket.order_type,
    ticket.item_type, ticket.part_no, ticket.version, ticket.rfq_no, ticket.customer_name, ticket.internal_project_no,
    ticket.customer_order_no, ticket.customer_project_no, ticket.material_name, ticket.engineering_material_code,
    ticket.engineering_material_name, ticket.engineering_material_type_code, ticket.engineering_material_type_name,
    ticket.engineering_material_category, ticket.material_shape, ticket.surface_treatment, ticket.remark,
    ticket.description, ticket.delivery_date, COALESCE(ticket.process_route, '[]'::jsonb),
    COALESCE(ticket.material_data, '{}'::jsonb), ticket.material_gross_weight, ticket.part_net_weight,
    jsonb_build_object('source', 'MES_MATERIAL_SUCCESS', 'ticketId', ticket.id, 'backfilled', TRUE),
    'PENDING', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM eligible
JOIN order_delivery_ticket ticket ON ticket.id = eligible.id;

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_allocation_material_production_row
    ON order_delivery_ticket_allocation(ticket_id)
    WHERE allocation_source = 'MES_MATERIAL';

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_active_production
    ON order_delivery_ticket_allocation(ticket_id, allocation_type, id)
    WHERE COALESCE(status, 'ACTIVE') = 'ACTIVE';

WITH permission_rows AS (
    SELECT 229::BIGINT AS id, 190::BIGINT AS parent_id, 'BUTTON'::VARCHAR AS permission_type,
           'market:sales-order:wms-allocate'::VARCHAR AS permission_code,
           '销售订单获取库存信息'::VARCHAR AS permission_name,
           NULL::VARCHAR AS route_path, NULL::VARCHAR AS component_key,
           134 AS sort_order, FALSE AS visible, TRUE AS assignable
)
INSERT INTO system_permission (
    id, parent_id, permission_type, permission_code, permission_name, route_path, component_key,
    icon, sort_order, visible, enabled, created_at, updated_at, assignable
)
SELECT id, parent_id, permission_type, permission_code, permission_name, route_path, component_key,
       NULL, sort_order, visible, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, assignable
FROM permission_rows
ON CONFLICT (permission_code) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_name = EXCLUDED.permission_name,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = TRUE,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE permission.permission_code = 'market:sales-order:wms-allocate'
  AND role.role_code IN (
      SELECT existing_role.role_code
      FROM system_role_permission existing
      JOIN system_role existing_role ON existing_role.id = existing.role_id
      JOIN system_permission existing_permission ON existing_permission.id = existing.permission_id
      WHERE existing_permission.permission_code = 'market:delivered-item:wms-allocate'
  )
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 229), TRUE);
