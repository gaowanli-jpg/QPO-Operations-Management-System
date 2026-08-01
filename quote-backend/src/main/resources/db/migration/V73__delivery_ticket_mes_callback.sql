ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS mes_request_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS mes_external_task_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS mes_callback_received_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS mes_callback_message TEXT;

CREATE UNIQUE INDEX IF NOT EXISTS uk_order_delivery_ticket_mes_request_id
    ON order_delivery_ticket(mes_request_id)
    WHERE mes_request_id IS NOT NULL AND TRIM(mes_request_id) <> '';

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_mes_external_task
    ON order_delivery_ticket(mes_external_task_id, id)
    WHERE mes_external_task_id IS NOT NULL AND TRIM(mes_external_task_id) <> '';

CREATE TABLE IF NOT EXISTS order_delivery_ticket_mes_request (
    id BIGSERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    mes_request_id VARCHAR(80) NOT NULL,
    system_task_id BIGINT,
    request_status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    mes_external_task_id VARCHAR(120),
    material_no VARCHAR(100),
    request_message TEXT,
    response_message TEXT,
    callback_message TEXT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    callback_received_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_mes_request_status CHECK (
        request_status IN ('PROCESSING', 'CALLBACK_PENDING', 'SUCCESS', 'FAILED', 'STALE', 'IGNORED')
    )
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_mes_request_request_id
    ON order_delivery_ticket_mes_request(mes_request_id);

CREATE INDEX IF NOT EXISTS idx_delivery_mes_request_ticket
    ON order_delivery_ticket_mes_request(ticket_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_mes_request_external_task
    ON order_delivery_ticket_mes_request(mes_external_task_id, id)
    WHERE mes_external_task_id IS NOT NULL AND TRIM(mes_external_task_id) <> '';

COMMENT ON COLUMN order_delivery_ticket.mes_request_id IS 'Latest QPO-generated MES material request id; primary correlation key for MES response and callback.';
COMMENT ON COLUMN order_delivery_ticket.mes_external_task_id IS 'MES-side task id or trace id returned for material number processing.';
COMMENT ON COLUMN order_delivery_ticket.mes_callback_received_at IS 'Time when the latest accepted MES material callback was received.';
COMMENT ON COLUMN order_delivery_ticket.mes_callback_message IS 'Short MES callback summary; never store full callback payload or secrets.';
COMMENT ON TABLE order_delivery_ticket_mes_request IS 'Request-level ledger for MES material number resolve calls and callbacks.';
