-- ============================================================================
-- 已提零件「批量获取物料号」三字段身份迁移 —— 打包前置核对（只读）
--
-- 用途：在产出 V71（把活动请求身份从「图号+版本」升级为「图号+版本+材质」）之前，
--       确认两件事：
--         Q2 活动台账按新身份分组是否出现重复（重复 = 回填会撞唯一索引，Flyway 失败）
--         Q3 每条在途批次能否证明「出站材质 == 当前提单材质」（证明不了就不能猜）
--
-- 用法（服务器上，脚本在仓库 docs/ops/ 下）：
--   docker cp mes-material-identity-preflight.sql quote-postgres:/tmp/
--   docker exec -i quote-postgres psql -U <POSTGRES_USER> -d <POSTGRES_DB> \
--       -f /tmp/mes-material-identity-preflight.sql
--
-- 本脚本只有 SELECT，不修改任何数据，可在生产库直接执行。
-- 需要 PostgreSQL 11+（内置 sha256()，不依赖 pgcrypto）。
--
-- 判读规则（只有两条硬门槛）：
--   Q2 必须 0 行            → 有行就先人工裁决，不要打包
--   Q3 必须全部 PROVABLE    → 有 UNPROVABLE 的行就保留旧两字段键 + 人工核对
--
-- 归一化口径与代码保持一致：
--   图号 / 版本 = btrim + upper（normalizeKeyPart）
--   材质        = btrim，空串与 NULL 等价（trimToNull）
--   两侧去空白的字符集只对「空格 + \t\n\r\f\v」一致：Java String.trim() 实际去的是
--   U+0000–U+0020 全部字符，V71 的 btrim 只去这 6 个。其它控制字符（\x00–\x08、
--   \x0E–\x1F）会被 Java 去掉而 SQL 保留，键就会不一致 —— 由 Q6 专门拦。
--   **本脚本所有查询（Q1–Q6）都必须用同一个字符集**，不能出现裸 btrim()：
--   裸 btrim() 只去空格，与 V71 的 qpo_mes_material_identity() 口径不同，
--   会让 Q2 漏报归一化后相同的身份，Q2 报 0 行但 V71 撞唯一索引失败。
-- ============================================================================

\echo ''
\echo '=== Q1 活动台账总览（先确认有没有在途数据）==='
\echo '    request_role=(null) 且 from_batch=f 的行就是刷新料号写入的台账行'

SELECT r.request_status,
       COALESCE(r.request_role, '(null)')      AS request_role,
       (r.batch_id IS NOT NULL)                AS from_batch,
       (r.dedup_key IS NOT NULL)               AS has_dedup_key,
       COUNT(*)                                AS row_count
  FROM order_delivery_ticket_mes_request r
 WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
 GROUP BY 1, 2, 3, 4
 ORDER BY 1, 2, 3, 4;

\echo ''
\echo '=== Q2 重复身份明细（硬门槛：必须 0 行）==='
\echo '    batch=t 的行会被回填成三字段键；batch=f 的刷新行按推定值回填'

WITH active AS (
    SELECT r.id                                        AS request_id,
           r.mes_request_id                            AS biz_id,
           r.ticket_id,
           COALESCE(r.request_role, '(null)')          AS request_role,
           (r.batch_id IS NOT NULL)                    AS from_batch,
           r.request_status,
           upper(btrim(t.part_no, E' \t\n\r\f\v'))                          AS part_no,
           upper(btrim(COALESCE(t.version, ''), E' \t\n\r\f\v'))            AS version,
           btrim(COALESCE(t.material_name, ''), E' \t\n\r\f\v')             AS material
      FROM order_delivery_ticket_mes_request r
      JOIN order_delivery_ticket t ON t.id = r.ticket_id
     WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
)
SELECT part_no,
       version,
       material,
       COUNT(*)            AS active_rows,
       COUNT(*) FILTER (WHERE from_batch) AS batch_rows,
       string_agg(
           format('req=%s biz=%s ticket=%s status=%s role=%s batch=%s',
                  request_id, biz_id, ticket_id, request_status, request_role, from_batch),
           E'\n' ORDER BY request_id
       )                   AS detail
  FROM active
 GROUP BY part_no, version, material
HAVING COUNT(*) > 1
 ORDER BY active_rows DESC, part_no;

\echo ''
\echo '=== Q3 在途批次的材质可证明性（硬门槛：必须全部 PROVABLE）==='
\echo '    按当初出站序列化逐字节重建请求 JSON 并重算 SHA-256，与库中 request_fingerprint 比对'
\echo '    PROVABLE   = 出站材质与当前提单材质一致，可安全回填三字段键'
\echo '    UNPROVABLE = 请求期间提单被改过（含备注/描述/表面处理，指纹都覆盖），保留旧键 + 人工核对'

