-- One-time QPO order business data cleanup before the U9 customer master cutover.
-- Run only after a PostgreSQL backup is completed and the target environment is confirmed.
-- This script is intentionally outside Flyway and must be executed manually.
--
-- Required confirmation in the same SQL session:
--   SET qpo.confirm_clear_order_business_data = 'YES';

DO $$
BEGIN
    IF current_setting('qpo.confirm_clear_order_business_data', true) IS DISTINCT FROM 'YES' THEN
        RAISE EXCEPTION 'Refusing to clear QPO order business data. Set qpo.confirm_clear_order_business_data = YES in this session first.';
    END IF;
END $$;

TRUNCATE TABLE
    order_delivery_ticket_mes_waiter,
    order_delivery_ticket_mes_request,
    order_delivery_ticket_allocation,
    order_delivery_ticket,
    sales_order_item_assembly_relation,
    sales_order_source_rfq,
    sales_order_item,
    sales_order_header,
    sales_order_import_record,
    sales_order_import_batch,
    item_assembly_relation,
    item_quote_version,
    order_rfq
RESTART IDENTITY CASCADE;

DELETE FROM system_task
WHERE task_type IN (
    'EXPORT_QUOTE',
    'REFRESH_ORDER_QUOTES',
    'RELEASE_DELIVERY_TICKET',
    'SALES_ORDER_IMPORT',
    'U9_SALES_ORDER_SYNC',
    'MES_MATERIAL_RESOLVE',
    'MES_PRODUCTION_DISPATCH',
    'WMS_ALLOCATION_BATCH'
);
