CREATE INDEX IF NOT EXISTS idx_item_quote_version_repeat_priced_snapshot_key
    ON item_quote_version USING btree (part_no, version, id DESC)
    WHERE status IN ('已核价', '已报价', '部分已提单', '已生成提单', '已提单')
      AND tax_included_unit_price IS NOT NULL
      AND material_data IS NOT NULL
      AND material_data <> '{}'::jsonb
      AND process_route IS NOT NULL
      AND process_route <> '[]'::jsonb;