WITH batch_item AS (
    SELECT b.id AS batch_id, b.biz_id, b.request_fingerprint, i.id AS item_id, i.ticket_id
      FROM mes_integration_batch b
      JOIN mes_material_batch_item i ON i.batch_id = b.id
     WHERE b.batch_type = 'MATERIAL_PREPARATION'
),
active_batch AS (
    SELECT DISTINCT r.batch_id
      FROM order_delivery_ticket_mes_request r
     WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
       AND r.batch_id IS NOT NULL
),
detail AS (
    SELECT bi.batch_id, bi.biz_id, bi.request_fingerprint, bi.item_id,
           NULLIF(btrim(d.part_no, E' \t\n\r\f\v'), '')                    AS drawing_no,
           COALESCE(btrim(d.version, E' \t\n\r\f\v'), '')                  AS drawing_version,
           NULLIF(btrim(d.material_name, E' \t\n\r\f\v'), '')              AS material,
           NULLIF(btrim(d.description, E' \t\n\r\f\v'), '')                AS part_name,
           NULLIF(btrim(d.remark, E' \t\n\r\f\v'), '')                     AS remark,
           NULLIF(btrim(d.internal_project_no, E' \t\n\r\f\v'), '')        AS project_no,
           CASE WHEN o.id IS NULL
                THEN NULLIF(btrim(d.customer_name, E' \t\n\r\f\v'), '')
                ELSE NULLIF(btrim(o.customer_name, E' \t\n\r\f\v'), '')
           END                                                             AS customer_name,
           CASE WHEN COALESCE(NULLIF(btrim(d.source_type, E' \t\n\r\f\v'), ''), 'RFQ') = 'SALES_ORDER'
                     AND NULLIF(btrim(sh.customer_code, E' \t\n\r\f\v'), '') IS NOT NULL
                THEN NULLIF(btrim(sh.customer_code, E' \t\n\r\f\v'), '')
                ELSE NULLIF(btrim(o.customer_code, E' \t\n\r\f\v'), '')
           END                                                             AS customer_code,
           NULLIF(btrim(replace(replace(d.material_name, E'\r\n', E'\n'), E'\r', E'\n'),
                       E' \t\n\r\f\v'), '')                                AS cr_material,
           NULLIF(btrim(replace(replace(d.surface_treatment, E'\r\n', E'\n'), E'\r', E'\n'),
                       E' \t\n\r\f\v'), '')                                AS cr_surface
      FROM batch_item bi
      JOIN order_delivery_ticket d ON d.id = bi.ticket_id
      LEFT JOIN order_rfq o ON o.id = d.rfq_id
      LEFT JOIN sales_order_header sh ON sh.id = d.source_order_id
),
item_json AS (
    SELECT batch_id, item_id,
           concat_ws(',',
               CASE WHEN cr IS NOT NULL
                    THEN '"customerRequirements":' || to_jsonb(cr)::text END,
               CASE WHEN drawing_no IS NOT NULL
                    THEN '"drawingNo":' || to_jsonb(drawing_no)::text END,
               '"drawingVersion":' || to_jsonb(drawing_version)::text,
               CASE WHEN material IS NOT NULL
                    THEN '"material":' || to_jsonb(material)::text END,
               CASE WHEN part_name IS NOT NULL
                    THEN '"name":' || to_jsonb(part_name)::text END,
               CASE WHEN remark IS NOT NULL
                    THEN '"remark":' || to_jsonb(remark)::text END
           ) AS fragment
      FROM (
            SELECT d.*,
                   NULLIF(concat_ws(E'\n',
                       CASE WHEN cr_material IS NOT NULL THEN '材料名称：' || cr_material END,
                       CASE WHEN cr_surface IS NOT NULL THEN '表面处理：' || cr_surface END
                   ), '') AS cr
              FROM detail d
           ) x
),
batch_json AS (
    -- 键顺序必须与 Jackson（SORT_PROPERTIES_ALPHABETICALLY）一致，且 NON_NULL：null 字段整段省略
    SELECT d.batch_id, d.biz_id, d.request_fingerprint,
           (array_agg(d.customer_code ORDER BY d.item_id))[1]  AS customer_code,
           (array_agg(d.customer_name ORDER BY d.item_id))[1]  AS customer_name,
           (array_agg(d.project_no ORDER BY d.item_id))[1]     AS project_no,
           string_agg('{' || j.fragment || '}', ',' ORDER BY j.item_id) AS items_json
      FROM detail d
      JOIN item_json j ON j.batch_id = d.batch_id AND j.item_id = d.item_id
     GROUP BY d.batch_id, d.biz_id, d.request_fingerprint
),
fp AS (
    SELECT batch_id, biz_id, request_fingerprint,
           '{' || concat_ws(',',
                  '"bizId":' || to_jsonb(biz_id)::text,
                  CASE WHEN customer_code IS NOT NULL
                       THEN '"customerCode":' || to_jsonb(customer_code)::text END,
                  CASE WHEN customer_name IS NOT NULL
                       THEN '"customerName":' || to_jsonb(customer_name)::text END,
                  CASE WHEN project_no IS NOT NULL
                       THEN '"internalProjectNo":' || to_jsonb(project_no)::text END,
                  '"items":[' || items_json || ']'
           ) || '}' AS request_json
      FROM batch_json
)
SELECT f.batch_id,
       f.biz_id,
       CASE WHEN f.request_fingerprint = encode(sha256(convert_to(f.request_json, 'UTF8')), 'hex')
            THEN 'PROVABLE' ELSE 'UNPROVABLE' END                      AS material_proof,
       f.request_fingerprint                                            AS stored,
       encode(sha256(convert_to(f.request_json, 'UTF8')), 'hex')        AS computed,
       f.request_json
  FROM fp f
  JOIN active_batch ab ON ab.batch_id = f.batch_id
 ORDER BY f.batch_id;

