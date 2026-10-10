# Sales Order Linked Discard WMS Alignment Plan

## Goal

Make linked discard and restore follow the current one-to-one `sales_order_item.allocation_id` relationship, prevent changes while a WMS reservation is unresolved, and snapshot WMS state from the allocation-based reservation records.

## Scope

- Prefer exact allocation IDs for current sales-order rows; use ticket/source relationships only when the row has no allocation link.
- Do not let a split row mutate a shared intermediate delivery ticket.
- Check WMS reservation states `RESERVED`, `SUBMITTING`, `RESULT_UNKNOWN`, `RESPONSE_RECEIVED`, and `APPLY_FAILED` before linked discard or restore.
- Snapshot the latest allocation-based WMS result and `external_biz_id`; retain delivery-ticket fields as a legacy fallback.
- Keep MES and stock-picking completion protection, notice history, permissions, and audit behavior unchanged.
- Update the linked-discard/WMS process documentation and add targeted backend regression tests. No schema migration is expected.

## Verification

- Test exact allocation targeting for split sales-order rows and prevent sibling-row changes.
- Test unresolved WMS reservations block item/order discard and restore.
- Test current WMS reservation status/request ID snapshots, with a legacy ticket fallback.
- Run the focused backend tests and `git diff --check`.
