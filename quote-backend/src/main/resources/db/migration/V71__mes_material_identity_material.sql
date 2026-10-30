-- 活动 MES 物料请求身份从「图号 + 版本」升级为「图号 + 版本 + 材质」。
--
-- 背景：MES 的物料准备任务按「图号 + 版本 + 材质」创建（同一图号版本换材质要重新建号）。
-- 只按图号+版本去重会把同图号同版本但材质不同的零件并成 follower，后者永远拿不到自己的料号。
--
-- 本迁移只做两件事：
--   1. 三处去重键列从 220 扩到 512；
--   2. 把仍然活动的台账行、提单行和等待行回填成三字段键，避免切换代码后在途请求
--      因键格式变化被当成新请求而重复提交。
--
-- 长度依据：order_delivery_ticket.part_no 为 varchar(100)、version 为 varchar(20)、
-- material_name 为 varchar(100)，长度前缀编码最坏 3+4+100+1+3+20+1+4+100 = 236 字符，
-- 512 有充分余量，不需要摘要降级。PostgreSQL 加宽 varchar 属元数据变更、不重写表，
-- uk_delivery_mes_request_active_dedup 与两个 dedup 索引会自动适配。
--
-- 回填前提（2026-10-29 正式库只读核验）：活动台账 15 行全部可回填，无重复身份，
-- 无 waiter/master 身份不一致，无刷新料号在途行。部署前请复跑
-- docs/ops/mes-material-identity-preflight.sql 确认结论未变。

SET search_path TO "${flyway:defaultSchema}";

ALTER TABLE order_delivery_ticket
    ALTER COLUMN mes_dedup_key TYPE character varying(512);

ALTER TABLE order_delivery_ticket_mes_request
    ALTER COLUMN dedup_key TYPE character varying(512);

ALTER TABLE order_delivery_ticket_mes_waiter
    ALTER COLUMN dedup_key TYPE character varying(512);

-- 与 MesMaterialDedupService.buildDedupKey 逐字节等价：
--   图号/版本 trim + uppercase；材质只 trim（不折叠大小写）；
--   长度前缀按字符计数（PostgreSQL length() 与 Java codePointCount 口径一致）。
-- btrim 的字符集与 Java String.trim() 去空白部分对齐（空格与五个空白控制符）。
CREATE OR REPLACE FUNCTION qpo_mes_material_identity(
    part_no text, version text, material_name text) RETURNS text
LANGUAGE sql IMMUTABLE AS $$
    SELECT 'v3|'
        || length(s.p) || ':' || s.p || '|'
        || length(s.v) || ':' || s.v || '|'
        || length(s.m) || ':' || s.m
      FROM (
        SELECT btrim(upper(btrim(coalesce(part_no, ''), E' \t\n\r\f\v')), E' \t\n\r\f\v') AS p,
               btrim(upper(btrim(coalesce(version, ''), E' \t\n\r\f\v')), E' \t\n\r\f\v') AS v,
               btrim(coalesce(material_name, ''), E' \t\n\r\f\v') AS m
      ) s
$$;

-- 闸门：图号/版本在 SQL 侧用 upper()，Java 侧用 toUpperCase(Locale.ROOT)；
-- 两者对非 ASCII 字符不保证一致。一旦存在这类数据，回填出的键会与运行期算出的键不同，
-- 在途请求会被漏判并重复提交，因此宁可让迁移失败也不要静默错配。
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
          FROM order_delivery_ticket_mes_request r
          JOIN order_delivery_ticket t ON t.id = r.ticket_id
         WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
           AND (coalesce(t.part_no, '') ~ '[^[:ascii:]]' OR coalesce(t.version, '') ~ '[^[:ascii:]]')
    ) THEN
        RAISE EXCEPTION 'MES material identity backfill requires ASCII drawing number/version; '
                        'resolve non-ASCII rows manually before upgrading';
    END IF;
END $$;

DO $$
DECLARE
    rec RECORD;
    new_key text;
    skipped_count integer := 0;
