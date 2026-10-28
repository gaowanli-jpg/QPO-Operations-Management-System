-- 提单创建时间。
--
-- 背景：「已提零件」列表需要展示创建时间，但 order_delivery_ticket 只有业务日期
-- （start_date 为订单日期、delivery_date 为交货日期），没有落库的创建时间。
-- 待产零件/锁库零件的时间来自 order_delivery_ticket_allocation.created_at，
-- 而提单行在物料号确认前还没有分配行，无法复用，只能补到提单表上。

SET search_path TO "${flyway:defaultSchema}";

ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS created_at timestamp without time zone;

-- 历史数据回填：优先取该提单最早一条分配行的创建时间（与提单同一业务流程产生），
-- 其次退回 MES 同步时间，仍为空则用迁移执行时间兜底，保证列可以置为 NOT NULL。
UPDATE order_delivery_ticket t
SET created_at = COALESCE(
        (SELECT MIN(a.created_at)
         FROM order_delivery_ticket_allocation a
         WHERE a.ticket_id = t.id),
        t.mes_synced_at
    )
WHERE t.created_at IS NULL;

UPDATE order_delivery_ticket
SET created_at = CURRENT_TIMESTAMP
WHERE created_at IS NULL;

ALTER TABLE order_delivery_ticket
    ALTER COLUMN created_at SET DEFAULT CURRENT_TIMESTAMP;

ALTER TABLE order_delivery_ticket
    ALTER COLUMN created_at SET NOT NULL;

COMMENT ON COLUMN order_delivery_ticket.created_at IS '提单创建时间，用于已提零件列表展示';
