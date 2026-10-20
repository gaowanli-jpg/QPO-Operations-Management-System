-- Keep every execution allocation and its sales-order row in a one-to-one link.
ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS allocation_id INTEGER,
    ADD COLUMN IF NOT EXISTS allocation_type VARCHAR(40);

ALTER TABLE sales_order_item
    DROP CONSTRAINT IF EXISTS chk_sales_order_item_qty;
ALTER TABLE sales_order_item
    ADD CONSTRAINT chk_sales_order_item_qty CHECK (qty >= 0);

ALTER TABLE order_delivery_ticket_allocation
    DROP CONSTRAINT IF EXISTS chk_order_delivery_ticket_allocation_qty;
ALTER TABLE order_delivery_ticket_allocation
    ADD CONSTRAINT chk_order_delivery_ticket_allocation_qty CHECK (qty >= 0);

ALTER TABLE sales_order_item
    ADD CONSTRAINT fk_sales_order_item_allocation
        FOREIGN KEY (allocation_id) REFERENCES order_delivery_ticket_allocation(id);

ALTER TABLE sales_order_item
    ADD CONSTRAINT chk_sales_order_item_allocation_type
        CHECK (allocation_type IS NULL OR allocation_type IN (
            'PRODUCTION_REQUIRED', 'IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK'
        ));

CREATE UNIQUE INDEX uk_sales_order_item_allocation_id
    ON sales_order_item(allocation_id)
    WHERE allocation_id IS NOT NULL;
CREATE INDEX idx_sales_order_item_allocation_type
    ON sales_order_item(sales_order_id, allocation_type, id);

-- V57 normally created this row. The guarded repair also covers deployments
-- where the old WMS result was already split before the optional-WMS migration.
WITH pending_wms AS (
    SELECT DISTINCT ticket_id
    FROM wms_reservation_ticket
    WHERE status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED')
), missing_production AS (
    SELECT ticket.id,
           CASE WHEN EXISTS (
               SELECT 1
               FROM order_delivery_ticket_allocation locked
               WHERE locked.ticket_id = ticket.id
                 AND locked.allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK')
                 AND COALESCE(locked.status, 'ACTIVE') = 'ACTIVE'
                 AND COALESCE(locked.discarded, FALSE) = FALSE
           ) THEN 0 ELSE GREATEST(COALESCE(ticket.qty, 0), 0) END AS production_qty
    FROM order_delivery_ticket ticket
    WHERE COALESCE(ticket.discarded, FALSE) = FALSE
      AND NULLIF(BTRIM(ticket.material_no), '') IS NOT NULL
      AND COALESCE(ticket.mes_sync_status, 'PENDING') = 'SUCCESS'
      AND ticket.qty IS NOT NULL
      AND NOT EXISTS (
          SELECT 1
          FROM order_delivery_ticket_allocation production
          WHERE production.ticket_id = ticket.id
            AND production.allocation_type = 'PRODUCTION_REQUIRED'
            AND COALESCE(production.status, 'ACTIVE') = 'ACTIVE'
            AND COALESCE(production.discarded, FALSE) = FALSE
      )
      AND NOT EXISTS (
          SELECT 1
          FROM order_delivery_ticket_allocation mes_production
          WHERE mes_production.ticket_id = ticket.id
            AND mes_production.allocation_source = 'MES_MATERIAL'
      )
      AND NOT EXISTS (SELECT 1 FROM pending_wms WHERE pending_wms.ticket_id = ticket.id)
)
INSERT INTO order_delivery_ticket_allocation (
    ticket_id, rfq_id, item_id, source_type, source_order_id, source_item_id,
    allocation_source, allocation_type, qty, ticket_qty, material_no, order_type,
    item_type, part_no, version, rfq_no, customer_name, internal_project_no,
    customer_order_no, customer_project_no, material_name, engineering_material_code,
    engineering_material_name, engineering_material_type_code, engineering_material_type_name,
    engineering_material_category, material_shape, surface_treatment, remark, description,
    delivery_date, process_route, material_data, material_gross_weight, part_net_weight,
    source_payload, mes_production_status, status, created_at, updated_at
)
SELECT ticket.id, ticket.rfq_id, ticket.item_id, ticket.source_type, ticket.source_order_id,
       ticket.source_item_id, 'MES_MATERIAL', 'PRODUCTION_REQUIRED', missing_production.production_qty,
       ticket.qty, ticket.material_no, ticket.order_type, ticket.item_type, ticket.part_no,
       ticket.version, ticket.rfq_no, ticket.customer_name, ticket.internal_project_no,
       ticket.customer_order_no, ticket.customer_project_no, ticket.material_name,
       ticket.engineering_material_code, ticket.engineering_material_name,
       ticket.engineering_material_type_code, ticket.engineering_material_type_name,
       ticket.engineering_material_category, ticket.material_shape, ticket.surface_treatment,
       ticket.remark, ticket.description, ticket.delivery_date,
       COALESCE(ticket.process_route, '[]'::jsonb), COALESCE(ticket.material_data, '{}'::jsonb),
       ticket.material_gross_weight, ticket.part_net_weight,
       jsonb_build_object('source', 'V58_PRODUCTION_REPAIR', 'ticketId', ticket.id),
       'PENDING', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM missing_production
