ALTER TABLE order_process_sheet ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4);
ALTER TABLE order_process_sheet ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4) DEFAULT 0;

UPDATE order_process_sheet sheet
SET material_gross_weight = item.material_gross_weight,
    part_net_weight = COALESCE(item.part_net_weight, 0)
FROM item_quote_version item
WHERE sheet.item_id = item.id
  AND (sheet.material_gross_weight IS NULL OR sheet.part_net_weight IS NULL);

UPDATE order_process_sheet SET part_net_weight = 0 WHERE part_net_weight IS NULL;
