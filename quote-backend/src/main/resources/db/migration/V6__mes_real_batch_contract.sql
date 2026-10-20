-- MES real batch contract: idempotent batch state, per-row line ids, and safe reconcile summaries.

SET search_path TO "${flyway:defaultSchema}";

CREATE TABLE mes_integration_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_type VARCHAR(40) NOT NULL,
    biz_id VARCHAR(80) NOT NULL,
    system_task_id BIGINT,
    group_key_hash VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    item_count INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    submitted_at TIMESTAMP,
    responded_at TIMESTAMP,
    completed_at TIMESTAMP,
    response_code VARCHAR(40),
    response_message VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_batch_type CHECK (batch_type IN ('MATERIAL_PREPARATION', 'PRODUCTION_DEMAND')),
    CONSTRAINT chk_mes_batch_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'PARTIAL_PENDING', 'SUCCESS',
        'FAILED', 'RESULT_UNKNOWN', 'CANCELLED'
    )),
    CONSTRAINT chk_mes_batch_item_count CHECK (item_count > 0),
    CONSTRAINT chk_mes_batch_attempt_no CHECK (attempt_no > 0)
);

CREATE UNIQUE INDEX uk_mes_integration_batch_biz_id
    ON mes_integration_batch(biz_id);

CREATE INDEX idx_mes_integration_batch_status
    ON mes_integration_batch(batch_type, status, id DESC);

CREATE TABLE mes_material_batch_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES mes_integration_batch(id),
    ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    line_request_id VARCHAR(80) NOT NULL,
    mes_request_row_id BIGINT REFERENCES order_delivery_ticket_mes_request(id),
    drawing_no VARCHAR(128) NOT NULL,
    drawing_version VARCHAR(64) NOT NULL DEFAULT '',
    item_key VARCHAR(220) NOT NULL,
    status VARCHAR(30) NOT NULL,
    mes_task_id VARCHAR(120),
    mes_task_no VARCHAR(120),
    product_id VARCHAR(120),
    product_code VARCHAR(100),
    product_name VARCHAR(255),
    response_status VARCHAR(60),
    message VARCHAR(500),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_material_batch_item_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'CALLBACK_PENDING', 'SUCCESS',
        'FAILED', 'RESULT_UNKNOWN', 'SKIPPED'
    )),
    CONSTRAINT uk_mes_material_batch_ticket UNIQUE (batch_id, ticket_id),
    CONSTRAINT uk_mes_material_batch_key UNIQUE (batch_id, item_key),
    CONSTRAINT uk_mes_material_line_request_id UNIQUE (line_request_id)
);

CREATE UNIQUE INDEX uk_mes_material_active_ticket
    ON mes_material_batch_item(ticket_id)
    WHERE status IN ('RESERVED', 'SUBMITTING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN');

CREATE INDEX idx_mes_material_batch_item_batch
    ON mes_material_batch_item(batch_id, id);

CREATE TABLE mes_production_batch_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES mes_integration_batch(id),
    allocation_id INTEGER NOT NULL REFERENCES order_delivery_ticket_allocation(id),
    line_request_id VARCHAR(80) NOT NULL,
    sale_order_no VARCHAR(100) NOT NULL,
    sale_order_item_no VARCHAR(100) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    item_key VARCHAR(320) NOT NULL,
    demand_qty NUMERIC(18, 6) NOT NULL,
    status VARCHAR(30) NOT NULL,
    mes_record_id VARCHAR(120),
    mes_plan_no VARCHAR(120),
    response_status VARCHAR(60),
    message VARCHAR(500),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_production_batch_item_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'SUCCESS', 'FAILED', 'RESULT_UNKNOWN', 'SKIPPED'
    )),
    CONSTRAINT chk_mes_production_batch_qty CHECK (demand_qty > 0),
    CONSTRAINT uk_mes_production_batch_allocation UNIQUE (batch_id, allocation_id),
    CONSTRAINT uk_mes_production_batch_key UNIQUE (batch_id, item_key),
    CONSTRAINT uk_mes_production_line_request_id UNIQUE (line_request_id)
);

CREATE UNIQUE INDEX uk_mes_production_active_allocation
    ON mes_production_batch_item(allocation_id)
    WHERE status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN');

CREATE INDEX idx_mes_production_batch_item_batch
    ON mes_production_batch_item(batch_id, id);

CREATE TABLE mes_integration_reconcile_run (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES mes_integration_batch(id),
    reconcile_type VARCHAR(40) NOT NULL,
    status VARCHAR(30) NOT NULL,
    matched_count INTEGER NOT NULL DEFAULT 0,
    pending_count INTEGER NOT NULL DEFAULT 0,
    failed_count INTEGER NOT NULL DEFAULT 0,
    message VARCHAR(500),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_reconcile_type CHECK (reconcile_type IN ('MATERIAL_PREPARATION', 'PRODUCTION_DEMAND')),
    CONSTRAINT chk_mes_reconcile_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'RESULT_UNKNOWN')),
    CONSTRAINT chk_mes_reconcile_counts CHECK (matched_count >= 0 AND pending_count >= 0 AND failed_count >= 0)
);

CREATE INDEX idx_mes_reconcile_run_batch
    ON mes_integration_reconcile_run(batch_id, id DESC);

ALTER TABLE order_delivery_ticket_mes_request
    ADD COLUMN batch_id BIGINT,
    ADD COLUMN batch_item_id BIGINT;

ALTER TABLE order_delivery_ticket_mes_request
    ADD CONSTRAINT fk_delivery_mes_request_batch
        FOREIGN KEY (batch_id) REFERENCES mes_integration_batch(id),
    ADD CONSTRAINT fk_delivery_mes_request_batch_item
        FOREIGN KEY (batch_item_id) REFERENCES mes_material_batch_item(id);

CREATE INDEX idx_delivery_mes_request_batch
    ON order_delivery_ticket_mes_request(batch_id, batch_item_id);
