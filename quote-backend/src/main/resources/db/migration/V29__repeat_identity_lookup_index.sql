CREATE INDEX IF NOT EXISTS idx_item_quote_version_part_version_latest
    ON item_quote_version USING btree (part_no, version, id DESC);
