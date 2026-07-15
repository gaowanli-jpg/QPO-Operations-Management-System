# 16 Stage 6 Item Pricing Workbench Redesign

Date: 2026-07-15

Scope: ERP frontend item pricing workbench polish for `quote-frontend`.

Stage 6 focuses on `ItemPricing.vue`. It improves the visual hierarchy of the densest engineering pricing page while preserving calculation logic, submission behavior, API calls, Pinia/store data flow, route permissions, drawing preview behavior, and quote completion flow.

## Files Changed

| File | Purpose |
| --- | --- |
| `quote-frontend/src/components/ItemPricing.vue` | Moves the pricing page onto the shared ERP shell, adds order/workbench context metrics, normalizes the toolbar/table actions, and separates pricing dialog sections. |
| `quote-frontend/src/itemPricingStage6.test.js` | Guards the Stage 6 structure and confirms the pricing workflow remains in place rather than being split or rewritten. |
| `docs/erp-ui-redesign/16-stage-6-item-pricing-workbench.md` | Records Stage 6 scope, behavior preservation, verification, and next-step recommendation. |
| `AGENTS.md` | Updates long-term memory with Stage 6 completion and Stage 7 as the next UI stage. |

## What Changed

### Workbench Page Shell

- Replaced the old `pricing-container` / `main-card` shell with `erp-page` and `erp-page-header`.
- Added a clearer title, description, RFQ tag, and order status tag.
- Added a `pricing-context-card` showing the current RFQ context and quick metrics:
  - item count;
  - quoted item count;
  - repeat item count.

### Toolbar And Item Table

- Moved top-level actions into `erp-toolbar pricing-toolbar`.
- Kept the existing actions and handlers unchanged:
  - return to global setup;
  - generate quote sheet;
  - refresh pricing;
  - complete quote submission.
- Moved the item table into `erp-table-surface pricing-table-surface`.
- Updated drawing and pricing operation cells to use `erp-table-actions pricing-row-actions`.
- Kept the existing table data source, columns, row key, fixed columns, status display, drawing preview, and quote dialog entry behavior.

### Pricing Dialog Hierarchy

- Kept the existing pricing dialog component and all state inside `ItemPricing.vue`.
- Added clear section wrappers:
  - `pricing-mode-section` for quote type selection;
  - `pricing-material-section` for material and dimension inputs;
  - `pricing-process-section` for process route rows;
  - `pricing-assembly-section` for assembly child pricing.
- Added `pricing-workbench-summary` for material and assembly totals.
- Updated dialog drawing actions, process row actions, assembly child actions, and child picker drawing actions to use shared action classes.
- Wrapped pricing and assembly picker dialog footers with `erp-dialog-footer pricing-dialog-footer`.

### Scoped Styling

- Reworked `ItemPricing.vue` scoped styles around Stage 1 tokens and Stage 3/4 shared classes.
- Preserved local styles that are still specific to this dense workbench:
  - material picker trigger;
  - process route rows;
  - assembly child rows;
  - drawing pane and iframe preview;
  - pricing dialog grid and responsive fallback.

## Behavior Preserved

Stage 6 did not modify:

- API/service imports or calls;
- Pinia stores or store data flow;
- route guards or permissions;
- quote data loading and refresh behavior;
- material, process, fee, and assembly calculations;
- `submitCurrentQuote()` payload construction;
- order quote completion and export handlers;
- drawing preview and new-window behavior;
- component splitting or backend contracts.

## Verification

Red/green guard:

- `npm test -- itemPricingStage6.test.js`
- Initial result before implementation: 2 tests failed as expected because `ItemPricing.vue` still used the old local shell and ungrouped dialog sections.
- Final result: 1 test file passed; 3 tests passed.

Full verification:

- `npm test`
- Result: 17 test files passed; 78 tests passed.
- `npm run build`
- Result: build passed; Vite transformed 1684 modules and built successfully in 1.51s.

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-C3fycVl-.css` | 394.58 kB | 53.82 kB |
| `dist/assets/index-DLUTfrsY.js` | 1,339.85 kB | 412.98 kB |

Known warning: the main JS chunk remains larger than 500 kB after minification. This predates Stage 6 and remains deferred.

## Stage 7 Recommendation

The next step is Stage 7: Final Consistency Pass.

Recommended scope:

1. Review remaining ERP pages for old local page/card/header/table patterns.
2. Consolidate repeated local CSS only after confirming the shared replacement is already in use.
3. Keep behavior frozen and focus on consistency, obsolete class cleanup, and obvious layout regressions.
4. Run `npm test`, `npm run build`, and a visual walkthrough of the redesigned pages.

Defer:

- business workflow changes;
- new UI libraries;
- quote calculation changes;
- component splitting of `ItemPricing.vue` unless a later maintenance task explicitly targets it;
- bundle/code-splitting work until the visual consistency pass is complete.