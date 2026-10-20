-- Consolidate material density to one QPO-maintained override field.
-- MES density remains in mes_density_raw and is used only as a pricing fallback.

UPDATE dict_material
SET density = COALESCE(NULLIF(density, 0),
        NULLIF(plate_density, 0),
        NULLIF(bar_density, 0),
        NULLIF(tube_density, 0)
    )
WHERE density IS NULL
   OR density = 0;

ALTER TABLE dict_material
    DROP COLUMN IF EXISTS plate_density,
    DROP COLUMN IF EXISTS bar_density,
    DROP COLUMN IF EXISTS tube_density;
