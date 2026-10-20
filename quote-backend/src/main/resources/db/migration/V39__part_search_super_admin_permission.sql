-- Grant the part search menu to super administrators for databases where V37 already ran.

INSERT INTO system_role_permission (role_id, permission_id, created_at)
SELECT role.id, permission.id, CURRENT_TIMESTAMP
FROM system_role role
CROSS JOIN system_permission permission
WHERE role.role_code = 'SUPER_ADMIN'
  AND permission.permission_code = 'market:part-search:view'
ON CONFLICT (role_id, permission_id) DO NOTHING;
