-- MES master data async submit guards and daily fixed-time schedule mode.

SET search_path TO "${flyway:defaultSchema}";

ALTER TABLE mes_master_data_sync_schedule_config ADD COLUMN IF NOT EXISTS schedule_mode VARCHAR(30) NOT NULL DEFAULT 'FIXED_DELAY';

ALTER TABLE mes_master_data_sync_schedule_config ADD COLUMN IF NOT EXISTS daily_run_time TIME NOT NULL DEFAULT '02:00:00';

DO $$
BEGIN
    ALTER TABLE mes_master_data_sync_schedule_config
        ADD CONSTRAINT chk_mes_master_data_sync_schedule_mode
            CHECK (schedule_mode IN ('FIXED_DELAY', 'DAILY_TIME'));
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

CREATE UNIQUE INDEX IF NOT EXISTS uk_mes_master_data_sync_run_active_type
    ON mes_master_data_sync_run (sync_type)
    WHERE status = 'RUNNING';
