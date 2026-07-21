ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS rfq_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS customer_name VARCHAR(255);

UPDATE order_delivery_ticket ticket
SET rfq_no = COALESCE(NULLIF(TRIM(ticket.rfq_no), ''), rfq.rfq_no),
    customer_name = COALESCE(NULLIF(TRIM(ticket.customer_name), ''), rfq.customer_name)
FROM order_rfq rfq
WHERE rfq.id = ticket.rfq_id
  AND (
      ticket.rfq_no IS NULL
      OR TRIM(ticket.rfq_no) = ''
      OR ticket.customer_name IS NULL
      OR TRIM(ticket.customer_name) = ''
  );

ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS rfq_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS customer_project_no VARCHAR(100),
    ADD COLUMN IF NOT EXISTS material_name VARCHAR(100),
    ADD COLUMN IF NOT EXISTS engineering_material_name VARCHAR(255),
    ADD COLUMN IF NOT EXISTS surface_treatment VARCHAR(100),
    ADD COLUMN IF NOT EXISTS description TEXT,
    ADD COLUMN IF NOT EXISTS delivery_date DATE,
    ADD COLUMN IF NOT EXISTS process_route JSONB,
    ADD COLUMN IF NOT EXISTS material_data JSONB,
    ADD COLUMN IF NOT EXISTS material_gross_weight NUMERIC(14, 4),
    ADD COLUMN IF NOT EXISTS part_net_weight NUMERIC(14, 4);

ALTER TABLE order_delivery_ticket_allocation
    ALTER COLUMN process_route TYPE JSONB USING NULLIF(process_route::TEXT, '')::JSONB,
    ALTER COLUMN material_data TYPE JSONB USING NULLIF(material_data::TEXT, '')::JSONB;

UPDATE order_delivery_ticket_allocation alloc
SET rfq_no = COALESCE(alloc.rfq_no, ticket.rfq_no, rfq.rfq_no),
    customer_name = COALESCE(alloc.customer_name, ticket.customer_name, rfq.customer_name),
    customer_order_no = COALESCE(alloc.customer_order_no, ticket.customer_order_no),
    customer_project_no = COALESCE(alloc.customer_project_no, ticket.customer_project_no),
    material_name = COALESCE(alloc.material_name, ticket.material_name),
    engineering_material_name = COALESCE(alloc.engineering_material_name, ticket.engineering_material_name),
    surface_treatment = COALESCE(alloc.surface_treatment, ticket.surface_treatment),
    description = COALESCE(alloc.description, ticket.description),
    delivery_date = COALESCE(alloc.delivery_date, ticket.delivery_date),
    process_route = COALESCE(alloc.process_route, ticket.process_route),
    material_data = COALESCE(alloc.material_data, ticket.material_data),
    material_gross_weight = COALESCE(alloc.material_gross_weight, ticket.material_gross_weight),
    part_net_weight = COALESCE(alloc.part_net_weight, ticket.part_net_weight)
FROM order_delivery_ticket ticket
LEFT JOIN order_rfq rfq ON rfq.id = ticket.rfq_id
WHERE alloc.ticket_id = ticket.id
  AND (
      alloc.rfq_no IS NULL
      OR alloc.customer_name IS NULL
      OR alloc.customer_order_no IS NULL
      OR alloc.customer_project_no IS NULL
      OR alloc.material_name IS NULL
      OR alloc.engineering_material_name IS NULL
      OR alloc.surface_treatment IS NULL
      OR alloc.description IS NULL
      OR alloc.delivery_date IS NULL
      OR alloc.process_route IS NULL
      OR alloc.material_data IS NULL
      OR alloc.material_gross_weight IS NULL
      OR alloc.part_net_weight IS NULL
  );

UPDATE order_delivery_ticket_allocation
SET process_route = COALESCE(process_route, '[]'::jsonb),
    material_data = COALESCE(material_data, '{}'::jsonb);

ALTER TABLE order_delivery_ticket_allocation
    ALTER COLUMN process_route SET DEFAULT '[]'::jsonb,
    ALTER COLUMN material_data SET DEFAULT '{}'::jsonb;
