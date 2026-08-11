ALTER TABLE customer_master
    ADD COLUMN u9_org_code varchar(50),
    ADD COLUMN u9_customer_id varchar(100),
    ADD COLUMN last_synced_at timestamp without time zone,
    ADD COLUMN source_hash varchar(64);

UPDATE customer_master
SET u9_org_code = '101'
WHERE NULLIF(btrim(u9_org_code), '') IS NULL;

ALTER TABLE customer_master
    ALTER COLUMN u9_org_code SET DEFAULT '101',
    ALTER COLUMN u9_org_code SET NOT NULL;

ALTER TABLE customer_master
    DROP CONSTRAINT IF EXISTS uk_customer_master_name,
    DROP CONSTRAINT IF EXISTS uk_customer_master_short_name;

CREATE UNIQUE INDEX uk_customer_master_u9_customer_id
    ON customer_master(u9_org_code, u9_customer_id)
    WHERE u9_customer_id IS NOT NULL AND btrim(u9_customer_id) <> '';

CREATE INDEX idx_customer_master_u9_org_enabled
    ON customer_master(u9_org_code, enabled);

CREATE INDEX idx_customer_master_customer_name
    ON customer_master(customer_name);

CREATE INDEX idx_customer_master_last_synced_at
    ON customer_master(last_synced_at);

CREATE TABLE u9_sync_seller_option_cache (
    id bigserial PRIMARY KEY,
    u9_org_code varchar(50) DEFAULT '101' NOT NULL,
    u9_seller_id varchar(100),
    seller_code varchar(100) NOT NULL,
    seller_name varchar(200),
    department_code varchar(100),
    department_name varchar(200),
    enabled boolean DEFAULT true NOT NULL,
    operator_type varchar(100),
    last_synced_at timestamp without time zone,
    source_hash varchar(64),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_u9_sync_seller_option_cache_code UNIQUE (u9_org_code, seller_code)
);

CREATE INDEX idx_u9_sync_seller_option_cache_enabled
    ON u9_sync_seller_option_cache(u9_org_code, enabled);

CREATE INDEX idx_u9_sync_seller_option_cache_department
    ON u9_sync_seller_option_cache(department_code);

CREATE TABLE u9_sync_department_option_cache (
    id bigserial PRIMARY KEY,
    u9_org_code varchar(50) DEFAULT '101' NOT NULL,
    u9_department_id varchar(100),
    department_code varchar(100) NOT NULL,
    department_name varchar(200),
    parent_department_code varchar(100),
    tree_display_name varchar(500),
    enabled boolean DEFAULT true NOT NULL,
    last_synced_at timestamp without time zone,
    source_hash varchar(64),
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT uk_u9_sync_department_option_cache_code UNIQUE (u9_org_code, department_code)
);

CREATE INDEX idx_u9_sync_department_option_cache_enabled
    ON u9_sync_department_option_cache(u9_org_code, enabled);

CREATE INDEX idx_u9_sync_department_option_cache_parent
    ON u9_sync_department_option_cache(parent_department_code);

CREATE TABLE u9_master_data_sync_run (
    id bigserial PRIMARY KEY,
    sync_type varchar(50) NOT NULL,
    u9_org_code varchar(50) DEFAULT '101' NOT NULL,
    status varchar(30) NOT NULL,
    started_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    finished_at timestamp without time zone,
    fetched_count integer DEFAULT 0 NOT NULL,
    upserted_count integer DEFAULT 0 NOT NULL,
    disabled_count integer DEFAULT 0 NOT NULL,
    message text,
    created_by bigint,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_u9_master_data_sync_run_type CHECK (sync_type IN ('CUSTOMER', 'U9_SYNC_OPTIONS', 'ALL')),
    CONSTRAINT ck_u9_master_data_sync_run_status CHECK (status IN ('RUNNING', 'SUCCESS', 'FAILED', 'PARTIAL_SUCCESS'))
);

CREATE INDEX idx_u9_master_data_sync_run_type_started
    ON u9_master_data_sync_run(sync_type, started_at DESC);

CREATE INDEX idx_u9_master_data_sync_run_status
    ON u9_master_data_sync_run(status);

ALTER TABLE system_role_data_scope_customer
    ADD COLUMN customer_code varchar(200),
    ADD COLUMN customer_name_snapshot varchar(200);

UPDATE system_role_data_scope_customer scope
SET customer_code = COALESCE(
        NULLIF(btrim(scope.customer_code), ''),
        (
            SELECT MIN(customer.customer_code)
            FROM customer_master customer
            WHERE customer.customer_name = scope.customer_name
        ),
        scope.customer_name
    ),
    customer_name_snapshot = COALESCE(NULLIF(btrim(scope.customer_name_snapshot), ''), scope.customer_name);

DELETE FROM system_role_data_scope_customer scope
USING (
    SELECT ctid,
           ROW_NUMBER() OVER (
               PARTITION BY role_id, customer_code
               ORDER BY created_at NULLS LAST, customer_name
           ) AS row_no
    FROM system_role_data_scope_customer
) duplicate_scope
WHERE scope.ctid = duplicate_scope.ctid
  AND duplicate_scope.row_no > 1;

ALTER TABLE system_role_data_scope_customer
    ALTER COLUMN customer_code SET NOT NULL;

ALTER TABLE system_role_data_scope_customer
    DROP CONSTRAINT IF EXISTS uk_system_role_data_scope_customer;

ALTER TABLE system_role_data_scope_customer
    ADD CONSTRAINT uk_system_role_data_scope_customer_code UNIQUE (role_id, customer_code);

CREATE INDEX idx_system_role_data_scope_customer_code
    ON system_role_data_scope_customer(customer_code);

CREATE INDEX idx_order_rfq_customer_code
    ON order_rfq(customer_code);

CREATE INDEX idx_order_rfq_customer_short_name
    ON order_rfq(customer_short_name);
