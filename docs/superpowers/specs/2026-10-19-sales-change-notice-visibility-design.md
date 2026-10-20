# Sales Change Notice Visibility Design

## Status

Approved by the user on 2026-10-19. This document defines design only; implementation starts after the user reviews this document and approves the resulting implementation plan.

## Context

The sales change page currently lists every notice unless a status filter is selected. `SENT` notices therefore remain in the default list as read-only history. The user wants batch discard and restore behavior like the production and locked-item work pools, where discard means hiding an operation from the active pool rather than changing the underlying order or downstream work order.

The existing notice lifecycle remains `PENDING`, `SENDING`, `SENT`, `FAILED`, and `RESULT_UNKNOWN`. Pending `MODIFY` notices merge by sales-order item and downstream target. A merged notice keeps its `noticeId`, receives the latest snapshot and accumulated changed fields, and is currently stamped with the active auto-dispatch epoch when auto mode is enabled.

## Goals

- Let authorized operators batch-hide notices that should no longer be actionable and restore them later.
- Add switchable active, hidden, and sent views.
- Keep notice status, payload, idempotency key, sales-order data, Allocation data, and MES state independent from visibility operations.
- Exclude hidden notices from both manual and automatic claiming.
- Preserve auto-mode semantics: restoring an old notice only makes it visible and leaves it for manual sending; a new modification merged into a hidden pending notice makes it visible again and follows the new change's current auto-mode rules.

## Non-goals

- Do not discard or restore a sales-order item, Allocation, MES production order, or stock-picking order.
- Do not delete notices or change their `noticeId` during hide, restore, or retry.
- Do not change notification payload contracts or downstream behavior.
- Do not change the existing coalescing scope for pending modification notices.

## Approaches Considered

1. **Hard-delete hidden notices.** Rejected because this loses audit history and can break `noticeId` idempotency and pending modification coalescing.
2. **Filter rows only in the browser.** Rejected because hidden rows could still be manually or automatically claimed, and the hidden state would not persist across sessions.
3. **Persist a soft-hidden state on each notice.** Recommended. It preserves notification identity and history while allowing server-side list and claim queries to enforce visibility consistently.

## Proposed Behavior

### Views

- **Active notices** is the default view. It includes visible `PENDING`, `SENDING`, `FAILED`, and `RESULT_UNKNOWN` notices. `SENT` notices leave this operational view.
- **Hidden notices** includes notices with the persistent hidden flag set. Only eligible non-sending notices can be hidden.
- **Sent notices** includes visible `SENT` notices as read-only history. Existing customer/order/type/date filters remain available.
- The status filter further narrows the selected view; view selection remains the primary visibility boundary.

### Batch hide and restore

- Batch hide accepts visible notices in `PENDING`, `FAILED`, or `RESULT_UNKNOWN` state. The operator must enter a non-empty reason, trimmed and limited to 500 characters.
- `SENDING` notices cannot be hidden because a send task has already claimed them. `SENT` notices are read-only and belong in the sent view.
- Batch restore accepts hidden eligible notices. Restore clears the hidden state and returns them to the active view without changing status, payload, attempt count, `noticeId`, or downstream identifiers.
- Each hide and restore action is audited. Hide audit records include the required reason. Restore reason is not required; if provided, it is recorded in the audit summary.
- Neither action changes sales-order item, Allocation, ticket, WMS, MES, or stock-picking state.

### Automatic and manual sending

- Both manual claim and automatic claim require `is_hidden = false` in addition to their existing state, data-scope, permission, and epoch checks.
- Restoring a `PENDING` notice sets its auto-dispatch epoch to the manual-backlog epoch (`0`). In auto mode it becomes visible and manually sendable, but the scheduler will not automatically claim it. This preserves the rule that auto mode processes newly generated notices, not restored backlog.
- If a new sales-order item change merges into a hidden `PENDING MODIFY` notice, the merge clears the hidden state, updates the payload and changed-field set, retains the original `noticeId`, and assigns the current auto-dispatch epoch when auto mode is enabled. The newly changed notice becomes visible and is eligible for auto dispatch under the existing current-epoch rule.
- Hidden `DISCARD` and `RESTORE` notices remain independent event records; they are not merged with modification notices.
- A send task and a hide request must serialize through row locks and conditional state updates. If a row has already entered `SENDING`, the hide operation rejects it rather than changing visibility mid-send.

## Data Model and API

### Migration

Add append-only Flyway migration `V65__sales_change_notice_visibility.sql`; do not modify `V64` or earlier migrations.

Add to `sales_order_change_notice`:

- `is_hidden BOOLEAN NOT NULL DEFAULT FALSE`;
- `hidden_reason VARCHAR(500)`;
- `hidden_at TIMESTAMP`;
- `hidden_by BIGINT` and `hidden_by_name VARCHAR(100)`;
- `restored_at TIMESTAMP` and `restored_by BIGINT`.

The boolean is the current visibility state. The remaining fields retain the latest hide/restore metadata; operation-log entries preserve each transition. Add an index supporting view filtering and creation-time ordering, subject to validation against current query plans.

### Endpoints

- `POST /api/change/sales-order-changes/batch-hide` with `{ "noticeIds": [...], "reason": "..." }`.
- `POST /api/change/sales-order-changes/batch-restore` with `{ "noticeIds": [...], "reason": "..." }`; `reason` is optional.
- Extend the existing paged list query with a view selector for active, hidden, and sent records.

All endpoints retain sales-order data-scope enforcement. Batch size follows the existing maximum of 1000 notice IDs.

### Permissions

Add `change:sales-change:hide` and `change:sales-change:restore` button permissions. Grant them to the same operational roles as `change:sales-change:send` (`SUPER_ADMIN`, `MARKET_USER`, and `MARKET_MANAGER`). The existing view permission continues to guard all three views. Backend permission checks remain authoritative.

## Audit and Documentation

Add audit actions `SALES_ORDER_CHANGE_NOTICE_HIDE` and `SALES_ORDER_CHANGE_NOTICE_RESTORE`. Record notice identifiers, order/item/Allocation identifiers, previous status, transition counts, operator, reason when present, and elapsed time. Do not record payload snapshots, external credentials, or full external requests/responses.

Update the sales change module development document, permission catalog, and operation-log documentation to describe the three views, soft-hide semantics, eligibility rules, audit actions, and auto-mode behavior.

## Validation Plan

- Verify default active view excludes hidden and `SENT` notices; hidden and sent views return only their respective records with existing data scope and filters applied.
- Verify batch hide requires a reason and rejects `SENDING` and `SENT` records without partially hiding an invalid batch.
- Verify batch restore clears current hidden state, preserves status and `noticeId`, and does not auto-dispatch restored pending backlog in auto mode.
- Verify manual and automatic claim queries both exclude hidden rows.
- Verify concurrent hide and claim operations cannot leave a row both hidden and `SENDING`.
- Verify a new modification merged into a hidden pending modification unhides it, keeps its `noticeId`, updates the snapshot and changed fields, and receives the current auto epoch.
- Verify repeated hide/restore actions are auditable and do not alter downstream identifiers or sales-order/Allocation state.
- Verify existing manual/automatic dispatch, retries, sent-history details, data-scope guards, and permission tests remain valid.
- Run focused backend and frontend tests, frontend build, Flyway policy tests, and `git diff --check` during implementation.

## Open Implementation Detail

The exact database index should be selected after inspecting the generated SQL and a representative `EXPLAIN`; avoid adding an index unless it improves the view query without excessive write cost.
