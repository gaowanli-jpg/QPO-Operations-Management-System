WITH permission_rows(parent_code, permission_type, permission_code, permission_name, route_path, component_key, icon, sort_order, visible, assignable) AS (
    VALUES
        (NULL, 'MENU', 'market:sales-match:view', '销售匹配', '/sales-match', 'SalesMatch', 'Files', 120, TRUE, TRUE),
        ('market:sales-match:view', 'API', 'market:sales-match:customer-select', '销售匹配选择客户', NULL, NULL, NULL, 121, FALSE, TRUE),
        ('market:sales-match:view', 'API', 'market:sales-match:order-select', '销售匹配选择订单', NULL, NULL, NULL, 122, FALSE, TRUE),
        ('market:sales-match:view', 'BUTTON', 'market:sales-match:apply', '确认销售匹配补全', NULL, NULL, NULL, 123, FALSE, TRUE),
        ('market:sales-match:view', 'API', 'market:sales-match:file-preview', '销售匹配预览图纸', NULL, NULL, NULL, 124, FALSE, TRUE)
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
JOIN system_permission p ON p.permission_code IN (
    'market:sales-match:view',
    'market:sales-match:customer-select',
    'market:sales-match:order-select',
    'market:sales-match:file-preview'
)
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER', 'READONLY')
ON CONFLICT (role_id, permission_id) DO NOTHING;

INSERT INTO system_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM system_role r
JOIN system_permission p ON p.permission_code = 'market:sales-match:apply'
WHERE r.role_code IN ('SUPER_ADMIN', 'MARKET_USER', 'MARKET_MANAGER')
ON CONFLICT (role_id, permission_id) DO NOTHING;

DELETE FROM system_role_permission srp
USING system_role r, system_permission p
WHERE srp.role_id = r.id
  AND srp.permission_id = p.id
  AND r.role_code = 'READONLY'
  AND p.permission_code = 'market:sales-match:apply';

ALTER TABLE sales_order_item
    ADD COLUMN IF NOT EXISTS assembly_relation_status VARCHAR(30) DEFAULT '不适用';

UPDATE sales_order_item
SET assembly_relation_status = CASE
    WHEN UPPER(COALESCE(quote_mode, '')) = 'ASSEMBLY' OR COALESCE(item_type, '') = '组合件' THEN '未指定父子关系'
    ELSE '不适用'
END
WHERE assembly_relation_status IS NULL OR TRIM(assembly_relation_status) = '';

CREATE TABLE IF NOT EXISTS sales_order_item_assembly_relation (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL,
    parent_item_id BIGINT NOT NULL REFERENCES sales_order_item(id) ON DELETE CASCADE,
    child_item_id BIGINT NOT NULL REFERENCES sales_order_item(id) ON DELETE CASCADE,
    qty_per_parent NUMERIC(14, 4) NOT NULL DEFAULT 1,
    sort_no INTEGER DEFAULT 0,
    source_parent_item_id INTEGER,
    source_child_item_id INTEGER,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_sales_order_assembly_distinct_items CHECK (parent_item_id <> child_item_id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_sales_order_item_assembly_parent_child
    ON sales_order_item_assembly_relation(parent_item_id, child_item_id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_assembly_sales_order
    ON sales_order_item_assembly_relation(sales_order_id, parent_item_id, sort_no, id);

CREATE INDEX IF NOT EXISTS idx_sales_order_item_assembly_child
    ON sales_order_item_assembly_relation(child_item_id);

COMMENT ON COLUMN sales_order_item.assembly_relation_status IS 'Sales order assembly relation status: 不适用, 未指定父子关系, 已指定父子关系.';
COMMENT ON TABLE sales_order_item_assembly_relation IS 'Sales order assembly parent-child relations; references sales_order_item only, never item_quote_version.';
