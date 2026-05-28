ALTER TABLE order_delivery_ticket ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) DEFAULT '普通件';
ALTER TABLE order_process_sheet ADD COLUMN IF NOT EXISTS item_type VARCHAR(20) DEFAULT '普通件';

UPDATE order_delivery_ticket ticket
SET item_type = CASE
    WHEN item.quote_mode = 'ASSEMBLY' THEN '组合件'
    ELSE '普通件'
END
FROM item_quote_version item
WHERE ticket.item_id = item.id;

UPDATE order_process_sheet sheet
SET item_type = CASE
    WHEN item.quote_mode = 'ASSEMBLY' THEN '组合件'
    ELSE '普通件'
END
FROM item_quote_version item
WHERE sheet.item_id = item.id;

UPDATE order_delivery_ticket SET item_type = '普通件' WHERE item_type IS NULL OR TRIM(item_type) = '';
UPDATE order_process_sheet SET item_type = '普通件' WHERE item_type IS NULL OR TRIM(item_type) = '';
