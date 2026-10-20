# 按销售订单号批量废弃已提零件

本文档用于在当前系统尚未提供“已提零件批量废弃”按钮时，通过数据库脚本按 **销售订单号** 批量废弃一个销售订单对应的已提零件。

## 适用场景

- 需要废弃某一整张销售订单对应的已提零件。
- 已知目标销售订单号，例如 `SOQPOxxxx-001`。
- 只希望影响 **已提零件模块** 的记录，不删除业务数据。

## 涉及表

已提零件主表是：

```sql
order_delivery_ticket
```

废弃相关字段是：

```sql
discarded       -- 是否废弃
discarded_at    -- 废弃时间
discarded_by    -- 废弃操作人用户 ID，可为空
discard_reason  -- 废弃原因
restored_at     -- 恢复时间
restored_by     -- 恢复操作人用户 ID
restore_reason  -- 恢复原因
```

销售订单相关表是：

```sql
sales_order_header -- 销售订单头，包含 sales_order_no
sales_order_item   -- 销售订单零件明细
```

`order_delivery_ticket` 本身没有 `sales_order_no` 字段，所以需要通过 `sales_order_header.sales_order_no` 反查。

## 重要注意事项

- 直接执行 SQL 不会写系统操作日志；如果需要完整审计，后续应开发正式批量废弃按钮。
- 系统单行废弃已提零件时，会禁止废弃 `mes_sync_status = 'CALLBACK_PENDING'` 的行。本文档脚本也保留这个限制，避免废弃 MES 待回调中的记录。
- 本脚本默认只更新 `order_delivery_ticket`，即已提零件主表。
- 如果已提零件已经产生锁库 / 待产分配，分配表是 `order_delivery_ticket_allocation`。是否同时废弃 allocation 要另行确认，不建议默认级联。
- 执行前建议先备份数据库，至少先运行预览 SQL 确认命中记录。

## Docker 进入数据库

在部署目录执行：

```bash
docker compose --env-file erp.env exec -T postgres sh -lc 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -v ON_ERROR_STOP=1'
```

进入 `psql` 后，再粘贴下面 SQL。

## 1. 预览要废弃的已提零件

先把 `这里替换成销售订单号` 改成目标销售订单号。

```sql
WITH target_order AS (
    SELECT id, sales_order_no
    FROM sales_order_header
    WHERE sales_order_no = '这里替换成销售订单号'
),
target_tickets AS (
    SELECT DISTINCT t.*
    FROM order_delivery_ticket t
    JOIN target_order so ON
        (
            t.source_type = 'SALES_ORDER'
            AND (
                t.source_order_id = so.id
                OR EXISTS (
                    SELECT 1
                    FROM sales_order_item soi
                    WHERE soi.sales_order_id = so.id
                      AND soi.id = t.source_item_id
                )
            )
        )
        OR EXISTS (
            SELECT 1
            FROM sales_order_item soi
            WHERE soi.sales_order_id = so.id
              AND soi.source_ticket_id = t.id
        )
)
SELECT
    id,
    source_type,
    source_order_id,
    source_item_id,
    part_no,
    version,
    qty,
    material_no,
    mes_sync_status,
    wms_allocate_status,
    discarded,
    discard_reason
FROM target_tickets
ORDER BY id;
```

如果这里没有查到记录，先检查：

```sql
SELECT id, sales_order_no, status, u9_sync_status
FROM sales_order_header
WHERE sales_order_no = '这里替换成销售订单号';
```

## 2. 查看待回调行

这些行默认不会被废弃，需要等 MES 回调结束后再处理。

```sql
WITH target_order AS (
    SELECT id, sales_order_no
    FROM sales_order_header
    WHERE sales_order_no = '这里替换成销售订单号'
),
target_tickets AS (
    SELECT DISTINCT t.*
    FROM order_delivery_ticket t
    JOIN target_order so ON
        (
            t.source_type = 'SALES_ORDER'
            AND (
                t.source_order_id = so.id
                OR EXISTS (
                    SELECT 1
                    FROM sales_order_item soi
                    WHERE soi.sales_order_id = so.id
                      AND soi.id = t.source_item_id
                )
            )
        )
        OR EXISTS (
            SELECT 1
            FROM sales_order_item soi
            WHERE soi.sales_order_id = so.id
              AND soi.source_ticket_id = t.id
        )
)
SELECT id, part_no, version, mes_sync_status, mes_request_id
FROM target_tickets
WHERE COALESCE(mes_sync_status, '') = 'CALLBACK_PENDING'
ORDER BY id;
```

## 3. 执行批量废弃

确认预览结果无误后执行。默认废弃原因是 `按销售订单整单批量废弃`，可以按实际情况修改。

