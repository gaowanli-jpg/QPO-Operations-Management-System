ALTER TABLE order_rfq
    ADD COLUMN IF NOT EXISTS drawing_folder_url character varying(800),
    ADD COLUMN IF NOT EXISTS drawing_folder_name character varying(255),
    ADD COLUMN IF NOT EXISTS drawing_folder_file_count integer;

ALTER TABLE sales_order_header
    ADD COLUMN IF NOT EXISTS drawing_folder_url character varying(800),
    ADD COLUMN IF NOT EXISTS drawing_folder_name character varying(255),
    ADD COLUMN IF NOT EXISTS drawing_folder_file_count integer;
