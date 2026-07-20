ALTER TABLE aps_work_item
    ALTER COLUMN process_snapshot_source SET DEFAULT 'ORDER_DELIVERY_TICKET';

UPDATE aps_work_item
SET process_snapshot_source = 'ORDER_DELIVERY_TICKET'
WHERE process_snapshot_source = 'ITEM_QUOTE_VERSION';

ALTER TABLE aps_work_item
    DROP COLUMN IF EXISTS erp_process_sheet_id;
