# 09 Acceptance Checklist

Use this checklist after each UI refactor stage.

## Visual Consistency

- [ ] Page has a clear page header.
- [ ] Primary action is visually obvious.
- [ ] Filters and actions are separated.
- [ ] Tables use consistent header, row, and action styles.
- [ ] Dialogs use consistent title, body, footer, and button order.
- [ ] Tags/status indicators use semantic colors and text.
- [ ] No new ad-hoc colors are introduced in page CSS.

## Usability

- [ ] Loading state appears for async actions.
- [ ] Write actions and external-sync actions cannot be triggered repeatedly while pending.
- [ ] Error state is visible and recoverable.
- [ ] Empty state explains what to do next.
- [ ] Destructive actions require confirmation.
- [ ] Forms have visible labels.
- [ ] Buttons do not shift layout when loading.

## Accessibility

- [ ] Text contrast is readable on all surfaces.
- [ ] Focus rings remain visible.
- [ ] Icon-only actions have text, tooltip, or accessible label.
- [ ] Color is not the only status indicator.
- [ ] Dialogs can be dismissed appropriately unless action is mandatory.

## Responsiveness

- [ ] Page does not create horizontal scroll at common desktop widths.
- [ ] Toolbars wrap gracefully.
- [ ] Long tables scroll inside their surface when necessary.
- [ ] Dialog content remains usable on smaller screens.

## Engineering

- [ ] Business behavior is unchanged unless the stage explicitly changes it.
- [ ] Pinia store behavior is unchanged.
- [ ] API calls are unchanged unless explicitly planned.
- [ ] No new UI library is introduced.
- [ ] `npm test` passes in `quote-frontend`.
- [ ] `npm run build` passes in `quote-frontend`.
