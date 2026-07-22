ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS process_route JSONB,
    ADD COLUMN IF NOT EXISTS material_data JSONB,
    ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4),
    ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4),
    ADD COLUMN IF NOT EXISTS engineering_material_name VARCHAR(255);

UPDATE order_delivery_ticket ticket
SET description = COALESCE(NULLIF(ticket.description, ''), item.description),
    process_route = COALESCE(ticket.process_route, item.process_route),
    material_data = COALESCE(ticket.material_data, item.material_data),
    material_gross_weight = COALESCE(ticket.material_gross_weight, item.material_gross_weight),
    part_net_weight = COALESCE(ticket.part_net_weight, item.part_net_weight, 0),
    engineering_material_name = COALESCE(
        NULLIF(ticket.engineering_material_name, ''),
        NULLIF(item.engineering_material_name, ''),
        NULLIF(TRIM(item.material_data ->> 'name'), '')
    )
FROM item_quote_version item
WHERE ticket.item_id = item.id
  AND (
    ticket.description IS NULL OR ticket.description = ''
    OR ticket.process_route IS NULL
    OR ticket.material_data IS NULL
    OR ticket.material_gross_weight IS NULL
    OR ticket.part_net_weight IS NULL
    OR ticket.engineering_material_name IS NULL
    OR ticket.engineering_material_name = ''
  );

UPDATE order_delivery_ticket
SET part_net_weight = 0
WHERE part_net_weight IS NULL;

DROP TABLE IF EXISTS order_process_sheet;
