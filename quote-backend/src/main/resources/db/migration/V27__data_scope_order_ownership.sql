ALTER TABLE order_rfq
    ADD COLUMN IF NOT EXISTS created_by INTEGER,
    ADD COLUMN IF NOT EXISTS owner_user_id INTEGER,
    ADD COLUMN IF NOT EXISTS owner_dept_id BIGINT;

UPDATE order_rfq
SET owner_user_id = COALESCE(owner_user_id, created_by);

UPDATE order_rfq o
SET owner_dept_id = COALESCE(
        owner_dept_id,
        (
            SELECT u.dept_id
            FROM system_user u
            WHERE u.legacy_user_id = o.owner_user_id
              AND COALESCE(u.deleted, FALSE) = FALSE
            ORDER BY u.id ASC
            LIMIT 1
        )
    )
WHERE o.owner_dept_id IS NULL;

CREATE INDEX IF NOT EXISTS idx_order_rfq_owner_user ON order_rfq(owner_user_id);
CREATE INDEX IF NOT EXISTS idx_order_rfq_owner_dept ON order_rfq(owner_dept_id);
CREATE INDEX IF NOT EXISTS idx_order_rfq_customer ON order_rfq(customer_name);
