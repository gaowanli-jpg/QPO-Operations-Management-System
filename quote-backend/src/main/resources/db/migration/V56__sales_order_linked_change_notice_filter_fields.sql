ALTER TABLE sales_order_linked_change_notice
    ADD COLUMN customer_order_no VARCHAR(255),
    ADD COLUMN internal_project_no VARCHAR(255);

UPDATE sales_order_linked_change_notice notice
SET customer_order_no = COALESCE(NULLIF(BTRIM(item.customer_order_no), ''), NULLIF(BTRIM(header.customer_order_no), '')),
    internal_project_no = COALESCE(NULLIF(BTRIM(item.internal_project_no), ''), NULLIF(BTRIM(header.internal_project_no), ''))
FROM sales_order_item item
JOIN sales_order_header header ON header.id = item.sales_order_id
WHERE item.id = notice.sales_order_item_id;

CREATE INDEX idx_linked_change_customer_order
    ON sales_order_linked_change_notice(customer_order_no, linked_discarded_at DESC, id DESC);

CREATE INDEX idx_linked_change_internal_project
    ON sales_order_linked_change_notice(internal_project_no, linked_discarded_at DESC, id DESC);
