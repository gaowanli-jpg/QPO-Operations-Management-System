-- Track MES master data hard deletes separately from MES status disables.

SET search_path TO "${flyway:defaultSchema}";

ALTER TABLE mes_master_data_sync_run ADD COLUMN IF NOT EXISTS deleted_count INTEGER NOT NULL DEFAULT 0;
