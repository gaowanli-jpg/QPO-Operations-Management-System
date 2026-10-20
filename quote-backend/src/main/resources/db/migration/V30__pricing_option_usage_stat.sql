ALTER TABLE dict_extra_fee_item
    ADD COLUMN IF NOT EXISTS fee_code VARCHAR(80);

UPDATE dict_extra_fee_item
SET fee_code = CASE fee_name
    WHEN 'EDM' THEN 'EDM'
    WHEN '热处理' THEN 'HEAT_TREATMENT'
    WHEN '表面处理' THEN 'SURFACE_TREATMENT'
    WHEN 'QA' THEN 'QA'
    WHEN '焊钳工\焊缝处理\测漏\校平' THEN 'WELDING_FINISH_LEAK_LEVELING'
    ELSE fee_code
END
WHERE fee_code IS NULL
  AND fee_name IN ('EDM', '热处理', '表面处理', 'QA', '焊钳工\焊缝处理\测漏\校平');

CREATE UNIQUE INDEX IF NOT EXISTS uk_dict_extra_fee_item_fee_code
    ON dict_extra_fee_item (fee_code)
    WHERE fee_code IS NOT NULL AND BTRIM(fee_code) <> '';

CREATE TABLE IF NOT EXISTS pricing_option_usage_stat (
    id BIGSERIAL PRIMARY KEY,
    option_type VARCHAR(40) NOT NULL,
    option_key VARCHAR(120) NOT NULL,
    usage_count BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_pricing_option_usage_stat_type_key UNIQUE (option_type, option_key),
    CONSTRAINT chk_pricing_option_usage_stat_count CHECK (usage_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_pricing_option_usage_stat_sort
    ON pricing_option_usage_stat (option_type, usage_count DESC, option_key);
