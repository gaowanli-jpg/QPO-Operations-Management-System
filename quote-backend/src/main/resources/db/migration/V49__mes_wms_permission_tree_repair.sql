WITH delivered_item AS (
    SELECT id
    FROM system_permission
    WHERE permission_code = 'market:delivered-item:view'
)
UPDATE system_permission child
SET parent_id = delivered_item.id,
    updated_at = CURRENT_TIMESTAMP
FROM delivered_item
WHERE child.permission_code IN (
        'market:delivered-item:file-preview',
        'market:delivered-item:mes-sync',
        'market:delivered-item:wms-allocate'
    )
  AND child.parent_id IS DISTINCT FROM delivered_item.id;

UPDATE system_permission
SET permission_type = 'MENU',
    route_path = '/delivered-items',
    component_key = 'DeliveredItemManage',
    visible = TRUE,
    enabled = TRUE,
    assignable = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code = 'market:delivered-item:view';

UPDATE system_permission
SET permission_type = 'MENU',
    route_path = '/locked-items',
    component_key = 'LockedItemManage',
    visible = TRUE,
    enabled = TRUE,
    assignable = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code = 'market:locked-item:view';

UPDATE system_permission
SET enabled = TRUE,
    assignable = TRUE,
    updated_at = CURRENT_TIMESTAMP
WHERE permission_code IN (
    'market:delivered-item:file-preview',
    'market:delivered-item:mes-sync',
    'market:delivered-item:wms-allocate'
);
