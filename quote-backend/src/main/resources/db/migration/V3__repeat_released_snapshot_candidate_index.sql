-- Rebuild repeat-source partial index so empty JSON snapshots are not eligible candidates.
DROP INDEX IF EXISTS idx_item_quote_version_repeat_released_key;

CREATE INDEX idx_item_quote_version_repeat_released_key
    ON item_quote_version USING btree (part_no, version, id DESC)
    WHERE (((status)::text = '已提单'::text)
        AND (tax_included_unit_price IS NOT NULL)
        AND (material_data IS NOT NULL)
        AND (material_data <> '{}'::jsonb)
        AND (process_route IS NOT NULL)
        AND (process_route <> '[]'::jsonb));
