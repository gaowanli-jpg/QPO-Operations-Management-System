# 14 Stage 4 Dialog/Form/Table Normalization

Date: 2026-07-15

Scope: ERP frontend dialog, form, table action, upload, empty-state, and destructive-action normalization for `quote-frontend`.

Stage 4 starts from the two Stage 3 business pages the user selected: `OrderManage.vue` and `MaterialProcessManage.vue`. It keeps APIs, Pinia stores, route permissions, CRUD behavior, upload behavior, and quotation logic unchanged.

## Files Changed

| File | Purpose |
| --- | --- |
| `quote-frontend/src/style.css` | Adds shared classes for dialog forms, dialog sections, table action groups, danger actions, and upload surfaces. |
| `quote-frontend/src/components/OrderManage.vue` | Applies shared action, danger, dialog-section, upload-surface, and footer classes to order actions and supplement drawing upload dialog. |
| `quote-frontend/src/components/MaterialProcessManage.vue` | Applies shared form, footer, action-group, and danger-action classes to material/process/fee dictionary dialogs and operation columns. |
| `quote-frontend/src/pageNormalizationStage4.test.js` | Guards Stage 4 shared class coverage for styles and the two migrated pages. |
| `docs/erp-ui-redesign/14-stage-4-dialog-form-table-normalization.md` | Records Stage 4 scope, verification, and next-step recommendation. |

## What Changed

### Shared Visual Classes

`style.css` now includes shared normalization classes for later stages:

- `erp-dialog-form` for compact, consistent Element Plus dialog forms.
- `erp-dialog-section` for grouped dialog content such as order context and related tables.
- `erp-table-actions` for stable operation-column spacing without Element Plus button margin drift.
- `erp-danger-action` for visually separated destructive or reset actions.
- `erp-upload-surface` for upload/drop areas that match the ERP surface system.
- `erp-dialog-footer` now wraps cleanly on smaller widths.

### Order Management

- The order table operation column now uses `erp-table-actions`.
- The reset production action is visually marked as destructive with `erp-danger-action`.
- The supplement drawing dialog groups order context and missing drawing items with `erp-dialog-section`.
- The supplement upload area now uses `erp-upload-surface`.
- The supplement dialog footer now uses `erp-dialog-footer`.

### Material / Process Management

- Material, process, markup fee, and extra fee tables now use `erp-table-actions` in operation columns.
- Delete buttons in all four dictionary tables now use `erp-danger-action`.
- All four dictionary edit dialogs now use `erp-dialog-form`.
- All four dictionary dialog footers now use `erp-dialog-footer`.

## Behavior Preserved

Stage 4 did not modify:

- service/API modules;
- Pinia stores;
- router guards or role permissions;
- dictionary CRUD request functions;
- order detail, reset, and supplement upload functions;
- upload parsing/submission logic;
- quote/pricing calculations.

## Verification

Red/green guard:

- `npm test -- pageNormalizationStage4.test.js`
- Initial result before implementation: 3 tests failed as expected because shared Stage 4 classes were missing.
- Final result: 1 test file passed; 3 tests passed.

Full verification:

- `npm test`
- Result: 15 test files passed; 72 tests passed.
- `npm run build`
- Result: build passed; Vite transformed 1684 modules and built successfully in 1.21s.

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-DxMlv13-.css` | 391.05 kB | 53.39 kB |
| `dist/assets/index-C0gmXABF.js` | 1,335.24 kB | 411.98 kB |

Known warning: the main JS chunk remains larger than 500 kB after minification. This predates Stage 4 and remains deferred.

## Stage 5 Recommendation

The next step is Stage 5: Engineering Setup Redesign.

Recommended scope:

1. Modify `quote-frontend/src/components/GlobalSetup.vue` first, using the Stage 1 tokens and Stage 4 dialog/form/action classes.
2. Make selected order context more prominent.
3. Group drawing preview, global configuration fields, and save/continue actions into clearer ERP sections.
4. Preserve existing `globalConfigStore`, `engineerApi`, route flow, drawing upload behavior, and engineer configuration persistence.
5. Re-run `npm test` and `npm run build`.

Defer:

- `ItemPricing.vue` workbench redesign until Stage 6;
- broad local CSS cleanup until Stage 7;
- bundle/code-splitting work until visual stages are stable.
