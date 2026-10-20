# Sales Order Detail Drawer Width Design

## Status

Design approved by the user on 2026-10-20. The user selected approach B: the sales-order detail drawer follows the sidebar's right edge and fills the remaining viewport width. Implementation proceeds under this approved design.

## Context

The sales-order detail drawer in `quote-frontend/src/components/SalesOrderManage.vue` currently uses a fixed `76%` width. The application sidebar is controlled by `quote-frontend/src/App.vue`: it is 200px when expanded and 72px when collapsed. At viewport widths of 760px or less, the sidebar is always 72px wide.

The user wants more room to inspect and operate on a sales order. They confirmed that the drawer should begin at the sidebar's right edge, extend to the right edge of the browser window, and follow the sidebar when it is expanded or collapsed.

## Goals

- Align the detail panel's left edge with the effective right edge of the sidebar.
- Fill the rest of the viewport horizontally, through the browser's right edge.
- Keep the alignment correct when the sidebar changes between 200px and 72px, including the narrow-screen 72px sidebar rule.
- Preserve existing detail content, scrolling, close behavior, permissions, API calls, and business operations.

## Non-goals

- Do not change sales-order data, detail tabs, forms, or operation behavior.
- Do not change sidebar widths, collapse behavior, or responsive breakpoints.
- Do not change the drawer's vertical sizing, modal behavior, or dismissal rules.
- Do not add a new UI library or alter permission or audit behavior.

## Approaches Considered

1. **Keep a viewport percentage with a larger fixed value.** This is a small change, but it cannot reliably align the panel with the sidebar in both expanded and collapsed states or at narrow widths.
2. **Tie the drawer's left edge and width to the effective sidebar width.** Recommended. It implements the selected B layout directly and adapts to the existing collapse and narrow-screen behavior.
3. **Add a user-adjustable drawer width.** This could support personal preferences, but adds interaction and state management beyond the requested layout.

## Proposed Behavior

- The drawer begins at the sidebar's right edge and ends at the viewport's right edge.
- With the expanded desktop sidebar, the drawer starts at 200px and uses the remaining viewport width.
- With the collapsed sidebar, the drawer starts at 72px and uses the remaining viewport width.
- At viewport widths of 760px or less, the existing responsive sidebar width of 72px determines the drawer offset and width, whether or not the collapse control is active.
- The panel retains its current full-height behavior and its existing content and interactions. Its internal content continues to scroll within the drawer as it does today.
- The modal backdrop and existing close behavior remain unchanged.

## Layout Integration

`App.vue` remains the source of truth for the sidebar's effective width. The implementation will expose that width through a shared layout value that remains available to the drawer even though Element Plus teleports drawer content under `body`. The drawer will use that value to set its left offset and calculate its width as the viewport width minus the sidebar width. The existing narrow-screen rule must resolve to 72px for the same shared value.

The implementation is limited to the application shell and the sales-order detail drawer styling/binding. No backend, store, permission, or logging changes are required for this layout-only adjustment.

## Validation Plan

- Open a sales-order detail at a desktop viewport with the sidebar expanded; confirm the panel begins at 200px and reaches the viewport's right edge.
- Collapse the sidebar while the detail is open; confirm the panel moves to 72px and continues to reach the viewport's right edge.
- Repeat with the sidebar expanded and collapsed at a viewport width of 760px or less; confirm the existing 72px mobile sidebar rule is respected and no horizontal page overflow is introduced.
- Confirm the drawer's content remains usable, its existing internal scrolling works, and existing close behavior is unchanged.
- Run `npm test` and `npm run build` in `quote-frontend`, then run `git diff --check`.
