ALTER TABLE dict_process_detail
    ADD COLUMN IF NOT EXISTS hourly_rate NUMERIC(18, 6);

COMMENT ON COLUMN dict_process_detail.hourly_rate IS '具体工艺小时价格；为空时继承工艺大类小时价格，0 表示明确为 0 元/小时';
