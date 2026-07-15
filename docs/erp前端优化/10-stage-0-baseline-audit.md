# 10 Stage 0 Baseline Visual Audit

Date: 2026-07-15

Scope: ERP frontend UI redesign baseline for `quote-frontend`.

Stage 0 does not change production code. It records the current visual and engineering baseline before the redesign starts.

## Verification Baseline

| Check | Command | Result | Notes |
| --- | --- | --- | --- |
| Unit tests | `npm test` in `quote-frontend` | Passed | 11 test files passed; 59 tests passed. |
| Production build | `npm run build` in `quote-frontend` | Passed | Vite transformed 1684 modules and built successfully in 1.23s. |
| Build warning | `npm run build` in `quote-frontend` | Present | Main JS chunk is larger than 500 kB after minification. This is a future performance topic, not a blocker for visual Stage 1. |

Build output sizes:

| Asset | Size | Gzip |
| --- | ---: | ---: |
| `dist/index.html` | 0.46 kB | 0.29 kB |
| `dist/assets/index-BZ5DZLTZ.css` | 381.36 kB | 51.70 kB |
| `dist/assets/index-B4xv445v.js` | 1,332.42 kB | 411.23 kB |

## Screenshot Baseline Status

Required screenshot targets from the roadmap:

| Target | Route / Surface | Status |
| --- | --- | --- |
| Login | `/login` | Captured: `screenshots/stage0-login.png`. |
| App shell | authenticated shell on `/market` | Captured: `screenshots/stage0-app-shell-market.png`. |
| Market import | `/market` | Captured: `screenshots/stage0-market-import.png`. |
| Engineering setup | `/engineering` | Captured: `screenshots/stage0-engineering-setup.png`. |
| Item pricing | `/pricing?rfqId=...` | Captured: `screenshots/stage0-item-pricing.png`. |
| Order management | `/orders` | Captured: `screenshots/stage0-order-management.png`. |
| Material/process management | `/material-process` | Captured: `screenshots/stage0-material-process-management.png`. |

Screenshot capture was attempted without changing project dependencies:

- In-app browser runtime failed during setup with a runtime property conflict.
- Temporary Playwright package was available through npm cache.
- Playwright Chromium was not installed locally.
- `npm exec --yes --package playwright -- playwright install chromium` exceeded the command timeout and the expected Chromium executable was still absent after additional monitoring.
- The temporary Vite preview service used for the attempt was stopped after the check.

Follow-up resolution: screenshots were later captured with the local Microsoft Edge executable and a temporary Playwright package from the npm cache. No browser binary or screenshot dependency was added to the project.

## Current UI Code Inventory

| Surface | Main file | Lines | Scoped style lines | Raw visual matches | Stage implication |
| --- | --- | ---: | ---: | ---: | --- |
| App shell | `quote-frontend/src/App.vue` | 371 | 156 | 22 | Sidebar/header colors and layout are partly hardcoded; clean in Stage 2 after tokens are stable. |
| Login | `quote-frontend/src/components/LoginView.vue` | 125 | 54 | 15 | Self-contained and visually separate; good early candidate after shell tokens exist. |
| Market import | `quote-frontend/src/components/MarketDashboard.vue` | 444 | 140 | 39 | Upload/dropzone, card header, inline message HTML, and alert styling need shared patterns. |
| Engineering setup | `quote-frontend/src/components/GlobalSetup.vue` | 792 | 210 | 14 | Workflow is complex; preserve behavior until Stage 5. |
| Item pricing | `quote-frontend/src/components/ItemPricing.vue` | 1775 | 420 | 33 | Largest visual surface; defer heavy redesign until Stage 6. |
| Order management | `quote-frontend/src/components/OrderManage.vue` | 631 | 161 | 20 | Repeats page/card/table patterns and is a good Stage 3 or Stage 4 migration candidate. |
| Material/process management | `quote-frontend/src/components/MaterialProcessManage.vue` | 533 | 17 | 20 | Uses less CSS but has repeated admin-table layout needs. |
| Quote process | `quote-frontend/src/components/QuoteProcess.vue` | 556 | 163 | 16 | Shares market/order workflow styling; should follow shared page patterns later. |
| User management | `quote-frontend/src/components/UserManage.vue` | 282 | 23 | 7 | Simple admin page; useful as a low-risk shared layout migration page. |
| Quote template | `quote-frontend/src/components/QuoteTemplateManage.vue` | 245 | 74 | 6 | Upload/admin pattern should share the same surface and toolbar classes. |
| Global stylesheet | `quote-frontend/src/style.css` | 143 | n/a | 36 | Already contains starter ERP tokens; Stage 1 should expand and organize this file first. |

