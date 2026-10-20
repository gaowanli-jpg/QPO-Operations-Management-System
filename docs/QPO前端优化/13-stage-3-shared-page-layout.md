# 13 Stage 3 Shared Page Layout

Date: 2026-07-15

Scope: QPO frontend shared page layout migration for `quote-frontend`.

Stage 3 moves three simpler business pages onto the shared Stage 1 layout classes. It keeps API calls, Pinia stores, route permissions, upload logic, CRUD actions, dialogs, and quotation calculations unchanged.

## Files Changed

| File | Purpose |
| --- | --- |
| `quote-frontend/src/components/MarketDashboard.vue` | Replaces the old card shell with `erp-page`, `erp-page-header`, `erp-surface`, and a shared toolbar footer. |
| `quote-frontend/src/components/OrderManage.vue` | Moves page title, filters, table body, and pagination into shared page, toolbar, table surface, and pagination classes. |
| `quote-frontend/src/components/MaterialProcessManage.vue` | Moves dictionary management title, action row, tabs, and table area into shared page, toolbar, and table surface classes. |
| `quote-frontend/src/pageLayoutStage3.test.js` | Guards the Stage 3 shared page layout migration for all three pages. |
| `docs/QPO前端优化/screenshots/stage3-*.png` | Adds visual check screenshots for the migrated order and material/process pages. |

## What Changed

### Market Import

- Replaced `market-container` plus `main-card` with an `erp-page` shell.
- Added an `erp-page-header` with title and short operational description.
- Kept the existing customer form, Excel upload, drawing upload, folder drop behavior, and submit action.
- Kept the existing section-by-section workflow; only the outer surface now uses shared layout classes.

### Order Management

- Replaced the old `el-card` page wrapper with `erp-page`, `erp-page-header`, `erp-toolbar`, and `erp-table-surface`.
- Kept customer loading, order loading, filters, pagination, detail drawer, supplement drawing dialog, and reset production actions unchanged.
- Moved pagination into `erp-pagination`.
- Preserved control widths so the filter row stays readable at desktop widths and wraps at smaller widths.

### Material / Process Management

- Replaced the old `el-card` page wrapper with `erp-page`, `erp-page-header`, `erp-toolbar`, and `erp-table-surface`.
- Kept dictionary store usage, CRUD requests, tabs, dialog forms, delete confirmations, and reload-after-mutation behavior unchanged.
- Preserved the existing tab and table contents while moving the surrounding frame onto shared classes.

## Behavior Preserved

Stage 3 did not modify:

- service/API modules;
- Pinia stores;
- route guards or role permissions;
- upload parsing/submission logic;
- order CRUD/detail/supplement behavior;
- dictionary CRUD behavior;
- quote/pricing calculations;
- dialog form fields or validation behavior.

## Visual Checks

Generated Stage 3 screenshots:

- `stage3-order-management.png`, 1440x960.
- `stage3-material-process-management.png`, 1440x960.

Capture method:

- Used local Microsoft Edge executable.
- Used temporary Playwright package from npm cache.
- Added no browser binary or screenshot dependency to the project.
- Used mocked API responses only for screenshot navigation.

## Verification

Targeted layout test:

- `npm test -- pageLayoutStage3.test.js`
- Result: 1 test file passed; 3 tests passed.

Full verification:

- `npm test`
- Result: 14 test files passed; 69 tests passed.
- `npm run build`
- Result: build passed; Vite transformed 1684 modules and built successfully in 1.60s.

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-DEsSF4x8.css` | 389.87 kB | 53.25 kB |
| `dist/assets/index-Bbo5MkFa.js` | 1,334.49 kB | 411.86 kB |

Known warning: the main JS chunk remains larger than 500 kB after minification. This predates Stage 3 and remains deferred.

## Stage 4 Recommendation

The next step is Stage 4: Dialog/Form/Table Normalization.

Recommended scope:

1. Start with the pages already touched in Stage 3:
   - `OrderManage.vue`
   - `MaterialProcessManage.vue`
2. Add `QuoteTemplateManage.vue` and `UserManage.vue` only if the first two remain small.
3. Normalize dialog titles, footers, form label widths, destructive actions, upload areas, table action columns, empty states, and error states.
4. Continue preserving API calls, Pinia state, route permissions, and business behavior.

Defer:

- `GlobalSetup.vue` workflow redesign until Stage 5;
- `ItemPricing.vue` workbench redesign until Stage 6;
- broad local CSS cleanup until Stage 7;
- bundle/code-splitting work until visual stages are stable.
