ALTER TABLE dict_process_detail ADD COLUMN IF NOT EXISTS process_code VARCHAR(80);

DELETE FROM dict_process_arrangement_item
WHERE source_type = 'process';

DELETE FROM dict_process_detail;

DELETE FROM pricing_option_usage_stat
WHERE option_type = 'PROCESS_CATEGORY';

ALTER TABLE dict_process_detail
    DROP CONSTRAINT IF EXISTS uk_dict_process_detail_category_name;

ALTER TABLE dict_process_detail
    DROP CONSTRAINT IF EXISTS dict_process_detail_category_id_fkey;

ALTER TABLE dict_process_detail
    ALTER COLUMN category_id DROP NOT NULL;

ALTER TABLE dict_process_detail
    ALTER COLUMN hourly_rate SET DEFAULT 0;

UPDATE dict_process_detail
SET hourly_rate = 0
WHERE hourly_rate IS NULL;

ALTER TABLE dict_process_detail
    ALTER COLUMN hourly_rate SET NOT NULL;

ALTER TABLE dict_process_detail
    ALTER COLUMN process_code SET NOT NULL;

ALTER TABLE dict_process_detail
    DROP CONSTRAINT IF EXISTS chk_dict_process_detail_process_code;

ALTER TABLE dict_process_detail
    ADD CONSTRAINT chk_dict_process_detail_process_code
        CHECK (process_code ~ '^[A-Z0-9_-]{1,80}$');

ALTER TABLE dict_process_detail
    DROP CONSTRAINT IF EXISTS chk_dict_process_detail_hourly_rate;

ALTER TABLE dict_process_detail
    ADD CONSTRAINT chk_dict_process_detail_hourly_rate
        CHECK (hourly_rate >= 0);

DROP INDEX IF EXISTS idx_dict_process_detail_category_enabled_sort;

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_process_detail_process_code
    ON dict_process_detail (process_code);

CREATE INDEX IF NOT EXISTS idx_dict_process_detail_enabled_sort
    ON dict_process_detail (enabled, sort_no, id);

CREATE INDEX IF NOT EXISTS idx_dict_process_detail_code_name
    ON dict_process_detail (process_code, detail_name, id);

COMMENT ON COLUMN dict_process_detail.process_code IS 'QPO维护的具体工艺编码；工时工艺推荐计数、核价快照和MES生产下发使用该编码';
COMMENT ON COLUMN dict_process_detail.category_id IS '历史兼容字段；具体工艺不再依赖MES工艺大类，可为空';
COMMENT ON COLUMN dict_process_detail.hourly_rate IS 'QPO维护的具体工艺实际核价小时价格；不再继承工艺大类价格，0表示明确按0元/小时核价';