`Raw visual matches` counts occurrences of raw hex/rgb values, gradients, shadows, padding, margin, and radius declarations. It is a directional signal for local visual styling, not a defect count.

## Repeated Patterns Found

The current frontend already has the right business separation from the previous Axios/Pinia refactor, but the visual layer is still page-local.

Repeated local page patterns:

- Page containers: `market-container`, `setup-container`, `pricing-container`, `order-manage-container`, `manage-container`, `page`.
- Card shells: `main-card`, `manage-card`, `quote-card`, `template-card`.
- Headers: `card-header`, `toolbar`, `panel-title`, `section-title`, `panel-heading`.
- Action rows: `header-actions`, `toolbar-actions`, `detail-actions`, `batch-actions`.
- Empty and helper states: `empty-state`, `upload-alert`, `panel-desc`, `el-upload__tip`.
- Picker buttons: `picker-trigger`, `picker-trigger-action`, `full-width`.

These should become shared ERP layout/component classes over time:

- `erp-page`
- `erp-page-header`
- `erp-page-actions`
- `erp-toolbar`
- `erp-filter-bar`
- `erp-surface`
- `erp-section`
- `erp-table-surface`
- `erp-empty-state`

## Current Risks Before Redesign

| Risk | What it means | Recommended handling |
| --- | --- | --- |
| Page-local style drift | Each page defines its own header/card/table rhythm. | Stage 1 creates shared tokens; Stage 3 starts migrating simpler pages. |
| Hardcoded visual values | Colors and layout values exist inside page CSS and inline HTML strings. | Do not chase every value in Stage 1; first define tokens and Element Plus mappings. |
| Dense workbench complexity | `ItemPricing.vue` combines table, dialog, material, process, fees, and quote submission UI. | Keep calculation and store/API behavior untouched until Stage 6. |
| Shell polish dependency | Sidebar and header define much of the first impression. | Improve only after shared colors/spacing are stable. |
| Large bundle warning | Build passes, but the JS chunk is large. | Track separately after UI stages; avoid mixing visual cleanup with code splitting. |
| Screenshot tooling gap | Automated screenshots are not currently available in this environment. | Capture manually or retry automated capture once browser tooling is available. |

## Stage 0 Acceptance Review

| Acceptance item | Status | Evidence |
| --- | --- | --- |
| Login screenshot exists | Met | `docs/erp前端优化/screenshots/stage0-login.png`, 1440x960. |
| App shell screenshot exists | Met | `docs/erp前端优化/screenshots/stage0-app-shell-market.png`, 1440x960. |
| Market import screenshot exists | Met | `docs/erp前端优化/screenshots/stage0-market-import.png`, 1440x960. |
| Engineering setup screenshot exists | Met | `docs/erp前端优化/screenshots/stage0-engineering-setup.png`, 1440x960. |
| Item pricing screenshot exists | Met | `docs/erp前端优化/screenshots/stage0-item-pricing.png`, 1440x960. |
| Order management screenshot exists | Met | `docs/erp前端优化/screenshots/stage0-order-management.png`, 1440x960. |
| Material/process screenshot exists | Met | `docs/erp前端优化/screenshots/stage0-material-process-management.png`, 1440x960. |
| `npm test` passes | Met | 11 files, 59 tests passed. |
| `npm run build` passes | Met | Build completed successfully with one chunk-size warning. |

## Stage 1 Recommended Scope

Stage 1 should be a foundation-only change. It should not redesign individual pages yet.

Primary file:

- `quote-frontend/src/style.css`

Recommended work:

1. Expand the existing `:root` ERP tokens for color, typography, spacing, radius, shadow, z-index, and motion.
2. Map Element Plus variables to ERP tokens in one place.
3. Add shared base classes for page frame, surface, toolbar, table shell, empty state, and dialog footer.
4. Keep existing component-scoped CSS in place for now, unless a selector is clearly global and belongs in `style.css`.
5. Do not change Pinia stores, services, API calls, routing, permissions, calculation logic, or page behavior.

Stage 1 acceptance should be:

- `npm test` passes in `quote-frontend`.
- `npm run build` passes in `quote-frontend`.
- No new page-specific raw colors are introduced.
- Existing pages remain usable.
- Shared tokens/classes are available for Stage 2 and Stage 3 migrations.

## Do Not Change Yet

- Do not split `ItemPricing.vue` during Stage 1.
- Do not change pricing calculations or quote submission behavior.
- Do not change route permissions or auth behavior.
- Do not introduce another UI framework.
- Do not remove existing page CSS before replacement classes are proven on simpler pages.
- Do not mix screenshot tooling installation into the frontend dependency list.
