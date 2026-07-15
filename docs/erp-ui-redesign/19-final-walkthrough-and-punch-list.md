# 19 Final Walkthrough And Punch List

Scope: small post-walkthrough ERP UI polish sprint after `19-final-browser-walkthrough.md`.

## Goal

Resolve only concrete visual issues found during final browser review. This sprint intentionally avoids broad redesign, business behavior changes, API changes, Pinia changes, route changes, pricing logic changes, new UI libraries, and component splitting.

## Punch List

| Item | Area | Change | Files |
| --- | --- | --- | --- |
| 1 | Market import upload fallback | Replaced the native-looking small folder button with the existing quiet workflow button style and gave the row consistent inline spacing. | `quote-frontend/src/components/MarketDashboard.vue` |
| 2 | Dense table operations | Added a shared minimum height and horizontal padding for small buttons inside shared table action groups, so edit/delete/save actions are less cramped while preserving dense desktop tables. | `quote-frontend/src/style.css` |
| 3 | Regression guard | Added a static guard so these final punch-list rules do not drift later. | `quote-frontend/src/finalPunchList.test.js` |

## Non-Changes

- Did not change API calls.
- Did not change Pinia stores.
- Did not change route permissions.
- Did not change quote calculation or submission behavior.
- Did not split `ItemPricing.vue`.
- Did not introduce another UI library.
- Did not redesign the quote-process or market-release workflow layout again.

## Acceptance

- `MarketDashboard.vue` uses `workflow-soft-button folder-select-button` for the folder-select fallback action.
- Small Element Plus buttons inside `erp-table-actions` have a shared minimum height.
- Workflow soft small buttons share the same minimum height rule.
- The final punch-list test covers the above UI decisions.
- `npm test` and `npm run build` must pass in `quote-frontend` before commit.

## Next Step

The ERP UI baseline can now be treated as frozen. Future work should be handled as separate tracks:

1. Performance/code splitting for the large Vite chunk warning.
2. New feature-specific UI work only when a new feature requires it.
3. Component decomposition only if it is needed for maintainability, not for visual polish.
