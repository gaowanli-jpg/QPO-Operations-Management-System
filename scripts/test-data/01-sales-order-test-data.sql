-- =============================================================================
-- QPO 测试数据：销售订单（已生成 + 已自动提单）
-- =============================================================================
-- 目标状态：数据停在「销售订单刚生成」这一刻。
--   · 销售订单管理页能看到 5 张订单（4 张已生成提单 + 1 张部分已提单）
--   · 已提零件页能看到 10 条提单行，全部「待获取物料号」
--   · 物料号 / 待产零件 / 锁库 / MES 生产下发 / U9 同步 全部保持未处理
--   · 后续每一步都留给人手工在页面上点，脚本不预置任何下游数据
--
-- 【通用版 · 纯 SQL】全文件只有 DML 和 SELECT，没有任何 PostgreSQL 特有语法糖：
--   · 不用临时表、不用 DO 块、不用 $$ 美元引用、不用 :: 类型转换
--   · 不依赖 order_delivery_ticket.created_at（Flyway V68 才加的列）
--   → 任何 PG 客户端（Navicat / DBeaver / pgAdmin / DataGrip）整份执行都能跑
--   → Flyway V66 及以上都能跑
--
-- 执行方式（任选其一）：
--   1) psql -h <host> -p <port> -U <user> -d <db> -f 本文件
--   2) 用客户端打开本文件，全选，一次性执行（不要只选中一部分）
--
-- 幂等性：所有数据带 QTEST 前缀，脚本开头先清理上一轮同类数据再重建，
--         重复执行结果一致。清理范围严格限定在 QTEST 前缀内，不碰真实数据。
--
-- 失败处理：整份脚本包在 BEGIN / COMMIT 里，任何一条语句报错都会让事务进入
--           中止状态，最后的 COMMIT 自动变成 ROLLBACK，不会留下半截数据。
--           报错后改完再整份重跑即可。
-- =============================================================================

-- -----------------------------------------------------------------------------
-- 0. 先看一眼环境版本（这条单独执行也能跑，用来确认连的是哪个库）
--    期望 71；66 也能正常跑，只是「已提零件」列表不显示创建时间。
-- -----------------------------------------------------------------------------
SELECT MAX(CAST(version AS integer)) AS flyway_version
  FROM flyway_schema_history
 WHERE success;

BEGIN;

-- -----------------------------------------------------------------------------
-- 1. 清理上一轮测试数据（只删 QTEST 前缀）
--    顺序按外键依赖从叶子往根删，避免约束报错。
--    每段用 CTE 现算目标 id，不依赖临时表。
-- -----------------------------------------------------------------------------

-- 下游可能已经跑过物料号 / 待产 / 锁库，先把挂在这些行上的引用摘掉
WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
UPDATE sales_order_item
   SET allocation_id = NULL
 WHERE sales_order_id IN (SELECT id FROM tgt)
   AND allocation_id IS NOT NULL;

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
), tk AS (
    SELECT id FROM order_delivery_ticket
     WHERE source_type = 'SALES_ORDER' AND source_order_id IN (SELECT id FROM tgt)
)
DELETE FROM mes_material_batch_item
 WHERE ticket_id IN (SELECT id FROM tk);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
), tk AS (
    SELECT id FROM order_delivery_ticket
     WHERE source_type = 'SALES_ORDER' AND source_order_id IN (SELECT id FROM tgt)
)
DELETE FROM order_delivery_ticket_mes_waiter
 WHERE master_ticket_id IN (SELECT id FROM tk)
    OR waiter_ticket_id IN (SELECT id FROM tk);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
), tk AS (
    SELECT id FROM order_delivery_ticket
     WHERE source_type = 'SALES_ORDER' AND source_order_id IN (SELECT id FROM tgt)
)
DELETE FROM order_delivery_ticket_mes_request
 WHERE ticket_id IN (SELECT id FROM tk);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
), tk AS (
    SELECT id FROM order_delivery_ticket
     WHERE source_type = 'SALES_ORDER' AND source_order_id IN (SELECT id FROM tgt)
)
DELETE FROM wms_reservation_ticket
 WHERE ticket_id IN (SELECT id FROM tk)
    OR sales_order_item_id IN (
           SELECT i.id FROM sales_order_item i WHERE i.sales_order_id IN (SELECT id FROM tgt)
       );

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
DELETE FROM wms_reservation_batch
 WHERE sale_order_id IN (SELECT id FROM tgt);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
), tk AS (
    SELECT id FROM order_delivery_ticket
     WHERE source_type = 'SALES_ORDER' AND source_order_id IN (SELECT id FROM tgt)
)
DELETE FROM order_delivery_ticket_allocation
 WHERE ticket_id IN (SELECT id FROM tk);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