JOIN order_delivery_ticket ticket ON ticket.id = missing_production.id;

-- Direct sales-order releases may contain several ticket quantities from one
-- source item. Normalize them into ticket-specific sales-order rows before the
-- one-to-one allocation backfill, keeping any not-yet-released remainder.
WITH direct_ticket_rows AS (
    SELECT ticket.id AS ticket_id,
           ticket.source_item_id AS base_item_id,
           ticket.qty AS ticket_qty,
           source_item.sales_order_id,
           source_item.line_no AS base_line_no,
           source_item.qty AS original_qty,
           source_item.shipped_qty AS original_shipped_qty,
           ROW_NUMBER() OVER (PARTITION BY ticket.source_item_id ORDER BY ticket.id) AS ticket_number,
           SUM(ticket.qty) OVER (PARTITION BY ticket.source_item_id) AS released_ticket_qty,
           COALESCE(SUM(ticket.qty) OVER (
               PARTITION BY ticket.source_item_id ORDER BY ticket.id
               ROWS BETWEEN UNBOUNDED PRECEDING AND 1 PRECEDING
           ), 0) AS preceding_ticket_qty
    FROM order_delivery_ticket ticket
    JOIN sales_order_item source_item ON source_item.id = ticket.source_item_id
    WHERE ticket.source_type = 'SALES_ORDER'
      AND ticket.source_item_id IS NOT NULL
      AND ticket.qty > 0
      AND source_item.source_ticket_id IS NULL
), direct_sources AS (
    SELECT DISTINCT ON (base_item_id)
           base_item_id, sales_order_id, base_line_no, original_qty, original_shipped_qty, released_ticket_qty
    FROM direct_ticket_rows
    ORDER BY base_item_id, ticket_number
), split_rows AS (
    SELECT row.base_item_id,
           row.sales_order_id,
           row.base_line_no,
           row.ticket_id,
           row.ticket_qty AS split_qty,
           LEAST(GREATEST(COALESCE(row.original_shipped_qty, 0) - row.preceding_ticket_qty, 0), row.ticket_qty) AS shipped_qty,
           row.ticket_number AS split_order,
           FALSE AS is_remainder
    FROM direct_ticket_rows row
    WHERE row.ticket_number > 1
    UNION ALL
    SELECT source.base_item_id,
           source.sales_order_id,
           source.base_line_no,
           NULL::INTEGER AS ticket_id,
           GREATEST(COALESCE(source.original_qty, 0) - source.released_ticket_qty, 0) AS split_qty,
           GREATEST(COALESCE(source.original_shipped_qty, 0) - source.released_ticket_qty, 0) AS shipped_qty,
           2147483647 AS split_order,
           TRUE AS is_remainder
    FROM direct_sources source
    WHERE COALESCE(source.original_qty, 0) > source.released_ticket_qty
), numbered_split_rows AS (
    SELECT split.*,
           ROW_NUMBER() OVER (
               PARTITION BY split.sales_order_id
               ORDER BY split.base_line_no NULLS LAST, split.base_item_id, split.split_order
           ) AS line_sequence,
           order_max.max_line_no AS order_max_line_no
    FROM split_rows split
    JOIN (
        SELECT sales_order_id, COALESCE(MAX(line_no), 0) AS max_line_no
        FROM sales_order_item
        GROUP BY sales_order_id
    ) order_max ON order_max.sales_order_id = split.sales_order_id
)
INSERT INTO sales_order_item
SELECT (jsonb_populate_record(
    NULL::sales_order_item,
    to_jsonb(source_item) || jsonb_build_object(
        'id', nextval('sales_order_item_id_seq'),
        'line_no', COALESCE(split.order_max_line_no, 0) + split.line_sequence,
        'source_ticket_id', split.ticket_id,
        'allocation_id', NULL,
        'allocation_type', NULL,
        'qty', split.split_qty,
        'shipped_qty', split.shipped_qty,
        'unshipped_qty', GREATEST(split.split_qty - split.shipped_qty, 0),
        'released_qty', CASE WHEN split.is_remainder THEN 0 ELSE split.split_qty END,
        'remaining_qty', CASE WHEN split.is_remainder THEN split.split_qty ELSE 0 END,
        'tax_included_total_price', CASE WHEN source_item.tax_included_unit_price IS NULL THEN NULL
            ELSE source_item.tax_included_unit_price * split.split_qty END,
        'untaxed_total_price', CASE WHEN source_item.untaxed_unit_price IS NULL THEN NULL
            ELSE source_item.untaxed_unit_price * split.split_qty END,
        'status', CASE WHEN split.is_remainder THEN '部分已提单' ELSE source_item.status END,
        'created_at', CURRENT_TIMESTAMP,
        'updated_at', CURRENT_TIMESTAMP
    )
)).*
FROM numbered_split_rows split
JOIN sales_order_item source_item ON source_item.id = split.base_item_id;

