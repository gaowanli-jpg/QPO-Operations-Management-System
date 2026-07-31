ALTER TABLE sales_order_header ADD COLUMN IF NOT EXISTS u9_sync_message TEXT;

COMMENT ON COLUMN sales_order_header.u9_sync_message IS
    'U9 sync failure or success summary only; do not store token, secret, or full external request body.';