DELETE FROM sales_order_change_notice
 WHERE sales_order_id IN (SELECT id FROM tgt);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
DELETE FROM order_delivery_ticket
 WHERE source_type = 'SALES_ORDER'
   AND source_order_id IN (SELECT id FROM tgt);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
DELETE FROM sales_order_item_assembly_relation
 WHERE sales_order_id IN (SELECT id FROM tgt);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
DELETE FROM sales_order_item
 WHERE sales_order_id IN (SELECT id FROM tgt);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
DELETE FROM sales_order_source_rfq
 WHERE sales_order_id IN (SELECT id FROM tgt);

WITH tgt AS (
    SELECT id FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
)
DELETE FROM sales_order_header
 WHERE id IN (SELECT id FROM tgt);

DELETE FROM customer_master
 WHERE customer_code LIKE 'QTEST-%';

-- -----------------------------------------------------------------------------
-- 2. 客户主数据（3 家）
--    销售订单必须挂在客户主数据上：客户下拉、客户维度数据权限、详情页都靠它。
--    用 NOT EXISTS 而不是 ON CONFLICT，避免依赖 customer_code 上的唯一索引。
-- -----------------------------------------------------------------------------
WITH ctx AS (
    SELECT COALESCE(
               (SELECT id FROM system_user WHERE username = 'market' ORDER BY id LIMIT 1),
               (SELECT MIN(id) FROM system_user)
           ) AS user_id
)
INSERT INTO customer_master (customer_code, customer_short_name, customer_name,
                             enabled, created_by, updated_by, u9_org_code)
SELECT v.customer_code, v.customer_short_name, v.customer_name,
       TRUE, ctx.user_id, ctx.user_id, '101'
FROM (VALUES
    ('QTEST-C001', '苏州启测', '苏州启测精密机械有限公司'),
    ('QTEST-C002', '无锡启测', '无锡启测自动化设备有限公司'),
    ('QTEST-C003', '常州启测', '常州启测液压科技有限公司')
) AS v(customer_code, customer_short_name, customer_name)
CROSS JOIN ctx
WHERE NOT EXISTS (
    SELECT 1 FROM customer_master c WHERE c.customer_code = v.customer_code
);

-- -----------------------------------------------------------------------------
-- 3. 销售订单头（5 张）
--    status = 已生成提单 / 部分已提单，就是「刚生成完销售订单」的样子。
--    u9_sync_status 一律「未同步」，留给人工去 U9 同步页验证。
--    total_item_count 与实际零件行数一致；unmatched_item_count 等于总行数，
--    因为库里没有历史已提单快照，自动匹配一定命中不了（真实环境也是这个结果）。
-- -----------------------------------------------------------------------------
WITH ctx AS (
    SELECT COALESCE(
               (SELECT id FROM system_user WHERE username = 'market' ORDER BY id LIMIT 1),
               (SELECT MIN(id) FROM system_user)
           ) AS user_id
)
INSERT INTO sales_order_header (
    sales_order_no, document_no, document_type_code,
    customer_id, customer_code, customer_short_name, customer_name,
    sales_order_date, customer_order_no, customer_project_no, customer_order_title,
    order_type, internal_project_no, status, u9_sync_status,
    total_item_count, matched_item_count, unmatched_item_count, warning_message,
    source_type, created_by, owner_user_id, created_at, updated_at
)
SELECT v.sales_order_no, v.sales_order_no, 'SO06',
       c.id, c.customer_code, c.customer_short_name, c.customer_name,
       v.sales_order_date, v.customer_order_no, v.customer_project_no, v.customer_order_title,
       v.order_type, v.internal_project_no, v.status, '未同步',
       v.total_item_count, 0, v.total_item_count, '未命中历史已提单生产快照',
       'DIRECT_IMPORT',
       ctx.user_id,
       ctx.user_id,
       v.created_at, v.created_at
