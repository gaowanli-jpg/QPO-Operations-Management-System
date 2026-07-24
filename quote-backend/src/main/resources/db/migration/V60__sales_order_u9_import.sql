WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        ('market:rfq:view', 'BUTTON', 'market:rfq:import-sales-order', '导入销售订单', NULL, NULL, NULL, 117, FALSE, TRUE)
)
INSERT INTO system_permission (
    parent_id, permission_type, permission_code, permission_name,
    route_path, component_key, icon, sort_order, visible, enabled, assignable
)
SELECT
    COALESCE(parent.id, 0),
    permission_rows.permission_type,
    permission_rows.permission_code,
    permission_rows.permission_name,
    permission_rows.route_path,
    permission_rows.component_key,
    permission_rows.icon,
    permission_rows.sort_order,
    permission_rows.visible,
    TRUE,
    permission_rows.assignable
FROM permission_rows
LEFT JOIN system_permission parent ON parent.permission_code = permission_rows.parent_code
ON CONFLICT (permission_code) DO UPDATE
SET parent_id = EXCLUDED.parent_id,
    permission_type = EXCLUDED.permission_type,
    permission_name = EXCLUDED.permission_name,
    route_path = EXCLUDED.route_path,
    component_key = EXCLUDED.component_key,
    icon = EXCLUDED.icon,
    sort_order = EXCLUDED.sort_order,
    visible = EXCLUDED.visible,
    enabled = TRUE,
    assignable = EXCLUDED.assignable,
    updated_at = CURRENT_TIMESTAMP;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = 'market:rfq:import-sales-order'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

CREATE TABLE IF NOT EXISTS sales_order_import_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_no VARCHAR(80) NOT NULL UNIQUE,
    source_file_name VARCHAR(255),
    status VARCHAR(30) NOT NULL DEFAULT 'PARSING',
    total_order_count INTEGER NOT NULL DEFAULT 0,
    success_order_count INTEGER NOT NULL DEFAULT 0,
    failed_order_count INTEGER NOT NULL DEFAULT 0,
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS sales_order_import_record (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES sales_order_import_batch(id) ON DELETE CASCADE,
    other_id VARCHAR(120),
    customer_order_no VARCHAR(120),
    order_by VARCHAR(120),
    document_type VARCHAR(120),
    line_count INTEGER NOT NULL DEFAULT 0,
    request_summary_json JSONB,
    u9_res_code INTEGER,
    u9_success BOOLEAN,
    u9_id VARCHAR(120),
    u9_code VARCHAR(120),
    u9_error_msg TEXT,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_sales_order_import_record_batch_id
    ON sales_order_import_record(batch_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_import_record_customer_order_no
    ON sales_order_import_record(customer_order_no);

CREATE INDEX IF NOT EXISTS idx_sales_order_import_record_u9_code
    ON sales_order_import_record(u9_code);
