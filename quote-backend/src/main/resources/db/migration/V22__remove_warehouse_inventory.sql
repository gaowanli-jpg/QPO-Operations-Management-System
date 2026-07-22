UPDATE erp_user
SET enabled = FALSE
WHERE LOWER(username) = 'warehouse'
   OR UPPER(role) = 'WAREHOUSE';

DROP TABLE IF EXISTS inventory_pending_stock CASCADE;
DROP TABLE IF EXISTS inventory_master CASCADE;
