# 17 Stage 7 Final Consistency Pass

Date: 2026-07-15

Scope: ERP frontend final visual consistency pass for `quote-frontend`.

Stage 7 focuses on cleaning remaining local card/header/table/dialog patterns after the staged ERP UI redesign. It keeps business workflows, API calls, Pinia/store behavior, permissions, quote calculations, exports, uploads, and submission behavior unchanged.

## Files Changed

| File | Purpose |
| --- | --- |
| `quote-frontend/src/components/HistoryOrderManage.vue` | Replaces the old history card shell with shared page, toolbar, table surface, empty state, pagination, and table action classes. |
| `quote-frontend/src/components/MarketRelease.vue` | Replaces the old release card shell with shared page/header/workspace surfaces and normalizes destructive table actions. |
| `quote-frontend/src/components/QuoteProcess.vue` | Replaces the old quote process card shell with shared page/header/workspace surfaces and shared batch action styling. |
| `quote-frontend/src/components/QuoteTemplateManage.vue` | Replaces the old template card shell with shared page, toolbar, table surface, upload surface, and footer classes. |
| `quote-frontend/src/components/UserManage.vue` | Replaces the old user card shell with shared page/table/dialog patterns and shared table action/footer classes. |
| `quote-frontend/src/finalConsistencyStage7.test.js` | Guards the remaining page shell, table/action, and dialog consistency expectations. |
| `docs/erp-ui-redesign/17-stage-7-final-consistency.md` | Records Stage 7 scope, behavior preservation, verification, and follow-up recommendations. |
| `AGENTS.md` | Updates long-term memory with Stage 7 completion and post-redesign follow-up guidance. |

## What Changed

### Remaining Page Shells

- Removed old local card-shell patterns from:
  - `HistoryOrderManage.vue`;
  - `MarketRelease.vue`;
  - `QuoteProcess.vue`;
  - `QuoteTemplateManage.vue`;
  - `UserManage.vue`.
- Replaced old `el-card` / `template #header` wrappers with shared `erp-page` and `erp-page-header` patterns.
- Kept dense page-local panel classes only where they describe specific workflow regions, not general page chrome.

### Tables And Actions

- Moved remaining list/table-heavy pages onto `erp-table-surface` where appropriate.
- Replaced old bare action columns with `erp-table-actions` on user/history/destructive operations.
- Marked destructive table actions with `erp-danger-action` where the action is irreversible or administrative.
- Kept table data, selection, pagination, fixed columns, exports, and upload handlers unchanged.

### Dialogs And Uploads

- Moved user create/edit and reset-password dialogs onto `erp-dialog-form` and `erp-dialog-footer`.
- Moved quote template upload controls into `erp-upload-surface` and used a shared footer action row.
- Left existing Element Plus dialog/drawer behavior and confirmation flows unchanged.

## Behavior Preserved

Stage 7 did not modify:

- service/API modules;
- Pinia stores;
- router guards or role permissions;
- user CRUD payloads and password reset logic;
- quote template upload behavior;
- history order query/detail/delete behavior;
- market release selection, delivery-date, ticket, and process-sheet behavior;
- quote process lead-time and export behavior;
- pricing calculations or `ItemPricing.vue` component boundaries.

## Verification

Red/green guard:

- `npm test -- finalConsistencyStage7.test.js`
- Initial result before implementation: 3 tests failed as expected because the remaining pages still used old local card shells and unshared action/dialog patterns.
- Final result: 1 test file passed; 3 tests passed.

Full verification:

- `npm test`
- Result: 18 test files passed; 81 tests passed.
- `npm run build`
- Result: build passed; Vite transformed 1684 modules and built successfully in 1.36s.

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-CWZ_KG30.css` | 393.50 kB | 53.77 kB |
| `dist/assets/index-DPSeisoa.js` | 1,342.12 kB | 413.41 kB |

Known warning: the main JS chunk remains larger than 500 kB after minification. This predates Stage 7 and remains deferred.

## Post-Redesign Recommendation

The staged ERP UI redesign is now structurally complete.

Recommended next step:

1. Run a browser walkthrough on the main ERP pages using the dev server.
2. Capture updated screenshots into `docs/erp-ui-redesign/screenshots/` if visual evidence is needed for comparison.
3. Address only obvious visual regressions from the walkthrough.
4. Defer bundle/code-splitting and component decomposition to separate technical tasks.

Defer:

- business workflow changes;
- new UI libraries;
- quote calculation changes;
- API/Pinia refactors;
- broad component splitting unless a future task explicitly targets maintainability.