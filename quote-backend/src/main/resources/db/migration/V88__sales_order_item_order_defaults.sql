ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS sales_order_date DATE,
    ADD COLUMN IF NOT EXISTS customer_order_title VARCHAR(255);

UPDATE sales_order_item item
SET sales_order_date = header.sales_order_date,
    customer_order_title = header.customer_order_title
FROM sales_order_header header
WHERE item.sales_order_id = header.id
  AND (item.sales_order_date IS NULL OR item.customer_order_title IS NULL);

COMMENT ON COLUMN sales_order_item.sales_order_date IS 'Order-level sales date copied from sales_order_header and preserved on sales_order_item.';
COMMENT ON COLUMN sales_order_item.customer_order_title IS 'Order-level customer order title copied from sales_order_header and preserved on sales_order_item.';
