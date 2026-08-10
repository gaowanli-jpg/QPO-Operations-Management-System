-- Retire the manual sales-match/anomaly-governance module.
-- Sales order import keeps its internal automatic snapshot completion; no user-facing permission remains.

DELETE FROM system_role_permission
WHERE permission_id IN (
    SELECT id
    FROM system_permission
    WHERE permission_code LIKE 'market:sales-match:%'
       OR route_path = '/sales-match'
       OR component_key = 'SalesMatch'
);

DELETE FROM system_permission
WHERE parent_id IN (
    SELECT id
    FROM system_permission
    WHERE permission_code LIKE 'market:sales-match:%'
       OR route_path = '/sales-match'
       OR component_key = 'SalesMatch'
)
   OR permission_code LIKE 'market:sales-match:%'
   OR route_path = '/sales-match'
   OR component_key = 'SalesMatch';