FROM (VALUES
    -- 订单号           客户编码      下单日期        客户订单号        客户项目号        标题               订单类型   内部项目号         状态           行数  创建时间
    ('QTEST-SO-2601', 'QTEST-C001', DATE '2026-10-28', 'CGDD-2026-1028', 'XM-2026-101', '测试订单 2601 三行全提', '正式订单', 'QTEST-PRJ-2601', '已生成提单', 3, TIMESTAMP '2026-10-28 09:15:00'),
    ('QTEST-SO-2602', 'QTEST-C002', DATE '2026-10-29', 'CGDD-2026-1029', 'XM-2026-102', '测试订单 2602 含缺价行', '正式订单', 'QTEST-PRJ-2602', '已生成提单', 2, TIMESTAMP '2026-10-29 10:20:00'),
    ('QTEST-SO-2603', 'QTEST-C001', DATE '2026-10-30', 'CGDD-2026-1030', 'XM-2026-103', '测试订单 2603 备货小件', '备货订单', 'QTEST-PRJ-2603', '已生成提单', 2, TIMESTAMP '2026-10-30 08:40:00'),
    ('QTEST-SO-2604', 'QTEST-C003', DATE '2026-10-27', 'CGDD-2026-1027', 'XM-2026-104', '测试订单 2604 部分已提单', '正式订单', 'QTEST-PRJ-2604', '部分已提单', 2, TIMESTAMP '2026-10-27 14:05:00'),
    ('QTEST-SO-2605', 'QTEST-C002', DATE '2026-10-30', 'CGDD-2026-1031', 'XM-2026-105', '测试订单 2605 大批量', '正式订单', 'QTEST-PRJ-2605', '已生成提单', 1, TIMESTAMP '2026-10-30 11:00:00')
) AS v(sales_order_no, customer_code, sales_order_date, customer_order_no, customer_project_no,
       customer_order_title, order_type, internal_project_no, status, total_item_count, created_at)
JOIN customer_master c ON c.customer_code = v.customer_code
CROSS JOIN ctx;