UPDATE order_delivery_ticket ticket
SET source_item_id = split_item.id
FROM sales_order_item split_item
WHERE ticket.source_type = 'SALES_ORDER'
  AND split_item.source_ticket_id = ticket.id
  AND ticket.source_item_id IS DISTINCT FROM split_item.id;

WITH first_direct_ticket AS (
    SELECT DISTINCT ON (ticket.source_item_id)
           ticket.source_item_id AS item_id,
           ticket.id AS ticket_id,
           ticket.qty AS ticket_qty
    FROM order_delivery_ticket ticket
    WHERE ticket.source_type = 'SALES_ORDER'
      AND ticket.source_item_id IS NOT NULL
      AND ticket.qty > 0
    ORDER BY ticket.source_item_id, ticket.id
)
UPDATE sales_order_item source_item
SET source_ticket_id = first_ticket.ticket_id,
    qty = first_ticket.ticket_qty,
    shipped_qty = LEAST(COALESCE(source_item.shipped_qty, 0), first_ticket.ticket_qty),
    unshipped_qty = GREATEST(first_ticket.ticket_qty - LEAST(COALESCE(source_item.shipped_qty, 0), first_ticket.ticket_qty), 0),
    released_qty = first_ticket.ticket_qty,
    remaining_qty = 0,
    tax_included_total_price = CASE WHEN source_item.tax_included_unit_price IS NULL THEN NULL
        ELSE source_item.tax_included_unit_price * first_ticket.ticket_qty END,
    untaxed_total_price = CASE WHEN source_item.untaxed_unit_price IS NULL THEN NULL
        ELSE source_item.untaxed_unit_price * first_ticket.ticket_qty END,
    updated_at = CURRENT_TIMESTAMP
FROM first_direct_ticket first_ticket
WHERE source_item.id = first_ticket.item_id
  AND source_item.source_ticket_id IS NULL;

-- Bind the original sales-order row to the production allocation first. This
-- preserves the original row as the待产 row even when lock rows already exist.
WITH production_links AS (
    SELECT DISTINCT ON (alloc.id)
           alloc.id AS allocation_id,
           alloc.allocation_type,
           COALESCE(
               CASE WHEN ticket.source_type = 'SALES_ORDER' THEN direct_item.id END,
               rfq_item.id
           ) AS sales_order_item_id
    FROM order_delivery_ticket_allocation alloc
    JOIN order_delivery_ticket ticket ON ticket.id = alloc.ticket_id
    LEFT JOIN sales_order_item direct_item
      ON ticket.source_type = 'SALES_ORDER'
     AND direct_item.id = ticket.source_item_id
    LEFT JOIN sales_order_item rfq_item
      ON ticket.source_type = 'RFQ'
     AND rfq_item.source_ticket_id = ticket.id
     AND rfq_item.source_type = 'RFQ'
    WHERE alloc.allocation_type = 'PRODUCTION_REQUIRED'
      AND COALESCE(alloc.status, 'ACTIVE') = 'ACTIVE'
      AND COALESCE(alloc.discarded, FALSE) = FALSE
    ORDER BY alloc.id, COALESCE(
        CASE WHEN ticket.source_type = 'SALES_ORDER' THEN direct_item.id END,
        rfq_item.id
    )
)
UPDATE sales_order_item item
SET allocation_id = links.allocation_id,
    allocation_type = links.allocation_type,
    qty = alloc.qty,
    unshipped_qty = GREATEST(0, alloc.qty - COALESCE(item.shipped_qty, 0)),
    remaining_qty = GREATEST(0, alloc.qty - COALESCE(item.released_qty, 0)),
    updated_at = CURRENT_TIMESTAMP
FROM production_links links
JOIN order_delivery_ticket_allocation alloc ON alloc.id = links.allocation_id
WHERE item.id = links.sales_order_item_id
  AND links.sales_order_item_id IS NOT NULL
  AND item.allocation_id IS NULL;

