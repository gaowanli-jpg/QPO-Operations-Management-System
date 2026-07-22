DELETE FROM system_role_permission
WHERE permission_id IN (
    SELECT id
    FROM system_permission
    WHERE permission_code IN ('common:customer:select', 'common:order:select')
);

DELETE FROM system_permission
WHERE permission_code IN ('common:customer:select', 'common:order:select');