-- -----------------------------------------------------------------------------
-- 4. 销售订单零件行（10 行）
--    released_qty = 已提单数量；status 由 released_qty 与 qty 推导：
--      全提 → 已生成提单 / 部分提 → 部分已提单
--    material_no 留空、material_no_status = 未确认，等人工去已提零件获取。
--    material_data 里的 name / materialCode 是给核价、MES 材质、列表展示用的快照。
--    QTEST-SO-2602 第 1 行故意不给价格，用来验证 U9 同步的「价格不能为空」拦截。
-- -----------------------------------------------------------------------------
INSERT INTO sales_order_item (
    sales_order_id, line_no, part_no, description, qty, version, order_type,
    customer_order_no, customer_project_no, internal_project_no,
    sales_material_no, material_no, customer_delivery_date, internal_delivery_date,
    material_name, engineering_material_name, engineering_material_code,
    engineering_material_type_code, engineering_material_type_name, engineering_material_category,
    surface_treatment, tax_included_unit_price, untaxed_unit_price,
    tax_included_total_price, untaxed_total_price,
    shipped_qty, unshipped_qty, released_qty, remaining_qty,
    drawing_url, has_drawing, material_gross_weight, part_net_weight,
    quote_mode, assembly_fee, base_cost, item_type,
    material_data, process_route, fee_data,
    matched_rfq_id, matched_item_id, match_status, match_message,
    remark, status, assembly_relation_status, material_no_status, material_shape,
    sales_order_date, customer_order_title, source_type, created_at, updated_at
)
SELECT h.id,
       v.line_no,
       v.part_no,
       v.description,
       v.qty,
       v.version,
       h.order_type,
       h.customer_order_no,
       h.customer_project_no,
       h.internal_project_no,
       NULL,                       -- sales_material_no：销售料号，测试数据留空
       NULL,                       -- material_no：物料号，等 MES 回写
       v.customer_delivery_date,
       v.internal_delivery_date,
       v.material_name,
       v.material_name,
       v.material_code,
       'RAW_MATERIAL',
       '原材料',
       '原材料',
       v.surface_treatment,
       v.unit_price,
       CASE WHEN v.unit_price IS NULL THEN NULL ELSE ROUND(v.unit_price / 1.13, 4) END,
       CASE WHEN v.unit_price IS NULL THEN NULL ELSE ROUND(v.unit_price * v.qty, 2) END,
       CASE WHEN v.unit_price IS NULL THEN NULL ELSE ROUND(v.unit_price * v.qty / 1.13, 2) END,
       0, 0,
       v.released_qty,
       v.qty - v.released_qty,
       NULL,                       -- drawing_url：没有图纸，补图是人工测试项
       FALSE,                      -- has_drawing
       ROUND(v.qty * 1.85, 4),     -- material_gross_weight：毛重，占位合理值
       ROUND(v.qty * 1.42, 4),     -- part_net_weight：净重
       'NORMAL',
       0,
       NULL,
       v.item_type,
       jsonb_build_object(
           'name', v.material_name,
           'materialCode', v.material_code,
           'materialTypeCode', 'RAW_MATERIAL',
           'materialTypeName', '原材料',
           'materialCategory', '原材料',
           'shape', v.material_shape
       ),
       CAST('[]' AS jsonb),        -- process_route：工艺路线，等核价/快照补全
       CAST('[]' AS jsonb),        -- fee_data：费用明细
       NULL, NULL,
       '未匹配',
       '未命中历史已提单生产快照',
       v.remark,
       CASE WHEN v.released_qty >= v.qty THEN '已生成提单' ELSE '部分已提单' END,
       '不适用',
       '未确认',
       v.material_shape,
       h.sales_order_date,
       h.customer_order_title,
       'DIRECT_IMPORT',
       h.created_at,
       h.created_at
FROM (VALUES
    -- 订单号          行号 图号              描述                    数量  版本 材质         材质编码       表面处理   客户交期        内部交期        含税单价  已提  备注                     材料形态 零件类型
    ('QTEST-SO-2601', 1, 'QTEST-2601-001', '法兰盘 FLANGE-A',       10,  'A', '45#钢',    'MAT-45',     '发黑处理', DATE '2026-11-10', DATE '2026-11-15', 128.5000, 10, '首件需附材质证明',     '圆钢', '普通件'),
    ('QTEST-SO-2601', 2, 'QTEST-2601-002', '连接轴 SHAFT-B',         4,  'B', '6061-T6',  'MAT-6061',   '阳极氧化', DATE '2026-11-18', DATE '2026-11-22', 356.0000,  4, '同轴度 0.02',          '棒料', '普通件'),
    ('QTEST-SO-2601', 3, 'QTEST-2601-003', '支撑座 BRACKET-C',       2,  'A', 'SUS304',   'MAT-SUS304', '钝化',     DATE '2026-11-25', DATE '2026-11-30', 890.0000,  2, NULL,                  '板材', '普通件'),
    ('QTEST-SO-2602', 1, 'QTEST-2602-001', '端盖 COVER-A',           6,  'A', '45#钢',    'MAT-45',     '镀锌',     DATE '2026-11-12', DATE '2026-11-18', NULL,      6, '价格待补，用于验证 U9 同步拦截', '圆钢', '普通件'),
    ('QTEST-SO-2602', 2, 'QTEST-2602-002', '导向套 GUIDE-B',         8,  'A', 'SUS304',   'MAT-SUS304', '无',       DATE '2026-11-20', DATE '2026-11-25',  96.0000,  8, NULL,                  '棒料', '普通件'),
    ('QTEST-SO-2603', 1, 'QTEST-2603-001', '定位销 PIN-A',          50,  'A', '40Cr',     'MAT-40CR',   '淬火 HRC45', DATE '2026-11-03', DATE '2026-11-05',  18.6000, 50, '急件',                '棒料', '普通件'),
    ('QTEST-SO-2603', 2, 'QTEST-2603-002', '垫片 WASHER-B',        200,  'A', 'Q235B',    'MAT-Q235B',  '发黑',     DATE '2026-11-03', DATE '2026-11-05',   2.3500, 200, NULL,                 '板材', '普通件'),
    ('QTEST-SO-2604', 1, 'QTEST-2604-001', '液压缸筒 CYL-A',        10,  'A', '2Cr13',    'MAT-2CR13',  '镀铬',     DATE '2026-11-08', DATE '2026-11-14', 1580.0000, 10, '内孔粗糙度 Ra0.4',    '管料', '普通件'),
    ('QTEST-SO-2604', 2, 'QTEST-2604-002', '活塞杆 ROD-B',          20,  'A', '45#钢',    'MAT-45',     '镀铬',     DATE '2026-11-08', DATE '2026-11-14', 420.0000,  8, '本批先提 8 件，剩余待提', '圆钢', '普通件'),
    ('QTEST-SO-2605', 1, 'QTEST-2605-001', '机架 FRAME-A',         100,  'A', 'Q235B',    'MAT-Q235B',  '喷塑',     DATE '2026-12-15', DATE '2026-12-20',  75.0000, 100, NULL,                 '板材', '普通件')
) AS v(sales_order_no, line_no, part_no, description, qty, version, material_name, material_code,
       surface_treatment, customer_delivery_date, internal_delivery_date, unit_price,
       released_qty, remark, material_shape, item_type)
