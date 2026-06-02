CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_rfq_part_version
    ON order_delivery_ticket(rfq_id, part_no, version);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_rfq_part_version
    ON item_quote_version(rfq_id, part_no, version);

CREATE TABLE IF NOT EXISTS system_task (
    id BIGSERIAL PRIMARY KEY,
    task_type VARCHAR(80) NOT NULL,
    status VARCHAR(20) NOT NULL,
    progress INTEGER DEFAULT 0 NOT NULL,
    total INTEGER DEFAULT 1 NOT NULL,
    request_json JSONB,
    result_json JSONB,
    file_path TEXT,
    error_message TEXT,
    created_by VARCHAR(80),
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
    started_at TIMESTAMP,
    finished_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_system_task_status_created
    ON system_task(status, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_system_task_type_created
    ON system_task(task_type, created_at DESC);
