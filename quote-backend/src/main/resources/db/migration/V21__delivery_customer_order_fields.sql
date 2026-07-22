ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS customer_order_no VARCHAR(100);
ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS customer_project_no VARCHAR(100);
