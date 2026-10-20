-- QPO MES sales order demand sync preflight check.
-- 阻断上线：如果本 SQL 返回任何行，说明 sales_order_item.line_no 或 material_no 不满足 MES 同步契约。
-- line_no 必须是同一销售订单内按 sales_order_item.id 生成的 1..n 系统顺序号，用于稳定返回 saleOrderItemNo。
-- material_no 用于 MES 匹配物料编码。
-- 必须先补齐 line_no / material_no，并执行 V51 历史归一迁移，再开放 /api/integrations/sales-orders。

WITH numbered AS (
    SELECT
        soi.sales_order_id,
        soi.id AS sales_order_item_id,
        soi.line_no,
        ROW_NUMBER() OVER (
            PARTITION BY soi.sales_order_id
            ORDER BY soi.id
        ) AS expected_line_no,
        soi.material_no,
        soi.part_no,
        soi.version,
        soi.created_at,
        soi.updated_at
    FROM sales_order_item soi
)
SELECT
    numbered.sales_order_id,
    so.sales_order_no,
    numbered.sales_order_item_id,
    numbered.line_no,
    numbered.expected_line_no,
    numbered.material_no,
    numbered.part_no,
    numbered.version,
    numbered.created_at,
    numbered.updated_at
FROM numbered
LEFT JOIN sales_order_header so ON so.id = numbered.sales_order_id
WHERE numbered.line_no IS NULL
   OR numbered.line_no <= 0
   OR numbered.line_no <> numbered.expected_line_no
   OR NULLIF(BTRIM(numbered.material_no), '') IS NULL
ORDER BY numbered.sales_order_id ASC, numbered.sales_order_item_id ASC;

-- 补齐 line_no 和 material_no 后重新执行本 SQL，必须返回 0 行。