JOIN sales_order_header h ON h.sales_order_no = v.sales_order_no;

-- -----------------------------------------------------------------------------
-- 5. 交付提单行（10 行）—— 对应「导入后自动提单」写出来的样子
--    字段口径完全对齐 DeliveryService.insertSalesOrderTicket：
--      rfq_id / item_id 为空，source_type = SALES_ORDER，source_order_id / source_item_id 指向订单与零件
--      rfq_no 存销售订单号（列表的「单号」列取这个字段）
--      qty 取零件已提数量（部分提单就是部分数量）
--      mes_sync_status / wms_allocate_status 保持 PENDING，物料号与库存都还没动
--
--    注意：这里不写 created_at —— 该列是 Flyway V68 才加的，V66 环境没有。
--    V68+ 环境该列有 DEFAULT CURRENT_TIMESTAMP，不写会自动填当前时间，
--    语义上也正确（测试数据就是今天导入的）。
-- -----------------------------------------------------------------------------
INSERT INTO order_delivery_ticket (
    rfq_id, item_id, order_type, project_no, part_no, qty,
    material_name, surface_treatment, version, start_date, delivery_date, remark,
    arrange_type, internal_project_no, item_type,
    engineering_material_name, engineering_material_code,
    engineering_material_type_code, engineering_material_type_name, engineering_material_category,
    customer_order_no, customer_project_no, process_route, material_data,
    material_gross_weight, part_net_weight, description, material_no,
    mes_sync_status, wms_allocate_status, rfq_no, customer_name,
    source_type, source_order_id, source_item_id, drawing_url, material_shape
)
SELECT NULL, NULL,
       COALESCE(i.order_type, h.order_type),
       h.sales_order_no,
       i.part_no,
       i.released_qty,
       i.material_name,
       i.surface_treatment,
       i.version,
       i.sales_order_date,
       i.internal_delivery_date,
       i.remark,
       '计划',
       i.internal_project_no,
       COALESCE(i.item_type, '普通件'),
       i.engineering_material_name,
       i.engineering_material_code,
       i.engineering_material_type_code,
       i.engineering_material_type_name,
       i.engineering_material_category,
       i.customer_order_no,
       i.customer_project_no,
       i.process_route,
       i.material_data,
       i.material_gross_weight,
       i.part_net_weight,
       i.description,
       NULL,
       'PENDING',
       'PENDING',
       h.sales_order_no,
       h.customer_name,
       'SALES_ORDER', h.id, i.id, i.drawing_url, i.material_shape
FROM sales_order_item i
JOIN sales_order_header h ON h.id = i.sales_order_id
WHERE h.sales_order_no LIKE 'QTEST-%'
  AND i.released_qty > 0
ORDER BY h.sales_order_no, i.line_no;

COMMIT;