\echo ''
\echo '=== Q4 waiter 与 master 身份不一致（回填时要不要拆关系）==='
\echo '    有行 = 历史 follower 关系本身挂错了，回填前需要人工确认'

SELECT w.id                        AS waiter_id,
       w.waiter_ticket_id,
       w.master_ticket_id,
       w.master_mes_request_id,
       w.wait_status,
       wt.part_no                  AS waiter_part_no,
       wt.version                  AS waiter_version,
       wt.material_name            AS waiter_material,
       mt.part_no                  AS master_part_no,
       mt.version                  AS master_version,
       mt.material_name            AS master_material
  FROM order_delivery_ticket_mes_waiter w
  JOIN order_delivery_ticket wt ON wt.id = w.waiter_ticket_id
  LEFT JOIN order_delivery_ticket mt ON mt.id = w.master_ticket_id
 WHERE w.wait_status IN ('WAITING', 'PROCESSING', 'CALLBACK_PENDING')
   AND (
         mt.id IS NULL
      OR upper(btrim(wt.part_no, E' \t\n\r\f\v')) <> upper(btrim(mt.part_no, E' \t\n\r\f\v'))
      OR upper(btrim(COALESCE(wt.version, ''), E' \t\n\r\f\v'))
         <> upper(btrim(COALESCE(mt.version, ''), E' \t\n\r\f\v'))
      OR btrim(COALESCE(wt.material_name, ''), E' \t\n\r\f\v')
         <> btrim(COALESCE(mt.material_name, ''), E' \t\n\r\f\v')
       )
 ORDER BY w.id;

\echo ''
\echo '=== Q5 刷新料号在途行（没有批次指纹，回填只能用推定值）==='
\echo '    这些行会拦住同图号版本的所有材质，属保守行为，符合预期'

SELECT r.id                AS request_id,
       r.mes_request_id    AS biz_id,
       r.ticket_id,
       r.request_status,
       r.requested_at,
       t.part_no,
       t.version,
       t.material_name
  FROM order_delivery_ticket_mes_request r
  JOIN order_delivery_ticket t ON t.id = r.ticket_id
 WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
   AND r.batch_id IS NULL
 ORDER BY r.id DESC;

\echo ''
\echo '=== Q6 活动行里的非常规控制字符（硬门槛：必须 0 行）==='
\echo '    Java trim() 去 U+0000–U+0020，V71 的 btrim 只去空格与 \t\n\r\f\v。'
\echo '    含其它控制字符时两侧算出的键不同 → 在途请求漏判 → 重复提交。'
\echo '    先 translate 掉双方都去的那 6 个字符，再看还剩不剩控制字符，避免误报。'

SELECT r.id             AS request_id,
       r.mes_request_id AS biz_id,
       t.id             AS ticket_id,
       t.part_no,
       t.version,
       t.material_name
  FROM order_delivery_ticket_mes_request r
  JOIN order_delivery_ticket t ON t.id = r.ticket_id
 WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
   AND (
        translate(COALESCE(t.part_no, ''),       E' \t\n\r\f\v', '') ~ '[[:cntrl:]]'
     OR translate(COALESCE(t.version, ''),       E' \t\n\r\f\v', '') ~ '[[:cntrl:]]'
     OR translate(COALESCE(t.material_name, ''), E' \t\n\r\f\v', '') ~ '[[:cntrl:]]'
   )
 ORDER BY r.id;

\echo ''
\echo '--- 判读 ---'
\echo 'Q2 有行       → BLOCK：先按明细人工裁决，不要打包'
\echo 'Q3 有 UNPROVABLE → 该批次保留旧两字段键 + 人工核对，其余行照常回填'
\echo 'Q6 有行       → BLOCK：先把这些行的控制字符清掉，否则回填键与运行期键不一致'
\echo 'Q2/Q6 空且 Q3 全 PROVABLE → 可以按计划产出 V71'
