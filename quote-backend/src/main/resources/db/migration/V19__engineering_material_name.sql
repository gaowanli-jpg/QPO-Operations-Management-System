ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS engineering_material_name VARCHAR(255);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS engineering_material_name VARCHAR(255);
ALTER TABLE order_process_sheet ADD COLUMN IF NOT EXISTS engineering_material_name VARCHAR(255);

UPDATE item_quote_version
SET engineering_material_name = NULLIF(TRIM(material_data ->> 'name'), '')
WHERE engineering_material_name IS NULL
  AND material_data IS NOT NULL
  AND NULLIF(TRIM(material_data ->> 'name'), '') IS NOT NULL;

UPDATE order_delivery_ticket ticket
SET engineering_material_name = item.engineering_material_name
FROM item_quote_version item
WHERE ticket.item_id = item.id
  AND ticket.engineering_material_name IS NULL
  AND item.engineering_material_name IS NOT NULL;

UPDATE order_process_sheet sheet
SET engineering_material_name = item.engineering_material_name
FROM item_quote_version item
WHERE sheet.item_id = item.id
  AND sheet.engineering_material_name IS NULL
  AND item.engineering_material_name IS NOT NULL;
