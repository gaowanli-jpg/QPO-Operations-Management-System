# 18 Post Stage 7 Workflow Page Polish

Scope: QPO frontend visual polish for the two dense market workflow pages after the staged redesign:

- `quote-frontend/src/components/QuoteProcess.vue`
- `quote-frontend/src/components/MarketRelease.vue`

## Goal

The user identified that quote processing and market release still felt visually noisy after Stage 7. The specific issue was not page shell inconsistency, but the dense concentration of colorful buttons and interactive controls in the left workflow panels.

This pass refines quote processing into a calmer workflow workbench, while keeping market release compact because its tighter original table rhythm is better for frequent operator actions. Business behavior is unchanged.

## Design Decisions

- Keep one clear primary action per workflow zone.
- Replace success/warning colored batch buttons with neutral workflow buttons.
- Use workflow workbench classes only where they improve clarity, mainly on `QuoteProcess.vue`.
- Keep `MarketRelease.vue` compact instead of adding wider workflow spacing.
- Preserve semantic danger styling only for destructive table actions.

## Files Changed

| File | Purpose |
| --- | --- |
| `quote-frontend/src/style.css` | Adds shared `workflow-*` classes for dense business workbenches, quiet secondary buttons, action stacks, and grouped batch panels. |
| `quote-frontend/src/components/QuoteProcess.vue` | Applies workflow layout classes, reduces quote adjustment/export button color noise, and lays out manual quote-date selection as one compact row. |
| `quote-frontend/src/components/MarketRelease.vue` | Restores the tighter Stage 7 layout, keeps selected secondary actions visually quieter, and lays out manual internal target-date selection as one compact row. |
| `quote-frontend/src/workflowPolish.test.js` | Guards the quote workflow classes, one-row manual quote/internal target-date controls, compact market release layout, quiet action styling, and key click handlers. |

## Behavior Preserved

This pass does not modify:

- API calls;
- Pinia/store behavior;
- route or permission logic;
- quote calculation logic;
- upload, export, submit, delete, or save handlers;
- existing table columns or row selection behavior.

## Follow-Up

The next useful step is a browser walkthrough and updated screenshots for `QuoteProcess.vue` and `MarketRelease.vue`, preferably with realistic order data. Further component splitting or workflow logic changes should remain separate from this visual polish.
