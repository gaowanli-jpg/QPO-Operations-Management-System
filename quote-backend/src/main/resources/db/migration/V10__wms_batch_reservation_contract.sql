-- WMS sale-lading reserve contract: persisted request snapshots, item rows, and ticket mappings.

SET search_path TO "${flyway:defaultSchema}";

CREATE TABLE wms_reservation_batch (
    id BIGSERIAL PRIMARY KEY,
    external_biz_id VARCHAR(80) NOT NULL,
    system_task_id BIGINT REFERENCES system_task(id),
    sale_order_id BIGINT REFERENCES sales_order_header(id),
    sale_order_no VARCHAR(100) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    request_payload JSONB NOT NULL,
    item_count INTEGER NOT NULL,
    ticket_count INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    last_error_type VARCHAR(60),
    response_code VARCHAR(40),
    response_msg VARCHAR(500),
    submitted_at TIMESTAMP,
    responded_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wms_reservation_batch_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'RESPONSE_RECEIVED', 'SUCCESS',
        'FAILED', 'RESULT_UNKNOWN', 'APPLY_FAILED'
    )),
    CONSTRAINT chk_wms_reservation_batch_item_count CHECK (item_count > 0),
    CONSTRAINT chk_wms_reservation_batch_ticket_count CHECK (ticket_count > 0),
    CONSTRAINT chk_wms_reservation_batch_attempt_no CHECK (attempt_no > 0)
);

CREATE UNIQUE INDEX uk_wms_reservation_batch_external_biz_id
    ON wms_reservation_batch(external_biz_id);

CREATE INDEX idx_wms_reservation_batch_status
    ON wms_reservation_batch(status, id DESC);

CREATE INDEX idx_wms_reservation_batch_task
    ON wms_reservation_batch(system_task_id, id DESC);

CREATE TABLE wms_reservation_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES wms_reservation_batch(id),
    product_code VARCHAR(100) NOT NULL,
    reserve_count NUMERIC(18, 6) NOT NULL,
    remark VARCHAR(500) NOT NULL,
    stock_count NUMERIC(18, 6),
    in_production_count NUMERIC(18, 6),
    frozen_count NUMERIC(18, 6),
    surplus_count NUMERIC(18, 6),
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wms_reservation_item_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'RESPONSE_RECEIVED', 'SUCCESS',
        'FAILED', 'RESULT_UNKNOWN', 'APPLY_FAILED'
    )),
    CONSTRAINT chk_wms_reservation_item_reserve_count CHECK (reserve_count > 0),
    CONSTRAINT chk_wms_reservation_item_stock_count CHECK (stock_count IS NULL OR stock_count >= 0),
    CONSTRAINT chk_wms_reservation_item_in_production_count CHECK (in_production_count IS NULL OR in_production_count >= 0),
    CONSTRAINT chk_wms_reservation_item_frozen_count CHECK (frozen_count IS NULL OR frozen_count >= 0),
    CONSTRAINT chk_wms_reservation_item_surplus_count CHECK (surplus_count IS NULL OR surplus_count >= 0),
    CONSTRAINT uk_wms_reservation_item_product UNIQUE (batch_id, product_code)
);

CREATE INDEX idx_wms_reservation_item_batch
    ON wms_reservation_item(batch_id, id);

CREATE TABLE wms_reservation_ticket (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES wms_reservation_batch(id),
    batch_item_id BIGINT NOT NULL REFERENCES wms_reservation_item(id),
    ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    sales_order_item_id BIGINT NOT NULL REFERENCES sales_order_item(id),
    sales_order_line_no INTEGER,
    product_code VARCHAR(100) NOT NULL,
    reserve_count NUMERIC(18, 6) NOT NULL,
    stock_frozen_count NUMERIC(18, 6),
    in_production_count NUMERIC(18, 6),
    surplus_count NUMERIC(18, 6),
    status VARCHAR(30) NOT NULL,
    message VARCHAR(500),
    applied_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_wms_reservation_ticket_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'RESPONSE_RECEIVED', 'SUCCESS',
        'FAILED', 'RESULT_UNKNOWN', 'APPLY_FAILED'
    )),
    CONSTRAINT chk_wms_reservation_ticket_reserve_count CHECK (reserve_count > 0),
    CONSTRAINT chk_wms_reservation_ticket_stock_count CHECK (stock_frozen_count IS NULL OR stock_frozen_count >= 0),
    CONSTRAINT chk_wms_reservation_ticket_in_production_count CHECK (in_production_count IS NULL OR in_production_count >= 0),
    CONSTRAINT chk_wms_reservation_ticket_surplus_count CHECK (surplus_count IS NULL OR surplus_count >= 0),
    CONSTRAINT uk_wms_reservation_ticket_ticket UNIQUE (batch_id, ticket_id)
);

CREATE UNIQUE INDEX uk_wms_reservation_active_ticket
    ON wms_reservation_ticket(ticket_id)
    WHERE status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN', 'RESPONSE_RECEIVED', 'APPLY_FAILED');

CREATE INDEX idx_wms_reservation_ticket_batch
    ON wms_reservation_ticket(batch_id, id);

CREATE INDEX idx_wms_reservation_ticket_item
    ON wms_reservation_ticket(batch_item_id, sales_order_line_no, ticket_id);

ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN wms_reservation_ticket_id BIGINT;

ALTER TABLE order_delivery_ticket_allocation
    ADD CONSTRAINT fk_delivery_allocation_wms_reservation_ticket
        FOREIGN KEY (wms_reservation_ticket_id) REFERENCES wms_reservation_ticket(id);

CREATE UNIQUE INDEX uk_delivery_allocation_wms_reservation_type
    ON order_delivery_ticket_allocation(wms_reservation_ticket_id, allocation_type)
    WHERE wms_reservation_ticket_id IS NOT NULL;

INSERT INTO system_data_center_endpoint_config (
    endpoint_code,
    display_name,
    target_system_code,
    method,
    path,
    enabled,
    timeout_ms,
    request_adapter,
    response_adapter,
    request_schema_json,
    response_schema_json,
    remark,
    created_at,
    updated_at
) VALUES (
    'WMS_INVENTORY_ALLOCATE',
    'WMS sale-lading reserve',
    'WMS',
    'POST',
    '/api/v1/wms/admin-api/wms/datacenter/sale-lading/reserve',
    true,
    60000,
    'WMS_SALE_LADING_RESERVE_V1',
    'WMS_SALE_LADING_RESERVE_RESPONSE_V1',
    '{}'::jsonb,
    '{}'::jsonb,
    'Data Center forwards QPO sale-lading reserve batches to WMS.',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
) ON CONFLICT (endpoint_code) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    target_system_code = EXCLUDED.target_system_code,
    method = EXCLUDED.method,
    path = EXCLUDED.path,
    enabled = true,
    timeout_ms = GREATEST(COALESCE(system_data_center_endpoint_config.timeout_ms, 0), 60000),
    request_adapter = EXCLUDED.request_adapter,
    response_adapter = EXCLUDED.response_adapter,
    remark = EXCLUDED.remark,
    updated_at = CURRENT_TIMESTAMP;
