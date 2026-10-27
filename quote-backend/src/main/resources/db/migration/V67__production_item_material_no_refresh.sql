-- 待产零件「刷新料号」异常修复权限点。
--
-- 背景：MES 侧删除旧料号并重新建号（或人工填错料号后被更正）时，QPO 已确认的
-- material_no 无法刷新，生产下发会持续报「以下料号不存在」。该能力属于异常修复，
-- 与正常「批量获取物料号」分离授权，只授予系统管理员与市场主管。

SET search_path TO "${flyway:defaultSchema}";

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM system_permission
        WHERE id = 240
          AND permission_code <> 'market:production-item:material-no-refresh'
    ) OR EXISTS (
        SELECT 1
        FROM system_permission
        WHERE permission_code = 'market:production-item:material-no-refresh'
          AND id <> 240
    ) THEN
        RAISE EXCEPTION 'Production item material no refresh permission ID or code is already assigned';
    END IF;
END $$;

INSERT INTO system_permission (
    id, parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled,
    created_at, updated_at, assignable
) VALUES
    (240, 199, 'BUTTON', 'market:production-item:material-no-refresh', '待产零件刷新料号',
     NULL, NULL, NULL, 133, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, true)
ON CONFLICT (id) DO UPDATE SET
    parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_code = EXCLUDED.permission_code,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = EXCLUDED.enabled,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE permission.permission_code = 'market:production-item:material-no-refresh'
  AND role.role_code IN ('SUPER_ADMIN', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

SELECT setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 240), TRUE);