-- -----------------------------------------------------------------------------
-- 6. 自检（看「结果」列，全 OK 就说明数据没问题）
--    这一节是纯 SELECT，不阻断事务；如果上面的 INSERT 出错，事务会整体回滚，
--    你会看到报错而不是这张表。
-- -----------------------------------------------------------------------------
SELECT '订单数' AS 检查项, CAST(count(*) AS varchar) AS 实际值, '5' AS 期望值,
       CASE WHEN count(*) = 5 THEN 'OK' ELSE '不符' END AS 结果
  FROM sales_order_header WHERE sales_order_no LIKE 'QTEST-%'
UNION ALL
SELECT '零件行数', CAST(count(*) AS varchar), '10',
       CASE WHEN count(*) = 10 THEN 'OK' ELSE '不符' END
  FROM sales_order_item i JOIN sales_order_header h ON h.id = i.sales_order_id
 WHERE h.sales_order_no LIKE 'QTEST-%'
UNION ALL
SELECT '提单行数', CAST(count(*) AS varchar), '10',
       CASE WHEN count(*) = 10 THEN 'OK' ELSE '不符' END
  FROM order_delivery_ticket t JOIN sales_order_header h ON h.id = t.source_order_id
 WHERE h.sales_order_no LIKE 'QTEST-%' AND t.source_type = 'SALES_ORDER'
UNION ALL
SELECT '订单头行数不一致', CAST(count(*) AS varchar), '0',
       CASE WHEN count(*) = 0 THEN 'OK' ELSE '不符' END
  FROM sales_order_header h
 WHERE h.sales_order_no LIKE 'QTEST-%'
   AND h.total_item_count <> (SELECT count(*) FROM sales_order_item i WHERE i.sales_order_id = h.id)
UNION ALL
SELECT '已提数量与提单不符', CAST(count(*) AS varchar), '0',
       CASE WHEN count(*) = 0 THEN 'OK' ELSE '不符' END
  FROM sales_order_item i JOIN sales_order_header h ON h.id = i.sales_order_id
 WHERE h.sales_order_no LIKE 'QTEST-%'
   AND i.released_qty <> (SELECT COALESCE(SUM(t.qty), 0) FROM order_delivery_ticket t
                           WHERE t.source_type = 'SALES_ORDER' AND t.source_item_id = i.id);

-- -----------------------------------------------------------------------------
-- 7. 生成结果一览（执行完直接看这里）
-- -----------------------------------------------------------------------------
SELECT h.sales_order_no              AS 销售订单号,
       h.customer_short_name         AS 客户,
       h.order_type                  AS 订单类型,
       h.status                      AS 订单状态,
       h.u9_sync_status              AS U9状态,
       h.total_item_count            AS 行数,
       (SELECT count(*) FROM order_delivery_ticket t
         WHERE t.source_type = 'SALES_ORDER' AND t.source_order_id = h.id) AS 提单行数,
       (SELECT COALESCE(SUM(t.qty), 0) FROM order_delivery_ticket t
         WHERE t.source_type = 'SALES_ORDER' AND t.source_order_id = h.id) AS 已提数量,
       h.sales_order_date            AS 下单日期
  FROM sales_order_header h
 WHERE h.sales_order_no LIKE 'QTEST-%'
 ORDER BY h.sales_order_no;

SELECT i.part_no       AS 图号,
       i.version       AS 版本,
       i.material_name AS 材质,
       i.qty           AS 数量,
       i.released_qty  AS 已提,
       i.remaining_qty AS 待提,
       i.status        AS 零件状态,
       i.material_no_status AS 物料号状态,
       COALESCE(CAST(i.tax_included_unit_price AS varchar), '（空，用于验证 U9 拦截）') AS 含税单价,
       t.id            AS 提单ID,
       t.mes_sync_status AS MES状态
  FROM sales_order_item i
  JOIN sales_order_header h ON h.id = i.sales_order_id
  LEFT JOIN order_delivery_ticket t ON t.source_type = 'SALES_ORDER' AND t.source_item_id = i.id
 WHERE h.sales_order_no LIKE 'QTEST-%'
 ORDER BY h.sales_order_no, i.line_no;
