CREATE TABLE IF NOT EXISTS aps_department_task (
    id BIGSERIAL PRIMARY KEY,
    work_item_id BIGINT NOT NULL REFERENCES aps_work_item(id) ON DELETE CASCADE,
    department_assignment_id BIGINT REFERENCES aps_department_assignment(id) ON DELETE SET NULL,
    department_id BIGINT REFERENCES aps_department(id) ON DELETE SET NULL,
    department_name VARCHAR(100) NOT NULL DEFAULT '',
    required_hours NUMERIC(14, 4) NOT NULL DEFAULT 0,
    task_status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    assigned_by VARCHAR(100),
    assigned_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    released_at TIMESTAMP,
    remark TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_aps_department_task_hours_nonnegative CHECK (required_hours >= 0),
    CONSTRAINT ck_aps_department_task_status CHECK (task_status IN ('ACTIVE', 'RELEASED'))
);

INSERT INTO aps_department_task (
    work_item_id, department_assignment_id, department_id, department_name,
    required_hours, task_status, assigned_by, assigned_at, released_at, remark,
    created_at, updated_at
)
SELECT a.work_item_id,
       a.id,
       a.department_id,
       COALESCE(a.department_name, ''),
       a.required_hours,
       CASE WHEN a.status = 'ASSIGNED' THEN 'ACTIVE' ELSE 'RELEASED' END,
       a.assigned_by,
       a.assigned_at,
       a.released_at,
       a.remark,
       a.assigned_at,
       COALESCE(a.released_at, a.assigned_at)
FROM aps_department_assignment a
WHERE NOT EXISTS (
    SELECT 1
    FROM aps_department_task t
    WHERE t.department_assignment_id = a.id
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_aps_department_task_active_item
    ON aps_department_task(work_item_id)
    WHERE task_status = 'ACTIVE';

CREATE INDEX IF NOT EXISTS idx_aps_department_task_department_status
    ON aps_department_task(department_id, task_status, assigned_at DESC, id DESC);

CREATE INDEX IF NOT EXISTS idx_aps_department_task_status
    ON aps_department_task(task_status, id);
