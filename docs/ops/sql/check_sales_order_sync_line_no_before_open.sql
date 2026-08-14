-- QPO sales order external sync preflight check.
-- 阻断上线：如果本 SQL 返回任何行，说明 sales_order_item.line_no 为空，
-- 外部同步无法稳定返回 saleOrderItemNo；必须先补齐 line_no，再开放 /api/integrations/sales-orders。

SELECT
    soi.sales_order_id,
    so.sales_order_no,
    soi.id AS sales_order_item_id,
    soi.part_no,
    soi.version,
    soi.created_at,
    soi.updated_at
FROM sales_order_item soi
LEFT JOIN sales_order_header so ON so.id = soi.sales_order_id
WHERE soi.line_no IS NULL
ORDER BY soi.sales_order_id ASC, soi.id ASC;

-- 补齐 line_no 后重新执行本 SQL，必须返回 0 行。
