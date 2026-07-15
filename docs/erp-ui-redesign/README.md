# ERP Frontend UI Redesign Guide

This folder is the working guide for redesigning `quote-frontend` into a cleaner, more consistent manufacturing ERP interface.

The goal is not to copy a template skin. The goal is to turn the current ERP into a disciplined Element Plus workspace with consistent layout, spacing, color, tables, forms, dialogs, and page patterns.

## Recommended Direction

Use a **clean manufacturing ERP workbench** style:

- Light gray app background, white work surfaces, dark navigation rail.
- Dense but readable tables for quotation, order, material, and process work.
- One stable blue primary color, plus semantic success/warning/danger colors.
- Clear page structure: page header, filters, primary actions, data surface, secondary details.
- Minimal decoration. No marketing-style hero sections, gradient blobs, or decorative cards.

## Folder Map

- `01-current-ui-audit.md` - what is messy today and why.
- `02-open-source-reference.md` - open-source ERP/admin projects to borrow from.
- `03-design-direction.md` - chosen product design direction.
- `04-design-tokens.md` - colors, typography, spacing, radius, shadows, status colors.
- `05-layout-navigation.md` - shell, sidebar, header, page frame, responsive behavior.
- `06-component-guidelines.md` - tables, forms, cards, dialogs, buttons, tags, messages.
- `07-page-patterns.md` - page-level patterns for Market, Engineer, Pricing, Orders, Dictionary, Admin.
- `08-implementation-roadmap.md` - phased development plan for the UI cleanup.
- `09-acceptance-checklist.md` - quality gate before each visual refactor is accepted.
- `references/` - summarized notes from reference projects and docs.

## Non-Goals

- Do not replace Vue, Element Plus, Pinia, or the existing API/store architecture.
- Do not redesign business workflows before visual structure is stabilized.
- Do not rewrite large pages just to make them smaller unless the visual refactor needs it.
- Do not introduce a second UI kit.

## Primary Files In Current ERP

- `quote-frontend/src/style.css` - current global Element Plus overrides and ERP tokens.
- `quote-frontend/src/App.vue` - sidebar, header, app shell, password dialog.
- `quote-frontend/src/components/MarketDashboard.vue` - market import center.
- `quote-frontend/src/components/GlobalSetup.vue` - engineering setup page.
- `quote-frontend/src/components/ItemPricing.vue` - quotation workbench, currently the densest page.
- `quote-frontend/src/components/OrderManage.vue` - order management page.
- `quote-frontend/src/components/MaterialProcessManage.vue` - dictionary management page.

