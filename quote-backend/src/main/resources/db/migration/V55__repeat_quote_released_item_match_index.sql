CREATE INDEX IF NOT EXISTS idx_item_quote_version_repeat_released_key
    ON item_quote_version(part_no, version, id DESC)
    WHERE status = '已提单'
      AND final_unit_price IS NOT NULL
      AND material_data IS NOT NULL
      AND process_route IS NOT NULL;
