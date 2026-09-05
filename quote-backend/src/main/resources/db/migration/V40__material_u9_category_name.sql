-- Store the U9 category name received from MES as a separate material dimension.
-- Keep material_category unchanged; it remains the existing QPO/MES material classification field.

ALTER TABLE dict_material ADD COLUMN IF NOT EXISTS category_name VARCHAR(120);
