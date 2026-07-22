ALTER TABLE item_quote_version ALTER COLUMN version DROP DEFAULT;
UPDATE item_quote_version SET version = '' WHERE version IS NULL;

ALTER TABLE inventory_master ALTER COLUMN version DROP DEFAULT;
UPDATE inventory_master SET version = '' WHERE version IS NULL;

UPDATE order_delivery_ticket SET version = '' WHERE version IS NULL;
UPDATE order_process_sheet SET version = '' WHERE version IS NULL;
