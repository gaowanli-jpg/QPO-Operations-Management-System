ALTER TABLE order_delivery_ticket
    ADD COLUMN IF NOT EXISTS mes_dedup_key VARCHAR(220),
    ADD COLUMN IF NOT EXISTS mes_request_role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS mes_master_ticket_id INTEGER,
    ADD COLUMN IF NOT EXISTS mes_master_request_id VARCHAR(80);

ALTER TABLE order_delivery_ticket_mes_request
    ADD COLUMN IF NOT EXISTS dedup_key VARCHAR(220),
    ADD COLUMN IF NOT EXISTS request_role VARCHAR(20),
    ADD COLUMN IF NOT EXISTS follower_count INTEGER DEFAULT 0;

CREATE UNIQUE INDEX IF NOT EXISTS uk_order_delivery_ticket_mes_request_id
    ON order_delivery_ticket(mes_request_id)
    WHERE mes_request_id IS NOT NULL AND TRIM(mes_request_id) <> '';

CREATE INDEX IF NOT EXISTS idx_delivery_ticket_mes_dedup_key
    ON order_delivery_ticket(mes_dedup_key, mes_sync_status, id)
    WHERE mes_dedup_key IS NOT NULL AND TRIM(mes_dedup_key) <> '';

CREATE INDEX IF NOT EXISTS idx_delivery_ticket_mes_master_request
    ON order_delivery_ticket(mes_master_request_id, id)
    WHERE mes_master_request_id IS NOT NULL AND TRIM(mes_master_request_id) <> '';

CREATE INDEX IF NOT EXISTS idx_delivery_mes_request_dedup_status
    ON order_delivery_ticket_mes_request(dedup_key, request_status, id DESC)
    WHERE dedup_key IS NOT NULL AND TRIM(dedup_key) <> '';

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_mes_request_active_dedup
    ON order_delivery_ticket_mes_request(dedup_key)
    WHERE request_status IN ('PROCESSING', 'CALLBACK_PENDING')
      AND dedup_key IS NOT NULL
      AND TRIM(dedup_key) <> '';

CREATE TABLE IF NOT EXISTS order_delivery_ticket_mes_waiter (
    id BIGSERIAL PRIMARY KEY,
    dedup_key VARCHAR(220) NOT NULL,
    master_ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    master_mes_request_id VARCHAR(80) NOT NULL,
    waiter_ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    wait_status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    material_no VARCHAR(100),
    wait_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_delivery_mes_waiter_status CHECK (
        wait_status IN ('WAITING', 'CALLBACK_PENDING', 'SUCCESS', 'FAILED', 'STALE', 'IGNORED')
    ),
    CONSTRAINT chk_delivery_mes_waiter_distinct_ticket CHECK (master_ticket_id <> waiter_ticket_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_mes_waiter_active_ticket
    ON order_delivery_ticket_mes_waiter(waiter_ticket_id)
    WHERE wait_status IN ('WAITING', 'CALLBACK_PENDING');

CREATE INDEX IF NOT EXISTS idx_delivery_mes_waiter_master_request
    ON order_delivery_ticket_mes_waiter(master_mes_request_id, wait_status, id);

CREATE INDEX IF NOT EXISTS idx_delivery_mes_waiter_dedup
    ON order_delivery_ticket_mes_waiter(dedup_key, wait_status, id);

COMMENT ON COLUMN order_delivery_ticket.mes_dedup_key IS 'Part number + version key used to deduplicate MES material number requests.';
COMMENT ON COLUMN order_delivery_ticket.mes_request_role IS 'MES request role for the ticket: MASTER, FOLLOWER, or REUSED.';
COMMENT ON COLUMN order_delivery_ticket.mes_master_ticket_id IS 'Master delivery ticket id followed by this ticket for deduplicated MES callback waiting.';
COMMENT ON COLUMN order_delivery_ticket.mes_master_request_id IS 'Master MES request id followed by this ticket for deduplicated MES callback waiting.';
COMMENT ON COLUMN order_delivery_ticket_mes_request.dedup_key IS 'Part number + version key for the real MES request.';
COMMENT ON COLUMN order_delivery_ticket_mes_request.request_role IS 'Request role; stage 1 stores MASTER for real MES requests.';
COMMENT ON COLUMN order_delivery_ticket_mes_request.follower_count IS 'Number of delivery ticket rows following this master request.';
COMMENT ON TABLE order_delivery_ticket_mes_waiter IS 'Follower delivery ticket rows waiting for a master MES material number request with the same part number and version.';
