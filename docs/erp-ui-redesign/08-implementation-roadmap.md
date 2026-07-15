# 08 Implementation Roadmap

This is a staged roadmap for visual cleanup. Each stage should be small enough to test and review.

## Stage 0: Baseline Visual Audit

Goal:

- Capture current screenshots for major ERP pages.
- Record current build/test baseline.

Files:

- No production code required.
- Optional: `docs/erp-ui-redesign/screenshots/`.

Acceptance:

- Screenshots exist for Login, App shell, Market import, Engineering setup, Item pricing, Order management, Material/process management.
- `npm test` and `npm run build` pass in `quote-frontend`.

## Stage 1: Token And Style File Foundation

Goal:

- Convert current `style.css` into a more structured design-token foundation without changing page behavior.

Files:

- Modify: `quote-frontend/src/style.css`
- Optional create later:
  - `quote-frontend/src/styles/tokens.css`
  - `quote-frontend/src/styles/element-plus.css`
  - `quote-frontend/src/styles/layout.css`
  - `quote-frontend/src/styles/components.css`

Acceptance:

- Existing pages visually remain usable.
- Element Plus primary color, borders, table headers, inputs, dialogs, and buttons use shared tokens.
- No page-specific color hardcoding is introduced.

## Stage 2: App Shell Polish

Goal:

- Clean sidebar, header, active navigation, and page content spacing.

Files:

- Modify: `quote-frontend/src/App.vue`
- Modify: `quote-frontend/src/style.css`

Acceptance:

- Sidebar labels are shorter and grouped clearly.
- Active route is obvious.
- Header shows useful context and user controls cleanly.
- App content spacing is consistent.

## Stage 3: Shared Page Layout Classes

Goal:

- Introduce shared ERP page classes and migrate 2-3 simpler pages first.

Candidate files:

- `MarketDashboard.vue`
- `OrderManage.vue`
- `MaterialProcessManage.vue`

Acceptance:

- Pages use `erp-page`, `erp-page-header`, `erp-toolbar`, `erp-surface`, and `erp-table-surface` style patterns.
- Existing business behavior remains unchanged.
- Tables, filter bars, and action rows visually align.

## Stage 4: Dialog/Form/Table Normalization

Goal:

- Make dialogs, forms, uploads, table action columns, empty states, and error states consistent.

Candidate files:

- `OrderManage.vue`
- `MaterialProcessManage.vue`
- `QuoteTemplateManage.vue`
- `UserManage.vue`

Acceptance:

- Dialog titles and footers are consistent.
- Destructive actions are visually separated.
- Forms have consistent label widths and spacing.
- Upload areas use the same style pattern.

## Stage 5: Engineering Setup Redesign

Goal:

- Refine `GlobalSetup.vue` into a clearer engineering setup workflow.

Files:

- Modify: `GlobalSetup.vue`
- Modify shared styles only as needed.

Acceptance:

- Selected order context is prominent.
- Drawing preview and actions are clear.
- Global configuration fields are grouped logically.
- Save/continue action is obvious.

## Stage 6: Item Pricing Workbench Redesign

Goal:

- Improve the densest page without rewriting calculation logic.

Files:

- Modify: `ItemPricing.vue`
- Optional future split if needed:
  - `components/pricing/PricingWorkbenchDialog.vue`
  - `components/pricing/PricingMaterialSection.vue`
  - `components/pricing/PricingProcessSection.vue`
  - `components/pricing/PricingSummary.vue`

Acceptance:

- Item table is easier to scan.
- Pricing dialog/workbench has clear section hierarchy.
- Material, process, and fees sections are visually distinct.
- Calculations and submission behavior are unchanged.

## Stage 7: Final Consistency Pass

Goal:

- Remove obsolete local CSS and align remaining pages.

Acceptance:

- No new page invents its own card/header/table pattern without reason.
- Repeated classes are consolidated.
- `npm test` passes.
- `npm run build` passes.
- Manual page walkthrough confirms no obvious layout regressions.

