-- MES master data timed sync runtime strategy.
-- Environment variables remain fallback defaults only; these rows are the runtime source of truth.

SET search_path TO "${flyway:defaultSchema}";

CREATE TABLE IF NOT EXISTS mes_master_data_sync_schedule_config (
    sync_type character varying(40) NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    startup_delay_ms bigint DEFAULT 30000 NOT NULL,
    fixed_delay_ms bigint DEFAULT 1800000 NOT NULL,
    last_scheduled_run_at timestamp without time zone,
    last_scheduled_sync_run_id bigint,
    last_scheduled_status character varying(30) DEFAULT 'NEVER'::character varying NOT NULL,
    last_scheduled_message character varying(500),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT mes_master_data_sync_schedule_config_pkey PRIMARY KEY (sync_type),
    CONSTRAINT chk_mes_master_data_sync_startup_delay CHECK ((startup_delay_ms >= 0) AND (startup_delay_ms <= 86400000)),
    CONSTRAINT chk_mes_master_data_sync_fixed_delay CHECK ((fixed_delay_ms >= 60000) AND (fixed_delay_ms <= 604800000))
);

INSERT INTO mes_master_data_sync_schedule_config (
    sync_type,
    enabled,
    startup_delay_ms,
    fixed_delay_ms,
    last_scheduled_status,
    updated_at
) VALUES
    ('MES_MATERIAL', false, 30000, 1800000, 'NEVER', CURRENT_TIMESTAMP),
    ('MES_PROCESS_TYPE', false, 30000, 1800000, 'NEVER', CURRENT_TIMESTAMP)
ON CONFLICT (sync_type) DO NOTHING;