-- Append one ordinary sales-order row for each positive historical lock row.
-- Zero production rows intentionally remain represented by the original item.
WITH split_rows AS (
    SELECT alloc.*, source_item.id AS source_sales_item_id,
           source_item.sales_order_id,
           ROW_NUMBER() OVER (
               PARTITION BY source_item.sales_order_id
               ORDER BY alloc.id
           ) AS split_sequence
    FROM order_delivery_ticket_allocation alloc
    JOIN sales_order_item source_item ON source_item.allocation_id = (
        SELECT production.id
        FROM order_delivery_ticket_allocation production
        WHERE production.ticket_id = alloc.ticket_id
          AND production.allocation_type = 'PRODUCTION_REQUIRED'
          AND COALESCE(production.status, 'ACTIVE') = 'ACTIVE'
          AND COALESCE(production.discarded, FALSE) = FALSE
        ORDER BY production.id
        LIMIT 1
    )
    WHERE alloc.allocation_type IN ('IN_STOCK_LOCK', 'IN_PRODUCTION_LOCK')
      AND alloc.qty > 0
      AND COALESCE(alloc.status, 'ACTIVE') = 'ACTIVE'
      AND COALESCE(alloc.discarded, FALSE) = FALSE
      AND NOT EXISTS (
          SELECT 1 FROM sales_order_item linked WHERE linked.allocation_id = alloc.id
      )
), next_lines AS (
    SELECT sales_order_id, COALESCE(MAX(line_no), 0) AS max_line_no
    FROM sales_order_item
    GROUP BY sales_order_id
)
INSERT INTO sales_order_item (
    sales_order_id, source_type, source_order_id, source_item_id, source_ticket_id,
    sales_order_date, line_no, part_no, description, qty, version, order_type,
    customer_order_no, customer_project_no, customer_order_title, internal_project_no,
    material_no, material_no_status, material_no_source, material_no_source_ticket_id,
    material_no_synced_at, material_no_message, customer_delivery_date, internal_delivery_date,
    material_name, engineering_material_code, engineering_material_name, engineering_material_type_code,
    engineering_material_type_name, engineering_material_category, material_shape, surface_treatment,
    tax_included_unit_price, untaxed_unit_price, tax_included_total_price, untaxed_total_price,
    shipped_qty, unshipped_qty, released_qty, remaining_qty, drawing_url, has_drawing,
    material_gross_weight, part_net_weight, quote_mode, assembly_fee, base_cost, item_type,
    material_data, process_route, fee_data, remark, status, allocation_id, allocation_type,
    created_at, updated_at
)
SELECT source_item.sales_order_id, source_item.source_type, source_item.source_order_id,
       source_item.source_item_id, NULL::INTEGER, source_item.sales_order_date,
       next_lines.max_line_no + split_rows.split_sequence, source_item.part_no,
       source_item.description, split_rows.qty, source_item.version, source_item.order_type,
       source_item.customer_order_no, source_item.customer_project_no, source_item.customer_order_title,
       source_item.internal_project_no, split_rows.material_no, source_item.material_no_status,
       source_item.material_no_source, source_item.material_no_source_ticket_id,
       source_item.material_no_synced_at, source_item.material_no_message,
       source_item.customer_delivery_date, source_item.internal_delivery_date,
       split_rows.material_name, split_rows.engineering_material_code, split_rows.engineering_material_name,
       split_rows.engineering_material_type_code, split_rows.engineering_material_type_name,
       split_rows.engineering_material_category, split_rows.material_shape, split_rows.surface_treatment,
       source_item.tax_included_unit_price, source_item.untaxed_unit_price,
       CASE WHEN source_item.tax_included_unit_price IS NULL THEN NULL
            ELSE source_item.tax_included_unit_price * split_rows.qty END,
       CASE WHEN source_item.untaxed_unit_price IS NULL THEN NULL
            ELSE source_item.untaxed_unit_price * split_rows.qty END,
       0, split_rows.qty, split_rows.qty, 0, source_item.drawing_url, source_item.has_drawing,
       split_rows.material_gross_weight, split_rows.part_net_weight, source_item.quote_mode,
       source_item.assembly_fee, source_item.base_cost, split_rows.item_type, split_rows.material_data,
       split_rows.process_route, source_item.fee_data, split_rows.remark, source_item.status,
       split_rows.id, split_rows.allocation_type, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM split_rows
JOIN next_lines ON next_lines.sales_order_id = split_rows.sales_order_id
JOIN sales_order_item source_item ON source_item.id = split_rows.source_sales_item_id;

UPDATE sales_order_header header
SET total_item_count = item_counts.total_item_count
FROM (
    SELECT sales_order_id, COUNT(*)::INTEGER AS total_item_count
    FROM sales_order_item
    GROUP BY sales_order_id
) item_counts
WHERE header.id = item_counts.sales_order_id
  AND header.total_item_count IS DISTINCT FROM item_counts.total_item_count;

COMMENT ON COLUMN sales_order_item.allocation_id IS 'Current one-to-one execution allocation row';
COMMENT ON COLUMN sales_order_item.allocation_type IS 'Allocation split type mirrored from order_delivery_ticket_allocation';
