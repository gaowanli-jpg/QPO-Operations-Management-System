ALTER TABLE item_quote_version ADD COLUMN IF NOT EXISTS material_shape VARCHAR(30);
ALTER TABLE sales_order_item ADD COLUMN IF NOT EXISTS material_shape VARCHAR(30);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS material_shape VARCHAR(30);
ALTER TABLE order_delivery_ticket_allocation ADD COLUMN IF NOT EXISTS material_shape VARCHAR(30);

UPDATE item_quote_version
SET material_shape = NULLIF(TRIM(material_data ->> 'shape'), '')
WHERE (material_shape IS NULL OR TRIM(material_shape) = '')
  AND material_data IS NOT NULL
  AND NULLIF(TRIM(material_data ->> 'shape'), '') IS NOT NULL;

UPDATE sales_order_item
SET material_shape = NULLIF(TRIM(material_data ->> 'shape'), '')
WHERE (material_shape IS NULL OR TRIM(material_shape) = '')
  AND material_data IS NOT NULL
  AND NULLIF(TRIM(material_data ->> 'shape'), '') IS NOT NULL;

UPDATE order_delivery_ticket
SET material_shape = NULLIF(TRIM(material_data ->> 'shape'), '')
WHERE (material_shape IS NULL OR TRIM(material_shape) = '')
  AND material_data IS NOT NULL
  AND NULLIF(TRIM(material_data ->> 'shape'), '') IS NOT NULL;

UPDATE order_delivery_ticket ticket
SET material_shape = COALESCE(NULLIF(TRIM(item.material_shape), ''), NULLIF(TRIM(item.material_data ->> 'shape'), ''))
FROM item_quote_version item
WHERE ticket.item_id = item.id
  AND (ticket.material_shape IS NULL OR TRIM(ticket.material_shape) = '')
  AND COALESCE(NULLIF(TRIM(item.material_shape), ''), NULLIF(TRIM(item.material_data ->> 'shape'), '')) IS NOT NULL;

UPDATE order_delivery_ticket ticket
SET material_shape = COALESCE(NULLIF(TRIM(item.material_shape), ''), NULLIF(TRIM(item.material_data ->> 'shape'), ''))
FROM sales_order_item item
WHERE ticket.source_type = 'SALES_ORDER'
  AND ticket.source_item_id = item.id
  AND (ticket.material_shape IS NULL OR TRIM(ticket.material_shape) = '')
  AND COALESCE(NULLIF(TRIM(item.material_shape), ''), NULLIF(TRIM(item.material_data ->> 'shape'), '')) IS NOT NULL;

UPDATE order_delivery_ticket_allocation
SET material_shape = NULLIF(TRIM(material_data ->> 'shape'), '')
WHERE (material_shape IS NULL OR TRIM(material_shape) = '')
  AND material_data IS NOT NULL
  AND NULLIF(TRIM(material_data ->> 'shape'), '') IS NOT NULL;

UPDATE order_delivery_ticket_allocation alloc
SET material_shape = ticket.material_shape
FROM order_delivery_ticket ticket
WHERE alloc.ticket_id = ticket.id
  AND (alloc.material_shape IS NULL OR TRIM(alloc.material_shape) = '')
  AND ticket.material_shape IS NOT NULL
  AND TRIM(ticket.material_shape) <> '';

COMMENT ON COLUMN item_quote_version.material_shape IS 'Material shape snapshot copied from material_data.shape, for example 板材 / 棒材 / 管材.';
COMMENT ON COLUMN sales_order_item.material_shape IS 'Material shape snapshot copied from material_data.shape, for example 板材 / 棒材 / 管材.';
COMMENT ON COLUMN order_delivery_ticket.material_shape IS 'Material shape snapshot copied from source item material_data.shape, for example 板材 / 棒材 / 管材.';
COMMENT ON COLUMN order_delivery_ticket_allocation.material_shape IS 'Material shape snapshot copied from delivered ticket material_data.shape, for example 板材 / 棒材 / 管材.';
