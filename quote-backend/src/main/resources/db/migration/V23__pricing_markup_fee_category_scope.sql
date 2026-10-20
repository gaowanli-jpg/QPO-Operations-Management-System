-- Unified pricing markup fees by scope and category.
-- Append-only migration; do not modify baseline migrations.

CREATE TABLE dict_pricing_markup_fee (
    id bigserial PRIMARY KEY,
    pricing_scope varchar(20) NOT NULL,
    fee_category varchar(32) NOT NULL,
    fee_name varchar(100) NOT NULL,
    rate_percent numeric(12,4) NOT NULL DEFAULT 0,
    sort_no integer NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT true,
    system_required boolean NOT NULL DEFAULT false,
    created_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at timestamp without time zone NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_dict_pricing_markup_fee_scope
        CHECK (pricing_scope IN ('NORMAL', 'ASSEMBLY')),
    CONSTRAINT ck_dict_pricing_markup_fee_category
        CHECK (fee_category IN ('BASIC_PERCENT', 'TAX', 'CUSTOMER_WEIGHT')),
    CONSTRAINT ck_dict_pricing_markup_fee_rate
        CHECK (rate_percent >= 0),
    CONSTRAINT ck_dict_pricing_markup_fee_name_not_blank
        CHECK (btrim(fee_name) <> ''),
    CONSTRAINT uk_dict_pricing_markup_fee_scope_category_name
        UNIQUE (pricing_scope, fee_category, fee_name)
);

CREATE INDEX idx_dict_pricing_markup_fee_scope_category_sort
    ON dict_pricing_markup_fee(pricing_scope, fee_category, enabled, sort_no, id);

INSERT INTO dict_pricing_markup_fee (
    pricing_scope,
    fee_category,
    fee_name,
    rate_percent,
    sort_no,
    enabled,
    system_required,
    created_at,
    updated_at
)
SELECT
    'NORMAL' AS pricing_scope,
    CASE
        WHEN lower(fee_name) LIKE '%tax%' OR lower(fee_name) LIKE '%vat%' OR position(U&'\7A0E' in fee_name) > 0
            THEN 'TAX'
        ELSE 'BASIC_PERCENT'
    END AS fee_category,
    fee_name,
    COALESCE(rate_percent, 0),
    COALESCE(sort_no, 0),
    COALESCE(enabled, true),
    CASE
        WHEN lower(fee_name) LIKE '%tax%' OR lower(fee_name) LIKE '%vat%' OR position(U&'\7A0E' in fee_name) > 0
            THEN true
        ELSE false
    END AS system_required,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dict_markup_fee;

INSERT INTO dict_pricing_markup_fee (
    pricing_scope,
    fee_category,
    fee_name,
    rate_percent,
    sort_no,
    enabled,
    system_required,
    created_at,
    updated_at
)
SELECT
    'ASSEMBLY' AS pricing_scope,
    CASE
        WHEN lower(fee_name) LIKE '%tax%' OR lower(fee_name) LIKE '%vat%' OR position(U&'\7A0E' in fee_name) > 0
            THEN 'TAX'
        ELSE 'BASIC_PERCENT'
    END AS fee_category,
    fee_name,
    COALESCE(rate_percent, 0),
    COALESCE(sort_no, 0),
    COALESCE(enabled, true),
    CASE
        WHEN lower(fee_name) LIKE '%tax%' OR lower(fee_name) LIKE '%vat%' OR position(U&'\7A0E' in fee_name) > 0
            THEN true
        ELSE false
    END AS system_required,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM dict_assembly_markup_fee;

INSERT INTO dict_pricing_markup_fee (
    pricing_scope,
    fee_category,
    fee_name,
    rate_percent,
    sort_no,
    enabled,
    system_required,
    created_at,
    updated_at
) VALUES
    ('NORMAL', 'CUSTOMER_WEIGHT', U&'\5BA2\6237\6743\91CD', 0, 100, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
    ('ASSEMBLY', 'CUSTOMER_WEIGHT', U&'\5BA2\6237\6743\91CD', 0, 100, false, true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (pricing_scope, fee_category, fee_name) DO UPDATE SET
    system_required = true,
    updated_at = CURRENT_TIMESTAMP;

UPDATE dict_pricing_markup_fee
SET system_required = true,
    updated_at = CURRENT_TIMESTAMP
WHERE fee_category = 'TAX';

COMMENT ON TABLE dict_pricing_markup_fee IS 'Pricing markup fees unified by pricing scope and fee category';
COMMENT ON COLUMN dict_pricing_markup_fee.pricing_scope IS 'NORMAL or ASSEMBLY';
COMMENT ON COLUMN dict_pricing_markup_fee.fee_category IS 'BASIC_PERCENT, TAX, or CUSTOMER_WEIGHT';
COMMENT ON COLUMN dict_pricing_markup_fee.system_required IS 'System row that cannot be deleted, such as tax and customer weight';

DROP TABLE IF EXISTS dict_markup_fee;
DROP TABLE IF EXISTS dict_assembly_markup_fee;
DROP SEQUENCE IF EXISTS dict_markup_fee_id_seq;
DROP SEQUENCE IF EXISTS dict_assembly_markup_fee_id_seq;
