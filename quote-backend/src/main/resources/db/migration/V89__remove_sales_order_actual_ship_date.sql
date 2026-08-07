ALTER TABLE sales_order_item
    DROP COLUMN IF EXISTS actual_ship_date;

ALTER TABLE item_quote_version
    DROP COLUMN IF EXISTS actual_ship_date;
