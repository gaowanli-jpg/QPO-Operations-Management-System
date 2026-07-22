UPDATE order_delivery_ticket
SET process_route = '[]'::jsonb
WHERE process_route IS NULL;

UPDATE order_delivery_ticket
SET material_data = '{}'::jsonb
WHERE material_data IS NULL;

ALTER TABLE order_delivery_ticket
    ALTER COLUMN process_route SET DEFAULT '[]'::jsonb,
    ALTER COLUMN material_data SET DEFAULT '{}'::jsonb;
