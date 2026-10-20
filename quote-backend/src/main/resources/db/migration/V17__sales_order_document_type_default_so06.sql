ALTER TABLE sales_order_header
    ALTER COLUMN document_type_code SET DEFAULT 'SO06'::character varying;