BEGIN
    -- 1) 活动批量台账行：按提单当前身份重算键。
    FOR rec IN
        SELECT r.id, r.dedup_key, t.part_no, t.version, t.material_name
          FROM order_delivery_ticket_mes_request r
          JOIN order_delivery_ticket t ON t.id = r.ticket_id
         WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
           AND r.dedup_key IS NOT NULL
           AND btrim(r.dedup_key) <> ''
         ORDER BY r.id
    LOOP
        new_key := qpo_mes_material_identity(rec.part_no, rec.version, rec.material_name);
        IF new_key IS DISTINCT FROM rec.dedup_key THEN
            IF EXISTS (
                SELECT 1
                  FROM order_delivery_ticket_mes_request o
                 WHERE o.id <> rec.id
                   AND o.dedup_key = new_key
                   AND o.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
            ) THEN
                RAISE EXCEPTION 'MES material identity % is already held by another active request', new_key;
            END IF;
            UPDATE order_delivery_ticket_mes_request
               SET dedup_key = new_key,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = rec.id;
        END IF;
    END LOOP;

    -- 2) 刷新料号台账行没有去重键。留空等于身份仲裁失效，比推定值更危险：
    --    推定错的代价有限——真正的旧身份若未被锁住，新提交会被 MES 以
    --    1_030_900_002 / 1_030_900_003 拒绝，而这两个码已按「安全等待」处理。
    --    同身份已有活动行时不覆盖，只追加核对提示。
    FOR rec IN
        SELECT r.id, t.part_no, t.version, t.material_name
          FROM order_delivery_ticket_mes_request r
          JOIN order_delivery_ticket t ON t.id = r.ticket_id
         WHERE r.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
           AND (r.dedup_key IS NULL OR btrim(r.dedup_key) = '')
         ORDER BY r.id
    LOOP
        new_key := qpo_mes_material_identity(rec.part_no, rec.version, rec.material_name);
        IF EXISTS (
            SELECT 1
              FROM order_delivery_ticket_mes_request o
             WHERE o.dedup_key = new_key
               AND o.request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
        ) THEN
            skipped_count := skipped_count + 1;
            UPDATE order_delivery_ticket_mes_request
               SET response_message = left(
                       coalesce(nullif(btrim(response_message), ''), '') ||
                       '未回填去重键：同图号版本材质已有活动请求，请按原 bizId 人工核对', 500),
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = rec.id;
        ELSE
            UPDATE order_delivery_ticket_mes_request
               SET dedup_key = new_key,
                   request_message = left(
                       coalesce(nullif(btrim(request_message), ''), '') ||
                       '；去重键按提单当前图号版本材质推定回填', 500),
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = rec.id;
        END IF;
    END LOOP;

    -- 3) 提单行：只处理仍在活动状态且已写过去重键的行（主请求与 follower 都覆盖）。
    FOR rec IN
        SELECT t.id, t.mes_dedup_key, t.part_no, t.version, t.material_name
          FROM order_delivery_ticket t
         WHERE t.mes_dedup_key IS NOT NULL
           AND btrim(t.mes_dedup_key) <> ''
           AND t.mes_sync_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
         ORDER BY t.id
    LOOP
        new_key := qpo_mes_material_identity(rec.part_no, rec.version, rec.material_name);
        IF new_key IS DISTINCT FROM rec.mes_dedup_key THEN
            UPDATE order_delivery_ticket
               SET mes_dedup_key = new_key
             WHERE id = rec.id;
        END IF;
    END LOOP;

    -- 4) 等待行：按等待方提单自身的身份回填。正常情况下与主请求身份一致
    --    （2026-10-29 核验 waiter/master 不一致 0 行），因此不会改变等待关系。
    FOR rec IN
        SELECT w.id, w.dedup_key, t.part_no, t.version, t.material_name
          FROM order_delivery_ticket_mes_waiter w
          JOIN order_delivery_ticket t ON t.id = w.waiter_ticket_id
         WHERE w.wait_status IN ('WAITING', 'PROCESSING', 'CALLBACK_PENDING')
         ORDER BY w.id
    LOOP
        new_key := qpo_mes_material_identity(rec.part_no, rec.version, rec.material_name);
        IF new_key IS DISTINCT FROM rec.dedup_key THEN
            UPDATE order_delivery_ticket_mes_waiter
               SET dedup_key = new_key,
                   updated_at = CURRENT_TIMESTAMP
             WHERE id = rec.id;
        END IF;
    END LOOP;

    IF skipped_count > 0 THEN
        RAISE NOTICE 'MES material identity backfill skipped % refresh request(s); reconcile manually', skipped_count;
    END IF;
END $$;

-- 闸门：回填后的键必须装得进新列宽，且格式统一。
DO $$
DECLARE
    too_long integer;
    bad_format integer;
BEGIN
    SELECT count(*) INTO too_long
      FROM order_delivery_ticket_mes_request
     WHERE dedup_key IS NOT NULL AND length(dedup_key) > 512;
    SELECT count(*) INTO bad_format
      FROM order_delivery_ticket_mes_request
     WHERE dedup_key IS NOT NULL
       AND btrim(dedup_key) <> ''
       AND request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN')
       AND dedup_key NOT LIKE 'v3|%';
    IF too_long > 0 OR bad_format > 0 THEN
        RAISE EXCEPTION 'MES material dedup key backfill incomplete: tooLong=%, badFormat=%',
            too_long, bad_format;
    END IF;
END $$;

DROP FUNCTION qpo_mes_material_identity(text, text, text);
