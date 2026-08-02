CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_active_flow_id
    ON order_delivery_ticket(id DESC)
    WHERE NULLIF(BTRIM(material_no), '') IS NULL
       OR COALESCE(mes_sync_status, 'PENDING') <> 'SUCCESS'
       OR COALESCE(wms_allocate_status, 'PENDING') <> 'SUCCESS';

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_completed_flow_id
    ON order_delivery_ticket(mes_sync_status, wms_allocate_status, id DESC)
    WHERE NULLIF(BTRIM(material_no), '') IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_ticket
    ON order_delivery_ticket_allocation(ticket_id, id);
