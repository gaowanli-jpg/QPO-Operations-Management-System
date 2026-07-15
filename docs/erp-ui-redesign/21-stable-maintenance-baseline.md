# 21 Stable Maintenance Baseline

Scope: final ERP UI baseline freeze after staged redesign, browser walkthrough, punch-list sprint, and first-pass code splitting.

## Status

The ERP UI redesign is complete and has entered stable maintenance. The current `quote-frontend` UI should be treated as the baseline for future work.

Future ERP UI changes should be feature-scoped, evidence-driven, and consistent with the existing token and shared-class system.

## Required Patterns

New pages and substantially edited pages must use the shared ERP layout and component classes already defined in `quote-frontend/src/style.css`:

- `erp-page`
- `erp-page-header`
- `erp-toolbar`
- `erp-surface`
- `erp-table-surface`
- `erp-table-actions`
- `erp-dialog-form`
- `erp-dialog-section`
- `erp-dialog-footer`
- `erp-danger-action`

Use Element Plus components through these shared classes and the existing ERP tokens. Page-local CSS should only solve truly local layout needs that cannot belong to the shared layer.

## Not Allowed

- Do not restart a broad UI redesign without a new approved roadmap.
- Do not add one-off page-local card/header/table/dialog/button systems.
- Do not introduce another UI library.
- Do not change Pinia, API services, route permissions, quote calculation, or submission behavior as part of visual maintenance.
- Do not split `ItemPricing.vue` only for aesthetics; handle component decomposition as a separate maintainability task if needed.

## Allowed Maintenance

- Add a new business page using the existing ERP shell and shared page/table/dialog classes.
- Extend `style.css` tokens or shared classes when a pattern repeats across pages.
- Fix small alignment, spacing, overflow, empty-state, dialog footer, or action-button issues.
- Add page-local CSS only for unique workflow geometry, then promote it to shared CSS if it repeats.
- Add regression tests for static UI contracts when they protect an important baseline rule.

## Review Checklist

Before accepting a future ERP UI change:

1. The page uses `erp-page` and a shared page header pattern.
2. Filters and action rows use `erp-toolbar` or a clearly related shared workflow class.
3. Data containers use `erp-surface` or `erp-table-surface`.
4. Table operation buttons use `erp-table-actions`.
5. Dialog forms and footers use `erp-dialog-form` and `erp-dialog-footer`.
6. Destructive actions use the shared danger style.
7. No new local visual system duplicates an existing shared ERP class.
8. `npm test` and `npm run build` pass in `quote-frontend`.

## Next Work

Treat the ERP UI and first-pass performance baseline as frozen. Future work should be separate tracks:

- Feature-specific UI additions that follow the baseline.
- Evidence-driven performance profiling if startup remains slow.
- Maintainability refactors only when they reduce actual implementation risk.
- API, Pinia, permissions, or pricing changes only in their own scoped tasks.
