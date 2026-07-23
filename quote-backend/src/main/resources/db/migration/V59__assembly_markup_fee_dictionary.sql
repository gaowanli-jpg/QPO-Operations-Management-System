CREATE TABLE IF NOT EXISTS dict_assembly_markup_fee (
    id SERIAL PRIMARY KEY,
    fee_name VARCHAR(100) NOT NULL UNIQUE,
    rate_percent NUMERIC(10, 2) DEFAULT 0,
    sort_no INTEGER,
    enabled BOOLEAN DEFAULT TRUE
);

INSERT INTO dict_assembly_markup_fee (fee_name, rate_percent, sort_no, enabled)
SELECT fee_name, rate_percent, sort_no, enabled
FROM dict_markup_fee
WHERE fee_name NOT LIKE '%运输%'
  AND fee_name NOT LIKE '%运费%'
ON CONFLICT (fee_name) DO NOTHING;
