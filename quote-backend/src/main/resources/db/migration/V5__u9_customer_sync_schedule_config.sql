-- U9 customer timed sync runtime strategy.
-- Environment variables remain fallback defaults only; this single-row table is the runtime source of truth.

SET search_path TO "${flyway:defaultSchema}";

CREATE TABLE IF NOT EXISTS u9_customer_sync_schedule_config (
    id integer NOT NULL,
    enabled boolean DEFAULT false NOT NULL,
    startup_delay_ms bigint DEFAULT 30000 NOT NULL,
    fixed_delay_ms bigint DEFAULT 1800000 NOT NULL,
    last_scheduled_run_at timestamp without time zone,
    last_scheduled_sync_run_id bigint,
    last_scheduled_status character varying(30) DEFAULT 'NEVER'::character varying NOT NULL,
    last_scheduled_message character varying(500),
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT u9_customer_sync_schedule_config_pkey PRIMARY KEY (id),
    CONSTRAINT chk_u9_customer_sync_startup_delay CHECK ((startup_delay_ms >= 0) AND (startup_delay_ms <= 86400000)),
    CONSTRAINT chk_u9_customer_sync_fixed_delay CHECK ((fixed_delay_ms >= 60000) AND (fixed_delay_ms <= 604800000))
);

INSERT INTO u9_customer_sync_schedule_config (
    id,
    enabled,
    startup_delay_ms,
    fixed_delay_ms,
    last_scheduled_status,
    updated_at
) VALUES (
    1,
    false,
    30000,
    1800000,
    'NEVER',
    CURRENT_TIMESTAMP
) ON CONFLICT (id) DO NOTHING;
