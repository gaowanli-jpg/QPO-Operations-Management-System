# 15 Stage 5 Engineering Setup Redesign

Date: 2026-07-15

Scope: ERP frontend engineering setup workflow polish for `quote-frontend`.

Stage 5 focuses on `GlobalSetup.vue`. It keeps `ItemPricing.vue` deferred to Stage 6 and does not change API calls, Pinia stores, route permissions, drawing preview mechanics, order configuration persistence, repeat-order logic, or pricing calculations.

## Files Changed

| File | Purpose |
| --- | --- |
| `quote-frontend/src/components/GlobalSetup.vue` | Moves the engineering setup page onto the shared ERP shell and reorganizes order context, drawing review, configuration groups, and save/continue action. |
| `quote-frontend/src/engineeringSetupStage5.test.js` | Guards the Stage 5 structure and confirms `ItemPricing.vue` remains deferred. |
| `docs/erp前端优化/15-stage-5-engineering-setup.md` | Records Stage 5 scope, behavior preservation, verification, and next-step recommendation. |
| `AGENTS.md` | Updates long-term memory with Stage 5 completion and Stage 6 as the next UI stage. |

## What Changed

### Engineering Page Shell

- Replaced the old local `setup-container` / `test-header` shell with `erp-page` and `erp-page-header`.
- Added a clear page title and description for the engineering setup workflow.
- Kept the existing "choose order" and "refresh current order" actions in the page header.

### Selected Order Context

- Added a prominent `engineering-context-card` below the page header.
- Shows current RFQ/customer context and quick metrics:
  - item count;
  - repeat item count;
  - new item count;
  - common process route count.
- Added an empty state when no order is selected, so the page communicates the next action before data loads.

### Drawing Review

- Moved the order item list into an `erp-table-surface` panel.
- Kept the existing item columns, repeat-item tags, drawing preview, and authenticated new-window behavior.
- Updated drawing actions to use `erp-table-actions`, matching Stage 4 operation-column styling.
- Moved the drawing preview "open in new window" action into a consistent `erp-dialog-footer`.

### Global Configuration

- Moved the default material/shape controls and process route controls into separate `erp-dialog-section` blocks.
- Kept the existing material picker, process picker, shape select, process ordering, and remove/add process behavior.
- Kept the existing `globalConfigStore` and `saveOrderConfig()` flow.

### Save / Continue

- Added a dedicated `engineering-save-panel` so the save-and-continue action is visually obvious.
- Kept the existing `handleNext()` behavior:
  - validate selected order;
  - assign process sort numbers;
  - save order config;
  - update `globalConfigStore`;
  - apply repeat quotes when available;
  - route to `/pricing?rfqId=...`.

## Behavior Preserved

Stage 5 did not modify:

- service/API modules;
- Pinia stores;
- router guards or role permissions;
- dictionary loading;
- order picker behavior;
- drawing preview/download authentication behavior;
- repeat-order analysis and apply-repeat flow;
- `ItemPricing.vue`;
- quote/pricing calculations.

## Verification

Red/green guard:

- `npm test -- engineeringSetupStage5.test.js`
- Initial result before implementation: 2 tests failed as expected because the page still used the old local shell.
- Final result: 1 test file passed; 3 tests passed.

Full verification:

- `npm test`
- Result: 16 test files passed; 75 tests passed.
- `npm run build`
- Result: build passed; Vite transformed 1684 modules and built successfully in 2.27s.

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-CZM1gEel.css` | 392.87 kB | 53.62 kB |
| `dist/assets/index-C7_fNbGg.js` | 1,337.24 kB | 412.47 kB |

Known warning: the main JS chunk remains larger than 500 kB after minification. This predates Stage 5 and remains deferred.

## Stage 6 Recommendation

The next step is Stage 6: Item Pricing Workbench Redesign.

Recommended scope:

1. Start with `quote-frontend/src/components/ItemPricing.vue`.
2. Use Stage 1 tokens, Stage 3 page/surface classes, Stage 4 dialog/action patterns, and the clearer Stage 5 engineering workflow as the visual direction.
3. Improve scanability of the item table and pricing dialog/workbench hierarchy.
4. Visually separate material, process, fee, and summary sections.
5. Preserve all existing calculation logic, submission behavior, `globalConfigStore` integration, dictionary store usage, and backend API calls.
6. Add a Stage 6 static guard before implementation, then run `npm test` and `npm run build`.

Defer:

- broad local CSS cleanup until Stage 7;
- component splitting unless the dense page becomes too risky to edit safely in place;
- bundle/code-splitting work until visual stages are stable.
