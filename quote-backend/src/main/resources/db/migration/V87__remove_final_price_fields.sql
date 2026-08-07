DO $$
DECLARE
    conflict_count BIGINT;
BEGIN
    SELECT COUNT(*)
    INTO conflict_count
    FROM item_quote_version
    WHERE tax_included_unit_price IS NOT NULL
      AND final_unit_price IS NOT NULL
      AND tax_included_unit_price <> final_unit_price;
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'item_quote_version contains conflicting unit prices: % row(s)', conflict_count;
    END IF;

    SELECT COUNT(*)
    INTO conflict_count
    FROM item_quote_version
    WHERE tax_included_total_price IS NOT NULL
      AND final_total_price IS NOT NULL
      AND tax_included_total_price <> final_total_price;
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'item_quote_version contains conflicting total prices: % row(s)', conflict_count;
    END IF;

    SELECT COUNT(*)
    INTO conflict_count
    FROM sales_order_item
    WHERE tax_included_unit_price IS NOT NULL
      AND final_unit_price IS NOT NULL
      AND tax_included_unit_price <> final_unit_price;
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'sales_order_item contains conflicting unit prices: % row(s)', conflict_count;
    END IF;

    SELECT COUNT(*)
    INTO conflict_count
    FROM sales_order_item
    WHERE tax_included_total_price IS NOT NULL
      AND final_total_price IS NOT NULL
      AND tax_included_total_price <> final_total_price;
    IF conflict_count > 0 THEN
        RAISE EXCEPTION 'sales_order_item contains conflicting total prices: % row(s)', conflict_count;
    END IF;
END $$;

UPDATE item_quote_version
SET tax_included_unit_price = final_unit_price
WHERE tax_included_unit_price IS NULL
  AND final_unit_price IS NOT NULL;

UPDATE item_quote_version
SET tax_included_total_price = final_total_price
WHERE tax_included_total_price IS NULL
  AND final_total_price IS NOT NULL;

UPDATE sales_order_item
SET tax_included_unit_price = final_unit_price
WHERE tax_included_unit_price IS NULL
  AND final_unit_price IS NOT NULL;

UPDATE sales_order_item
SET tax_included_total_price = final_total_price
WHERE tax_included_total_price IS NULL
  AND final_total_price IS NOT NULL;

UPDATE item_quote_version
SET tax_included_total_price = ROUND(tax_included_unit_price * qty, 2)
WHERE tax_included_total_price IS NULL
  AND tax_included_unit_price IS NOT NULL
  AND qty IS NOT NULL;

UPDATE sales_order_item
SET tax_included_total_price = ROUND(tax_included_unit_price * qty, 2)
WHERE tax_included_total_price IS NULL
  AND tax_included_unit_price IS NOT NULL
  AND qty IS NOT NULL;

DROP INDEX IF EXISTS idx_item_quote_version_repeat_released_key;

CREATE INDEX idx_item_quote_version_repeat_released_key
    ON item_quote_version(part_no, version, id DESC)
    WHERE status = '已提单'
      AND tax_included_unit_price IS NOT NULL
      AND material_data IS NOT NULL
      AND process_route IS NOT NULL;

ALTER TABLE item_quote_version
    DROP COLUMN IF EXISTS final_unit_price,
    DROP COLUMN IF EXISTS final_total_price;

ALTER TABLE sales_order_item
    DROP COLUMN IF EXISTS final_unit_price,
    DROP COLUMN IF EXISTS final_total_price;
