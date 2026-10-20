-- Support server-side U9 category filtering in material pickers without loading all materials.

CREATE INDEX IF NOT EXISTS idx_dict_material_u9_category_enabled
    ON dict_material (enabled, category_name, material_type_id, sort_no, id)
    WHERE category_name IS NOT NULL;
