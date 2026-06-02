CREATE TABLE IF NOT EXISTS aps_erp_sync_run (
    id BIGSERIAL PRIMARY KEY,
    source_system VARCHAR(50) NOT NULL DEFAULT 'ERP',
    sync_mode VARCHAR(50) NOT NULL DEFAULT 'PULL',
    request_after_ticket_id INTEGER,
    request_limit INTEGER,
    received_count INTEGER NOT NULL DEFAULT 0,
    imported_count INTEGER NOT NULL DEFAULT 0,
    skipped_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING',
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_aps_erp_sync_run_status_started ON aps_erp_sync_run(status, started_at DESC);

CREATE TABLE IF NOT EXISTS aps_work_item (
    id BIGSERIAL PRIMARY KEY,
    erp_sync_run_id BIGINT REFERENCES aps_erp_sync_run(id) ON DELETE SET NULL,
    erp_ticket_id INTEGER NOT NULL,
    erp_rfq_id INTEGER,
    erp_item_id INTEGER,
    erp_process_sheet_id INTEGER,
    process_snapshot_source VARCHAR(50) NOT NULL DEFAULT 'ITEM_QUOTE_VERSION',
    rfq_no VARCHAR(100),
    customer_name VARCHAR(200) NOT NULL DEFAULT '',
    order_status VARCHAR(50),
    internal_project_no VARCHAR(100),
    project_no VARCHAR(100),
    order_type VARCHAR(50),
    item_type VARCHAR(50),
    part_no VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT '',
    description TEXT,
    qty INTEGER NOT NULL DEFAULT 0,
    source_item_qty INTEGER,
    unit VARCHAR(50),
    material_name VARCHAR(200),
    material_type VARCHAR(50),
    material_gross_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    material_demand_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    part_net_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    surface_treatment VARCHAR(200),
    lead_time VARCHAR(100),
    quote_lead_time VARCHAR(100),
    internal_target_lead_time VARCHAR(100),
    start_date DATE,
    delivery_date DATE,
    remark TEXT,
    arrange_type VARCHAR(100),
    drawing_url VARCHAR(500),
    drawing_view_url VARCHAR(800),
    material_data JSONB,
    source_payload JSONB,
    total_work_hours NUMERIC(14, 4) NOT NULL DEFAULT 0,
    part_stock_status VARCHAR(50) NOT NULL DEFAULT 'UNLOCKED',
    material_stock_status VARCHAR(50) NOT NULL DEFAULT 'UNLOCKED',
    assignment_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PMC_ASSIGNMENT',
    assigned_department_id BIGINT,
    assigned_department_name VARCHAR(100),
    assigned_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_work_item_erp_ticket UNIQUE (erp_ticket_id),
    CONSTRAINT ck_aps_work_item_qty_nonnegative CHECK (qty >= 0),
    CONSTRAINT ck_aps_work_item_weights_nonnegative CHECK (
        material_gross_weight_kg >= 0
        AND material_demand_weight_kg >= 0
        AND part_net_weight_kg >= 0
    ),
    CONSTRAINT ck_aps_work_item_hours_nonnegative CHECK (total_work_hours >= 0)
);

CREATE INDEX IF NOT EXISTS idx_aps_work_item_status_id ON aps_work_item(status, id);
CREATE INDEX IF NOT EXISTS idx_aps_work_item_assignment_status ON aps_work_item(assignment_status, id);
CREATE INDEX IF NOT EXISTS idx_aps_work_item_customer_part_version ON aps_work_item(customer_name, part_no, version);
CREATE INDEX IF NOT EXISTS idx_aps_work_item_internal_project_no ON aps_work_item(internal_project_no);
CREATE INDEX IF NOT EXISTS idx_aps_work_item_rfq_no ON aps_work_item(rfq_no);
CREATE INDEX IF NOT EXISTS idx_aps_work_item_delivery_date ON aps_work_item(delivery_date);
CREATE INDEX IF NOT EXISTS idx_aps_work_item_sync_run ON aps_work_item(erp_sync_run_id);

CREATE TABLE IF NOT EXISTS aps_work_item_process (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT NOT NULL REFERENCES aps_work_item(id) ON DELETE CASCADE,
    sort_no INTEGER,
    process_name VARCHAR(100) NOT NULL,
    calc_type VARCHAR(50),
    source_type VARCHAR(50),
    input_val NUMERIC(14, 4) NOT NULL DEFAULT 0,
    work_hours NUMERIC(14, 4) NOT NULL DEFAULT 0,
    unit_rate NUMERIC(14, 4),
    line_cost NUMERIC(14, 4) NOT NULL DEFAULT 0,
    raw_row JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_work_item_process_values_nonnegative CHECK (
        input_val >= 0
        AND work_hours >= 0
        AND line_cost >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_aps_work_item_process_item_sort ON aps_work_item_process(work_item_id, sort_no, id);
CREATE INDEX IF NOT EXISTS idx_aps_work_item_process_name ON aps_work_item_process(process_name);

CREATE TABLE IF NOT EXISTS aps_part_inventory (
    id BIGSERIAL PRIMARY KEY,
    customer_name VARCHAR(200) NOT NULL,
    part_no VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT '',
    in_stock_qty INTEGER NOT NULL DEFAULT 0,
    in_production_qty INTEGER NOT NULL DEFAULT 0,
    locked_in_stock_qty INTEGER NOT NULL DEFAULT 0,
    locked_in_production_qty INTEGER NOT NULL DEFAULT 0,
    available_in_stock_qty INTEGER GENERATED ALWAYS AS (in_stock_qty - locked_in_stock_qty) STORED,
    available_in_production_qty INTEGER GENERATED ALWAYS AS (in_production_qty - locked_in_production_qty) STORED,
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_part_inventory_customer_part_version UNIQUE (customer_name, part_no, version),
    CONSTRAINT ck_aps_part_inventory_qty_nonnegative CHECK (
        in_stock_qty >= 0
        AND in_production_qty >= 0
        AND locked_in_stock_qty >= 0
        AND locked_in_production_qty >= 0
        AND locked_in_stock_qty <= in_stock_qty
        AND locked_in_production_qty <= in_production_qty
    )
);

CREATE INDEX IF NOT EXISTS idx_aps_part_inventory_customer ON aps_part_inventory(customer_name);
CREATE INDEX IF NOT EXISTS idx_aps_part_inventory_part_version ON aps_part_inventory(part_no, version);

CREATE TABLE IF NOT EXISTS aps_part_inventory_lock (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT NOT NULL REFERENCES aps_work_item(id) ON DELETE CASCADE,
    part_inventory_id BIGINT REFERENCES aps_part_inventory(id) ON DELETE SET NULL,
    customer_name VARCHAR(200) NOT NULL,
    part_no VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT '',
    demand_qty INTEGER NOT NULL DEFAULT 0,
    locked_in_stock_qty INTEGER NOT NULL DEFAULT 0,
    locked_in_production_qty INTEGER NOT NULL DEFAULT 0,
    production_required_qty INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'LOCKED',
    locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_part_inventory_lock_item UNIQUE (work_item_id),
    CONSTRAINT ck_aps_part_inventory_lock_qty_nonnegative CHECK (
        demand_qty >= 0
        AND locked_in_stock_qty >= 0
        AND locked_in_production_qty >= 0
        AND production_required_qty >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_aps_part_inventory_lock_part ON aps_part_inventory_lock(customer_name, part_no, version);
CREATE INDEX IF NOT EXISTS idx_aps_part_inventory_lock_status ON aps_part_inventory_lock(status, id);

CREATE TABLE IF NOT EXISTS aps_production_requirement (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT NOT NULL REFERENCES aps_work_item(id) ON DELETE CASCADE,
    customer_name VARCHAR(200) NOT NULL,
    part_no VARCHAR(100) NOT NULL,
    version VARCHAR(50) NOT NULL DEFAULT '',
    required_qty INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PRODUCTION',
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_production_requirement_qty_nonnegative CHECK (required_qty >= 0)
);

CREATE INDEX IF NOT EXISTS idx_aps_production_requirement_status ON aps_production_requirement(status, id);
CREATE INDEX IF NOT EXISTS idx_aps_production_requirement_item ON aps_production_requirement(work_item_id);

CREATE TABLE IF NOT EXISTS aps_material_inventory (
    id BIGSERIAL PRIMARY KEY,
    material_name VARCHAR(200) NOT NULL,
    material_type VARCHAR(50) NOT NULL DEFAULT '',
    stock_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    locked_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    available_weight_kg NUMERIC(16, 4) GENERATED ALWAYS AS (stock_weight_kg - locked_weight_kg) STORED,
    safety_stock_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    unit VARCHAR(20) NOT NULL DEFAULT 'kg',
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_material_inventory_name_type UNIQUE (material_name, material_type),
    CONSTRAINT ck_aps_material_inventory_weight_nonnegative CHECK (
        stock_weight_kg >= 0
        AND locked_weight_kg >= 0
        AND locked_weight_kg <= stock_weight_kg
        AND safety_stock_weight_kg >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_aps_material_inventory_name_type ON aps_material_inventory(material_name, material_type);

CREATE TABLE IF NOT EXISTS aps_material_lock (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT NOT NULL REFERENCES aps_work_item(id) ON DELETE CASCADE,
    material_inventory_id BIGINT REFERENCES aps_material_inventory(id) ON DELETE SET NULL,
    rfq_no VARCHAR(100),
    internal_project_no VARCHAR(100),
    customer_name VARCHAR(200),
    part_no VARCHAR(100),
    version VARCHAR(50) NOT NULL DEFAULT '',
    material_name VARCHAR(200) NOT NULL,
    material_type VARCHAR(50) NOT NULL DEFAULT '',
    demand_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    locked_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    shortage_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'LOCKED',
    locked_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_material_lock_item UNIQUE (work_item_id),
    CONSTRAINT ck_aps_material_lock_weight_nonnegative CHECK (
        demand_weight_kg >= 0
        AND locked_weight_kg >= 0
        AND shortage_weight_kg >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_aps_material_lock_material ON aps_material_lock(material_name, material_type);
CREATE INDEX IF NOT EXISTS idx_aps_material_lock_status ON aps_material_lock(status, id);

CREATE TABLE IF NOT EXISTS aps_material_shortage (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT REFERENCES aps_work_item(id) ON DELETE SET NULL,
    rfq_no VARCHAR(100),
    internal_project_no VARCHAR(100),
    customer_name VARCHAR(200),
    part_no VARCHAR(100),
    version VARCHAR(50) NOT NULL DEFAULT '',
    material_name VARCHAR(200) NOT NULL,
    material_type VARCHAR(50) NOT NULL DEFAULT '',
    shortage_weight_kg NUMERIC(16, 4) NOT NULL DEFAULT 0,
    source_task VARCHAR(100),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING_PURCHASE',
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_material_shortage_weight_nonnegative CHECK (shortage_weight_kg >= 0)
);

CREATE INDEX IF NOT EXISTS idx_aps_material_shortage_status ON aps_material_shortage(status, id);
CREATE INDEX IF NOT EXISTS idx_aps_material_shortage_material ON aps_material_shortage(material_name, material_type);
CREATE INDEX IF NOT EXISTS idx_aps_material_shortage_project ON aps_material_shortage(internal_project_no, rfq_no);

CREATE TABLE IF NOT EXISTS aps_department (
    id BIGSERIAL PRIMARY KEY,
    department_name VARCHAR(100) NOT NULL,
    department_type VARCHAR(50) NOT NULL,
    department_status VARCHAR(50) NOT NULL DEFAULT 'ENABLED',
    monthly_available_hours NUMERIC(14, 2),
    occupied_hours NUMERIC(14, 2) NOT NULL DEFAULT 0,
    remaining_hours NUMERIC(14, 2) GENERATED ALWAYS AS (COALESCE(monthly_available_hours, 0) - occupied_hours) STORED,
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_department_name UNIQUE (department_name),
    CONSTRAINT ck_aps_department_type CHECK (department_type IN ('PRODUCTION', 'OUTSOURCE')),
    CONSTRAINT ck_aps_department_status CHECK (department_status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT ck_aps_department_hours_nonnegative CHECK (
        (monthly_available_hours IS NULL OR monthly_available_hours >= 0)
        AND occupied_hours >= 0
    )
);

CREATE INDEX IF NOT EXISTS idx_aps_department_type_status ON aps_department(department_type, department_status);

CREATE TABLE IF NOT EXISTS aps_machine (
    id BIGSERIAL PRIMARY KEY,
    department_id BIGINT NOT NULL REFERENCES aps_department(id) ON DELETE CASCADE,
    machine_code VARCHAR(100),
    machine_name VARCHAR(100) NOT NULL,
    machine_status VARCHAR(50) NOT NULL DEFAULT 'IDLE',
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_machine_department_code UNIQUE (department_id, machine_code)
);

CREATE INDEX IF NOT EXISTS idx_aps_machine_department ON aps_machine(department_id);
CREATE INDEX IF NOT EXISTS idx_aps_machine_status ON aps_machine(machine_status, id);

CREATE TABLE IF NOT EXISTS aps_machine_process_capability (
    id BIGSERIAL PRIMARY KEY,
    machine_id BIGINT NOT NULL REFERENCES aps_machine(id) ON DELETE CASCADE,
    process_name VARCHAR(100) NOT NULL,
    calc_type VARCHAR(50),
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_aps_machine_process UNIQUE (machine_id, process_name, calc_type)
);

CREATE INDEX IF NOT EXISTS idx_aps_machine_process_name ON aps_machine_process_capability(process_name);

CREATE TABLE IF NOT EXISTS aps_department_assignment (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT NOT NULL REFERENCES aps_work_item(id) ON DELETE CASCADE,
    department_id BIGINT NOT NULL REFERENCES aps_department(id),
    department_name VARCHAR(100) NOT NULL,
    required_hours NUMERIC(14, 4) NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL DEFAULT 'ASSIGNED',
    assigned_by VARCHAR(100),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    remark TEXT,
    CONSTRAINT ck_aps_department_assignment_hours_nonnegative CHECK (required_hours >= 0)
);

CREATE INDEX IF NOT EXISTS idx_aps_department_assignment_item ON aps_department_assignment(work_item_id);
CREATE INDEX IF NOT EXISTS idx_aps_department_assignment_department ON aps_department_assignment(department_id, assigned_at);
CREATE INDEX IF NOT EXISTS idx_aps_department_assignment_status ON aps_department_assignment(status, id);
