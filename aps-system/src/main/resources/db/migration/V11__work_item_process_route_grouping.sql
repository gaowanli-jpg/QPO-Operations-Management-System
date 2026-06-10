ALTER TABLE aps_work_item
    ADD COLUMN IF NOT EXISTS process_route_text TEXT;

ALTER TABLE aps_work_item
    ADD COLUMN IF NOT EXISTS process_route_signature VARCHAR(64) NOT NULL DEFAULT '';

WITH routes AS (
    SELECT
        work_item_id,
        STRING_AGG(TRIM(process_name), ' > ' ORDER BY sort_no NULLS LAST, id) AS route_text
    FROM aps_work_item_process
    WHERE process_name IS NOT NULL AND TRIM(process_name) <> ''
    GROUP BY work_item_id
)
UPDATE aps_work_item w
SET process_route_text = routes.route_text,
    process_route_signature = MD5(routes.route_text),
    updated_at = CURRENT_TIMESTAMP
FROM routes
WHERE w.id = routes.work_item_id
  AND COALESCE(w.process_route_signature, '') = '';

CREATE INDEX IF NOT EXISTS idx_aps_work_item_assignment_customer_id
    ON aps_work_item(assignment_status, customer_name, id DESC);

CREATE INDEX IF NOT EXISTS idx_aps_work_item_assignment_material_id
    ON aps_work_item(assignment_status, material_name, material_type, id DESC);

CREATE INDEX IF NOT EXISTS idx_aps_work_item_assignment_process_route_id
    ON aps_work_item(assignment_status, process_route_signature, id DESC);