```sql
BEGIN;

WITH target_order AS (
    SELECT id, sales_order_no
    FROM sales_order_header
    WHERE sales_order_no = '这里替换成销售订单号'
),
target_tickets AS (
    SELECT DISTINCT t.id
    FROM order_delivery_ticket t
    JOIN target_order so ON
        (
            t.source_type = 'SALES_ORDER'
            AND (
                t.source_order_id = so.id
                OR EXISTS (
                    SELECT 1
                    FROM sales_order_item soi
                    WHERE soi.sales_order_id = so.id
                      AND soi.id = t.source_item_id
                )
            )
        )
        OR EXISTS (
            SELECT 1
            FROM sales_order_item soi
            WHERE soi.sales_order_id = so.id
              AND soi.source_ticket_id = t.id
        )
),
admin_user AS (
    SELECT id::bigint AS user_id
    FROM erp_user
    WHERE username = 'admin'
    LIMIT 1
)
UPDATE order_delivery_ticket t
SET
    discarded = true,
    discarded_at = CURRENT_TIMESTAMP,
    discarded_by = (SELECT user_id FROM admin_user),
    discard_reason = '按销售订单整单批量废弃',
    restored_at = NULL,
    restored_by = NULL,
    restore_reason = NULL
WHERE t.id IN (SELECT id FROM target_tickets)
  AND COALESCE(t.discarded, false) = false
  AND COALESCE(t.mes_sync_status, '') <> 'CALLBACK_PENDING'
RETURNING
    t.id,
    t.part_no,
    t.version,
    t.qty,
    t.mes_sync_status,
    t.wms_allocate_status,
    t.discarded,
    t.discarded_at,
    t.discard_reason;

COMMIT;
```

## 4. 执行后复查

```sql
WITH target_order AS (
    SELECT id, sales_order_no
    FROM sales_order_header
    WHERE sales_order_no = '这里替换成销售订单号'
),
target_tickets AS (
    SELECT DISTINCT t.*
    FROM order_delivery_ticket t
    JOIN target_order so ON
        (
            t.source_type = 'SALES_ORDER'
            AND (
                t.source_order_id = so.id
                OR EXISTS (
                    SELECT 1
                    FROM sales_order_item soi
                    WHERE soi.sales_order_id = so.id
                      AND soi.id = t.source_item_id
                )
            )
        )
        OR EXISTS (
            SELECT 1
            FROM sales_order_item soi
            WHERE soi.sales_order_id = so.id
              AND soi.source_ticket_id = t.id
        )
)
SELECT
    COUNT(*) AS total_count,
    COUNT(*) FILTER (WHERE COALESCE(discarded, false) = true) AS discarded_count,
    COUNT(*) FILTER (WHERE COALESCE(discarded, false) = false) AS active_count,
    COUNT(*) FILTER (WHERE COALESCE(mes_sync_status, '') = 'CALLBACK_PENDING') AS callback_pending_count
FROM target_tickets;
```

也可以查看明细：

```sql
WITH target_order AS (
    SELECT id, sales_order_no
    FROM sales_order_header
    WHERE sales_order_no = '这里替换成销售订单号'
),
target_tickets AS (
    SELECT DISTINCT t.*
    FROM order_delivery_ticket t
    JOIN target_order so ON
        (
            t.source_type = 'SALES_ORDER'
            AND (
                t.source_order_id = so.id
                OR EXISTS (
                    SELECT 1
                    FROM sales_order_item soi
                    WHERE soi.sales_order_id = so.id
                      AND soi.id = t.source_item_id
                )
            )
        )
        OR EXISTS (
            SELECT 1
            FROM sales_order_item soi
            WHERE soi.sales_order_id = so.id
              AND soi.source_ticket_id = t.id
        )
)
SELECT
    id,
    part_no,
    version,
    qty,
    mes_sync_status,
    wms_allocate_status,
    discarded,
    discarded_at,
    discard_reason
FROM target_tickets
ORDER BY id;
```

## 5. 可选：恢复本次批量废弃

如果误操作，可以按同一销售订单号恢复。恢复原因可以按实际情况修改。

```sql
BEGIN;

WITH target_order AS (
    SELECT id, sales_order_no
    FROM sales_order_header
    WHERE sales_order_no = '这里替换成销售订单号'
),
target_tickets AS (
    SELECT DISTINCT t.id
    FROM order_delivery_ticket t
    JOIN target_order so ON
        (
            t.source_type = 'SALES_ORDER'
            AND (
                t.source_order_id = so.id
                OR EXISTS (
                    SELECT 1
                    FROM sales_order_item soi
                    WHERE soi.sales_order_id = so.id
                      AND soi.id = t.source_item_id
                )
            )
        )
        OR EXISTS (
            SELECT 1
            FROM sales_order_item soi
            WHERE soi.sales_order_id = so.id
              AND soi.source_ticket_id = t.id
        )
),
admin_user AS (
    SELECT id::bigint AS user_id
    FROM erp_user
    WHERE username = 'admin'
    LIMIT 1
)
UPDATE order_delivery_ticket t
SET
    discarded = false,
    restored_at = CURRENT_TIMESTAMP,
    restored_by = (SELECT user_id FROM admin_user),
    restore_reason = '按销售订单整单批量恢复'
WHERE t.id IN (SELECT id FROM target_tickets)
  AND COALESCE(t.discarded, false) = true
RETURNING
    t.id,
    t.part_no,
    t.version,
    t.discarded,
    t.restored_at,
    t.restore_reason;

COMMIT;
```

## 6. 如果需要同时处理分配表

`order_delivery_ticket_allocation` 是锁库零件 / 待产零件等下游分配表。当前已提零件页面的单行废弃逻辑只废弃 `order_delivery_ticket`，不自动级联更新 allocation。

如果业务明确要求“已生成的锁库零件 / 待产零件也一起隐藏或废弃”，需要另行评估后再更新：

```sql
-- 不建议默认执行，仅在业务确认后使用。
UPDATE order_delivery_ticket_allocation alloc
SET
    discarded = true,
    discarded_at = CURRENT_TIMESTAMP,
    discarded_by = NULL,
    discard_reason = '随销售订单已提零件批量废弃'
WHERE alloc.ticket_id IN (...目标 order_delivery_ticket.id...)
  AND COALESCE(alloc.discarded, false) = false;
```

