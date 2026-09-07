-- QPO local common-material flag. MES owns material identity, QPO owns this usability marker.

ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS common_material BOOLEAN NOT NULL DEFAULT FALSE;

CREATE INDEX IF NOT EXISTS idx_dict_material_common_enabled_type_sort
    ON dict_material (enabled, common_material, material_type_id, sort_no, id);

CREATE INDEX IF NOT EXISTS idx_dict_material_common_enabled_u9_category_sort
    ON dict_material (enabled, common_material, category_name, material_type_id, sort_no, id)
    WHERE category_name IS NOT NULL;
