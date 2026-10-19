SET search_path TO "${flyway:defaultSchema}";

ALTER TABLE sales_order_change_notice
    ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_reason VARCHAR(500),
    ADD COLUMN hidden_at TIMESTAMP,
    ADD COLUMN hidden_by BIGINT,
    ADD COLUMN hidden_by_name VARCHAR(100),
    ADD COLUMN restored_at TIMESTAMP,
    ADD COLUMN restored_by BIGINT;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM system_permission
        WHERE id IN (237, 238)
          AND permission_code NOT IN ('change:sales-change:hide', 'change:sales-change:restore')
    ) OR EXISTS (
        SELECT 1
        FROM system_permission
        WHERE permission_code IN ('change:sales-change:hide', 'change:sales-change:restore')
          AND id NOT IN (237, 238)
    ) THEN
        RAISE EXCEPTION 'Sales change visibility permission IDs or codes are already assigned';
    END IF;
END $$;

INSERT INTO system_permission (
    id, parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled,
    created_at, updated_at, assignable
) VALUES
    (237, 233, 'BUTTON', 'change:sales-change:hide', '隐藏销售变更通知', NULL, NULL, NULL, 706, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true),
    (238, 233, 'BUTTON', 'change:sales-change:restore', '恢复销售变更通知', NULL, NULL, NULL, 707, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_code = EXCLUDED.permission_code,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE permission.permission_code IN ('change:sales-change:hide', 'change:sales-change:restore')
  AND role.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 238), TRUE);
