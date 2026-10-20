-- Material prices are now maintained as three fixed price specs per material.
-- Runtime code no longer reads dict_material.plate_price/bar_price/tube_price.

CREATE TABLE IF NOT EXISTS dict_material_price_spec (
    id BIGSERIAL PRIMARY KEY,
    material_id BIGINT NOT NULL,
    spec_code VARCHAR(20) NOT NULL,
    spec_name VARCHAR(40) NOT NULL,
    unified_price NUMERIC(12,2),
    plate_price NUMERIC(12,2),
    bar_price NUMERIC(12,2),
    tube_price NUMERIC(12,2),
    sort_no INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_material_price_spec_material
        FOREIGN KEY (material_id) REFERENCES dict_material(id) ON DELETE CASCADE,
    CONSTRAINT uk_material_price_spec_material_code UNIQUE (material_id, spec_code),
    CONSTRAINT ck_material_price_spec_code CHECK (spec_code IN ('SPEC_1', 'SPEC_2', 'SPEC_3')),
    CONSTRAINT ck_material_price_spec_unified_non_negative CHECK (unified_price IS NULL OR unified_price >= 0),
    CONSTRAINT ck_material_price_spec_plate_non_negative CHECK (plate_price IS NULL OR plate_price >= 0),
    CONSTRAINT ck_material_price_spec_bar_non_negative CHECK (bar_price IS NULL OR bar_price >= 0),
    CONSTRAINT ck_material_price_spec_tube_non_negative CHECK (tube_price IS NULL OR tube_price >= 0)
);

CREATE INDEX IF NOT EXISTS idx_material_price_spec_material
    ON dict_material_price_spec(material_id, sort_no, id);

INSERT INTO dict_material_price_spec(material_id, spec_code, spec_name, unified_price, plate_price, bar_price, tube_price, sort_no)
SELECT id, 'SPEC_1', '规格一',
       COALESCE(NULLIF(plate_price, 0), NULLIF(bar_price, 0), NULLIF(tube_price, 0)),
       plate_price, bar_price, tube_price, 10
FROM dict_material
ON CONFLICT (material_id, spec_code) DO NOTHING;

INSERT INTO dict_material_price_spec(material_id, spec_code, spec_name, sort_no)
SELECT id, 'SPEC_2', '规格二', 20
FROM dict_material
ON CONFLICT (material_id, spec_code) DO NOTHING;

INSERT INTO dict_material_price_spec(material_id, spec_code, spec_name, sort_no)
SELECT id, 'SPEC_3', '规格三', 30
FROM dict_material
ON CONFLICT (material_id, spec_code) DO NOTHING;

ALTER TABLE dict_material
    DROP COLUMN IF EXISTS plate_price,
    DROP COLUMN IF EXISTS bar_price,
    DROP COLUMN IF EXISTS tube_price;
