DELETE FROM system_role_permission
WHERE permission_id IN (
    SELECT id FROM system_permission WHERE permission_code = 'market:rfq:order-type-select'
);

DELETE FROM system_permission
WHERE permission_code = 'market:rfq:order-type-select';