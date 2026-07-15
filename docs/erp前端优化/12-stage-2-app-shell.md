# 12 Stage 2 App Shell Polish

Date: 2026-07-15

Scope: ERP frontend app shell polish for `quote-frontend`.

Stage 2 improves the sidebar, header, active navigation, user controls, and app content spacing. It does not change authentication behavior, route permissions, Pinia stores, API calls, or business pages.

## Files Changed

| File | Purpose |
| --- | --- |
| `quote-frontend/src/App.vue` | Reworks shell navigation labels, route-to-active-menu mapping, header context, user card, and shell CSS. |
| `quote-frontend/src/style.css` | Adds rail-specific design tokens for the app shell. |
| `quote-frontend/src/appShell.test.js` | Guards Stage 2 shell structure, short labels, tokenized colors, and `/pricing` active route behavior. |
| `docs/erp前端优化/screenshots/*.png` | Adds Stage 0 baseline screenshots and Stage 2 visual check screenshots. |
| `docs/erp前端优化/10-stage-0-baseline-audit.md` | Updates Stage 0 screenshot acceptance from missing to complete. |
| `docs/erp前端优化/screenshots/README.md` | Documents the local Edge screenshot fallback. |

## Screenshot Gap Resolution

The Stage 0 screenshot gap was resolved before Stage 2 implementation.

Generated baseline screenshots:

- `stage0-login.png`
- `stage0-app-shell-market.png`
- `stage0-market-import.png`
- `stage0-engineering-setup.png`
- `stage0-item-pricing.png`
- `stage0-order-management.png`
- `stage0-material-process-management.png`

Generated Stage 2 visual check screenshots:

- `stage2-app-shell-market.png`
- `stage2-app-shell-pricing-active.png`

Capture method:

- Used local Microsoft Edge executable.
- Used temporary Playwright package from npm cache.
- Added no browser binary or screenshot dependency to the project.
- Used mocked API responses only for screenshot navigation.

## What Changed

### Sidebar

- Replaced long navigation labels with shorter operational labels.
- Split navigation into visible groups: Market, Engineering, Orders, System.
- Removed hardcoded Element Plus menu colors from template attributes.
- Added clearer active state with an accent indicator.
- Mapped `/pricing` to keep Engineering Setup active, since pricing belongs to the engineering workflow.

### Header

- Replaced generic system-status text with route-aware module title and description.
- Added a compact user card with avatar, display name, and role pill.
- Kept password change and logout controls in the same behavior path.
- Separated destructive logout visually using Element Plus danger text button styling.

### Shell Styling

- Added shell rail tokens in `style.css`:
  - `--erp-rail-text`
  - `--erp-rail-muted`
  - `--erp-rail-active`
  - `--erp-rail-active-bg`
  - `--erp-rail-border`
- Reworked shell spacing using Stage 1 token values.
- Kept content padding consistent with the Stage 1 spacing foundation.

## Behavior Preserved

Stage 2 did not modify:

- auth store behavior;
- route guard behavior;
- role visibility logic;
- password dialog validation or submit behavior;
- logout action behavior;
- business page templates;
- pricing or quotation logic.

## Verification

Targeted shell test:

- `npm test -- appShell.test.js`
- Result: 1 test file passed; 4 tests passed.

Full verification:

- `npm test`
- Result: 13 test files passed; 66 tests passed.
- `npm run build`
- Result: build passed; Vite transformed 1684 modules and built successfully in 1.35s.

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-uelOhomi.css` | 391.17 kB | 53.31 kB |
| `dist/assets/index-Cv7Dug5o.js` | 1,334.03 kB | 411.66 kB |

Known warning: the main JS chunk remains larger than 500 kB after minification. This was present before Stage 2 and remains deferred.

## Stage 3 Recommendation

The next step is Stage 3: Shared Page Layout Classes.

Recommended scope:

1. Migrate 2-3 simpler business pages to the shared Stage 1 classes.
2. Best candidates from the roadmap and current code are:
   - `MarketDashboard.vue`
   - `OrderManage.vue`
   - `MaterialProcessManage.vue`
3. Use `erp-page`, `erp-page-header`, `erp-toolbar`, `erp-surface`, and `erp-table-surface` where practical.
4. Keep API calls, Pinia state, route permissions, upload logic, and CRUD behavior unchanged.
5. Re-run `npm test`, `npm run build`, and capture one visual check screenshot for at least one migrated page.

Defer:

- dialog/form/table deep normalization until Stage 4;
- `GlobalSetup.vue` workflow redesign until Stage 5;
- `ItemPricing.vue` workbench redesign until Stage 6;
- bundle/code-splitting work until visual stages are stable.
