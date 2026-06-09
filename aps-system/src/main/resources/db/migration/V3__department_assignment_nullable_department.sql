ALTER TABLE aps_department_assignment
    ALTER COLUMN department_id DROP NOT NULL;

ALTER TABLE aps_department_assignment
    DROP CONSTRAINT IF EXISTS aps_department_assignment_department_id_fkey;

ALTER TABLE aps_department_assignment
    ADD CONSTRAINT aps_department_assignment_department_id_fkey
    FOREIGN KEY (department_id) REFERENCES aps_department(id) ON DELETE SET NULL;
