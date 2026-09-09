-- Normalize sales order item line numbers to QPO system row order.
-- The customer Excel "sequence" column is no longer preserved. Existing rows are
-- renumbered by persisted insert order so downstream saleOrderItemNo/DocLineNo
-- values are 1..n within each sales order.

WITH ordered AS (
    SELECT
        id,
        CAST(ROW_NUMBER() OVER (
            PARTITION BY sales_order_id
            ORDER BY id
        ) AS INTEGER) AS new_line_no
    FROM sales_order_item
)
UPDATE sales_order_item soi
SET line_no = ordered.new_line_no,
    updated_at = CURRENT_TIMESTAMP
FROM ordered
WHERE soi.id = ordered.id
  AND soi.line_no IS DISTINCT FROM ordered.new_line_no;

ALTER TABLE sales_order_item
    ALTER COLUMN line_no SET NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_item_order_line_no
    ON sales_order_item (sales_order_id, line_no);
