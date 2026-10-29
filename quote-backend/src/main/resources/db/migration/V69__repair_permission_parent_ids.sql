-- 修复角色授权保存失败：历史迁移把 parent_id 写成了 Java 权限目录里的常量 id。
--
-- V57 的 market:sales-order:wms-allocate 用了 parent_id = 190，V66/V67 的两个待产零件按钮
-- 用了 parent_id = 199。这些 id 取自 PermissionService.FALLBACK_CATALOG，而真实数据库由
-- V2 基线种子生成，父权限实际 id 是 market:sales-order:view = 150、
-- market:production-item:view = 159，199 在库中根本不存在。
--
-- 后果：角色一旦勾选这些权限，SystemRoleService.addPermissionWithParents 查不到父权限就抛
-- "Parent permission does not exist"，整份角色授权保存直接失败，表现为「勾选后保存不生效」。
--
-- 这里统一按 permission_code 解析父权限，不再依赖任何写死的 id。

SET search_path TO "${flyway:defaultSchema}";

DO $$
DECLARE
    missing INTEGER;
BEGIN
    SELECT COUNT(*) INTO missing
    FROM (VALUES
        ('market:sales-order:wms-allocate', 'market:sales-order:view'),
        ('market:production-item:mes-production-schedule', 'market:production-item:view'),
        ('market:production-item:material-no-refresh', 'market:production-item:view')
    ) AS target(permission_code, parent_code)
    WHERE EXISTS (
            SELECT 1 FROM system_permission child WHERE child.permission_code = target.permission_code
        )
      AND NOT EXISTS (
            SELECT 1 FROM system_permission parent WHERE parent.permission_code = target.parent_code
        );

    IF missing > 0 THEN
        RAISE EXCEPTION 'Cannot repair permission parent_id: % parent permission code(s) are missing', missing;
    END IF;
END $$;

UPDATE system_permission child
SET parent_id = parent.id,
    updated_at = CURRENT_TIMESTAMP
FROM system_permission parent
WHERE (child.permission_code, parent.permission_code) IN (
        ('market:sales-order:wms-allocate', 'market:sales-order:view'),
        ('market:production-item:mes-production-schedule', 'market:production-item:view'),
        ('market:production-item:material-no-refresh', 'market:production-item:view')
    )
  AND child.parent_id IS DISTINCT FROM parent.id;

-- 兜底：任何仍指向不存在父权限的行一律置空，让权限树退回到分组层级，
-- 同时避免同类问题再次把整份角色授权保存卡死。
UPDATE system_permission child
SET parent_id = NULL,
    updated_at = CURRENT_TIMESTAMP
WHERE child.parent_id IS NOT NULL
  AND child.parent_id > 0
  AND NOT EXISTS (
        SELECT 1 FROM system_permission parent WHERE parent.id = child.parent_id
  );
