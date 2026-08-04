CREATE INDEX IF NOT EXISTS idx_delivery_allocation_mes_production_status_id
    ON order_delivery_ticket_allocation(mes_production_status, id DESC);
