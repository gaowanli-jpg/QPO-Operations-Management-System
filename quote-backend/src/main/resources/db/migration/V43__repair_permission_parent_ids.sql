-- Repair permission parent links that were introduced by post-baseline migrations
-- using fallback-catalog ids instead of the active database permission ids.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM (VALUES
            ('market:order:update-item', 'market:order:view'),
            ('market:delivered-item:discard', 'market:delivered-item:view'),
            ('market:delivered-item:restore', 'market:delivered-item:view'),
            ('market:production-item:discard', 'market:production-item:view'),
            ('market:production-item:restore', 'market:production-item:view'),
            ('market:sales-order:production-task-notice-export', 'market:sales-order:view')
        ) AS mapping(child_code, parent_code)
        LEFT JOIN system_permission child ON child.permission_code = mapping.child_code
        LEFT JOIN system_permission parent ON parent.permission_code = mapping.parent_code
        WHERE child.id IS NULL OR parent.id IS NULL
    ) THEN
        RAISE EXCEPTION 'Cannot repair permission parent ids: required permission code is missing';
    END IF;
END $$;

WITH mapping(child_code, parent_code) AS (
    VALUES
        ('market:order:update-item', 'market:order:view'),
        ('market:delivered-item:discard', 'market:delivered-item:view'),
        ('market:delivered-item:restore', 'market:delivered-item:view'),
        ('market:production-item:discard', 'market:production-item:view'),
        ('market:production-item:restore', 'market:production-item:view'),
        ('market:sales-order:production-task-notice-export', 'market:sales-order:view')
), resolved AS (
    SELECT child.id AS child_id, parent.id AS parent_id
    FROM mapping
    JOIN system_permission child ON child.permission_code = mapping.child_code
    JOIN system_permission parent ON parent.permission_code = mapping.parent_code
)
UPDATE system_permission child
SET parent_id = resolved.parent_id,
    updated_at = CURRENT_TIMESTAMP
FROM resolved
WHERE child.id = resolved.child_id
  AND child.parent_id IS DISTINCT FROM resolved.parent_id;
