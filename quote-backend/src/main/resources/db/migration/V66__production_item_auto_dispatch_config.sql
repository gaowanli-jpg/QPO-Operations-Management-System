SET search_path TO "${flyway:defaultSchema}";

CREATE TABLE IF NOT EXISTS production_item_auto_dispatch_config (
    id                  VARCHAR(60) PRIMARY KEY,
    enabled             BOOLEAN      NOT NULL DEFAULT FALSE,
    schedule_mode       VARCHAR(20)  NOT NULL DEFAULT 'DAILY_TIME',
    daily_run_time      TIME         NOT NULL DEFAULT '17:00:00',
    batch_size          INTEGER      NOT NULL DEFAULT 200,
    batch_interval_ms   INTEGER      NOT NULL DEFAULT 3000,
    max_rounds          INTEGER      NOT NULL DEFAULT 50,
    warn_duration_ms    BIGINT       NOT NULL DEFAULT 3600000,
    failure_breaker     INTEGER      NOT NULL DEFAULT 3,
    include_failed      BOOLEAN      NOT NULL DEFAULT FALSE,
    last_run_at         TIMESTAMP,
    last_run_date       DATE,
    last_task_id        BIGINT,
    last_status         VARCHAR(20)  NOT NULL DEFAULT 'NEVER',
    last_message        VARCHAR(500),
    last_total          INTEGER,
    last_success        INTEGER,
    last_failed         INTEGER,
    last_skipped        INTEGER,
    last_rounds         INTEGER,
    last_duration_ms    BIGINT,
    updated_by          VARCHAR(60),
    updated_at          TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO production_item_auto_dispatch_config (id)
VALUES ('MES_PRODUCTION_AUTO_DISPATCH')
ON CONFLICT (id) DO NOTHING;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM system_permission
        WHERE id = 239
          AND permission_code <> 'market:production-item:mes-production-schedule'
    ) OR EXISTS (
        SELECT 1
        FROM system_permission
        WHERE permission_code = 'market:production-item:mes-production-schedule'
          AND id <> 239
    ) THEN
        RAISE EXCEPTION 'Production item auto dispatch schedule permission ID or code is already assigned';
    END IF;
END $$;

INSERT INTO system_permission (
    id, parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled,
    created_at, updated_at, assignable
) VALUES
    (239, 199, 'BUTTON', 'market:production-item:mes-production-schedule', '待产零件定时下发策略',
     NULL, NULL, NULL, 132, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
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
WHERE permission.permission_code = 'market:production-item:mes-production-schedule'
  AND role.role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 239), TRUE);
