ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS internal_project_no VARCHAR(100);
ALTER TABLE order_process_sheet ADD COLUMN IF NOT EXISTS internal_project_no VARCHAR(100);
