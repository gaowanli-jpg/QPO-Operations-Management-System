DROP INDEX IF EXISTS idx_aps_work_item_rfq_no;
DROP INDEX IF EXISTS idx_aps_material_shortage_project;

ALTER TABLE aps_work_item DROP COLUMN IF EXISTS rfq_no;
ALTER TABLE aps_material_lock DROP COLUMN IF EXISTS rfq_no;
ALTER TABLE aps_material_shortage DROP COLUMN IF EXISTS rfq_no;
