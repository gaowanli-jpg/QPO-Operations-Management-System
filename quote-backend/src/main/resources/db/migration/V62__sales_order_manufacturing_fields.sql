ALTER TABLE item_quote_version
    ADD COLUMN manufacturing_process_type VARCHAR(120),
    ADD COLUMN reserved_field_1 VARCHAR(120),
    ADD COLUMN reserved_field_2 VARCHAR(120),
    ADD COLUMN reserved_field_3 VARCHAR(120);

ALTER TABLE sales_order_header
    ADD COLUMN manufacturing_process_type VARCHAR(120),
    ADD COLUMN reserved_field_1 VARCHAR(120),
    ADD COLUMN reserved_field_2 VARCHAR(120),
    ADD COLUMN reserved_field_3 VARCHAR(120);

ALTER TABLE sales_order_item
    ADD COLUMN manufacturing_process_type VARCHAR(120),
    ADD COLUMN reserved_field_1 VARCHAR(120),
    ADD COLUMN reserved_field_2 VARCHAR(120),
    ADD COLUMN reserved_field_3 VARCHAR(120);
