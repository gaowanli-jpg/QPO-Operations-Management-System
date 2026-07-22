UPDATE system_role
SET data_scope = 'ALL',
    updated_at = CURRENT_TIMESTAMP
WHERE UPPER(data_scope) IN ('DEPT', 'DEPT_AND_CHILD', 'CUSTOM');

DELETE FROM system_role_data_scope_dept;
