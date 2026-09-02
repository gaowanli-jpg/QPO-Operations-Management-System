-- Add module-local discard/restore metadata for delivered and production item work pools.
-- Discarded rows are hidden from normal processing lists but remain restorable in their module.

ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS discarded boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS discarded_at timestamp,
    ADD COLUMN IF NOT EXISTS discarded_by bigint,
    ADD COLUMN IF NOT EXISTS discard_reason character varying(500),
    ADD COLUMN IF NOT EXISTS restored_at timestamp,
    ADD COLUMN IF NOT EXISTS restored_by bigint,
    ADD COLUMN IF NOT EXISTS restore_reason character varying(500);

ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS discarded boolean DEFAULT false NOT NULL,
    ADD COLUMN IF NOT EXISTS discarded_at timestamp,
    ADD COLUMN IF NOT EXISTS discarded_by bigint,
    ADD COLUMN IF NOT EXISTS discard_reason character varying(500),
    ADD COLUMN IF NOT EXISTS restored_at timestamp,
    ADD COLUMN IF NOT EXISTS restored_by bigint,
    ADD COLUMN IF NOT EXISTS restore_reason character varying(500);

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_discarded_id
    ON order_delivery_ticket(discarded, id DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_discarded_id
    ON order_delivery_ticket_allocation(discarded, id DESC);

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
    (210, 158, 'BUTTON', 'market:delivered-item:discard', '废弃已提零件', NULL, NULL, NULL, 131, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (211, 158, 'BUTTON', 'market:delivered-item:restore', '恢复已提零件', NULL, NULL, NULL, 132, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (212, 199, 'BUTTON', 'market:production-item:discard', '废弃待产零件', NULL, NULL, NULL, 130, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (213, 199, 'BUTTON', 'market:production-item:restore', '恢复待产零件', NULL, NULL, NULL, 131, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
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
SELECT role_id, permission_id, CURRENT_TIMESTAMP
FROM (VALUES
    (1, 210), (1, 211), (1, 212), (1, 213),
    (2, 210), (2, 211), (2, 212), (2, 213),
    (3, 210), (3, 211), (3, 212), (3, 213)
) AS grants(role_id, permission_id)
WHERE EXISTS (SELECT 1 FROM system_role WHERE id = grants.role_id)
  AND EXISTS (SELECT 1 FROM system_permission WHERE id = grants.permission_id)
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 213), true);
