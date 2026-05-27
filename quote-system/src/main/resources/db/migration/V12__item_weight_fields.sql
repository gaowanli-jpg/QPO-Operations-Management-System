ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4);
ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4) DEFAULT 0;
UPDATE item_quote_version SET part_net_weight = 0 WHERE part_net_weight IS NULL;
