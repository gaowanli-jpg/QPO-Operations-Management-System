-- Retire the previous sales-order-change module before its redesign.
-- Historical change records and permissions remain for audit compatibility.

UPDATE system_permission
SET enabled = FALSE,
    visible = FALSE,
    assignable = FALSE,
    updated_at = CURRENT_TIMESTAMP
WHERE id IN (202, 203, 204, 205)
   OR permission_code IN (
       'change:sales-order:view',
       'change:sales-order:customer-select',
       'change:sales-order:order-select',
       'change:sales-order:update'
   );
