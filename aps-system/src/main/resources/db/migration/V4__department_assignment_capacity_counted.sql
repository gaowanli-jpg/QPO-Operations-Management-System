ALTER TABLE aps_department_assignment
    ADD COLUMN IF NOT EXISTS capacity_counted BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_aps_department_assignment_capacity
    ON aps_department_assignment(department_id, status, capacity_counted);
