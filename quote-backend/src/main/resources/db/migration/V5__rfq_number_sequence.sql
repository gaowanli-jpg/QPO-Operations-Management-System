CREATE TABLE IF NOT EXISTS rfq_no_sequence (
    rfq_date DATE PRIMARY KEY,
    last_seq INTEGER NOT NULL DEFAULT 0,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint c
        JOIN pg_class t ON t.oid = c.conrelid
        JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum = ANY(c.conkey)
        WHERE t.relname = 'order_rfq'
          AND c.contype = 'u'
          AND array_length(c.conkey, 1) = 1
          AND a.attname = 'rfq_no'
    ) THEN
        ALTER TABLE order_rfq ADD CONSTRAINT uk_order_rfq_rfq_no UNIQUE (rfq_no);
    END IF;
END $$;
