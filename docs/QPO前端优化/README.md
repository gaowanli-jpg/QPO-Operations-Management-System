# QPO Frontend UI Redesign Guide

This folder is the source of truth for the completed `quote-frontend` QPO UI redesign and the ongoing stable maintenance baseline.

The goal is not to copy a template skin. The QPO UI is now a disciplined Element Plus workspace with consistent layout, spacing, color, tables, forms, dialogs, and page patterns.

## Stable Baseline

The QPO UI redesign is now frozen as a stable baseline. Future pages and features must extend the existing design system instead of inventing new local visual systems.

QPO is the user-facing product name. Existing technical prefixes such as `erp.env`, `ERP_*`, `erp_*`, `erp-page`, `.erp-*`, `--erp-*`, and backend `erp.*` remain intentionally compatible until a separate technical-prefix migration is approved.

Required shared classes for new or substantially edited pages:

- `erp-page`
- `erp-page-header`
- `erp-toolbar`
- `erp-surface`
- `erp-table-surface`
- `erp-table-actions`
- `erp-dialog-form`
- `erp-dialog-footer`

Do not add a separate page-local card/header/table style unless the shared class is first proven insufficient and the new pattern is promoted back into the shared token/class layer.

## Recommended Direction

Use a **clean manufacturing QPO workbench** style:

- Light gray app background, white work surfaces, dark navigation rail.
- Dense but readable tables for quotation, order, material, and process work.
- One stable blue primary color, plus semantic success/warning/danger colors.
- Clear page structure: page header, filters, primary actions, data surface, secondary details.
- Minimal decoration. No marketing-style hero sections, gradient blobs, or decorative cards.

## Folder Map

- `../../AGENTS.md` - root project memory for QPO workspace boundaries, QPO UI maintenance, permission/log cross-checks, and development guardrails.
- `01-current-ui-audit.md` - what is messy today and why.
- `02-open-source-reference.md` - open-source QPO/admin projects to borrow from.
- `03-design-direction.md` - chosen product design direction.
- `04-design-tokens.md` - colors, typography, spacing, radius, shadows, status colors.
- `05-layout-navigation.md` - shell, sidebar, header, page frame, responsive behavior.
- `06-component-guidelines.md` - tables, forms, cards, dialogs, buttons, tags, messages.
- `07-page-patterns.md` - page-level patterns for Market, Engineer, Pricing, Orders, Dictionary, Admin.
- `08-implementation-roadmap.md` - phased development plan for the UI cleanup.
- `09-acceptance-checklist.md` - quality gate before each visual refactor is accepted.
- `10-stage-0-baseline-audit.md` through `20-performance-code-splitting.md` - completed stage notes and verification history.
- `21-stable-maintenance-baseline.md` - final freeze rules for future QPO UI work.
- `22-action-loading-and-duplicate-guard.md` - loading, disabled state, duplicate-click protection, and async-task feedback rules.
- `23-project-wide-duplicate-click-upgrade-development-doc.md` - project-wide implementation plan for preventing accidental repeated clicks.
- `references/` - summarized notes from reference projects and docs.

## Non-Goals

- Do not replace Vue, Element Plus, Pinia, or the existing API/store architecture.
- Do not restart broad visual redesign now that the UI baseline is stable.
- Do not redesign business workflows as part of visual maintenance work.
- Do not rewrite large pages just to make them smaller unless a separate maintainability task explicitly requires it.
- Do not introduce a second UI kit.
- Do not add one-off card, header, toolbar, table, dialog, or button systems inside a page when a shared QPO/`erp-*` class already exists.

## Primary Files In Current QPO

- `quote-frontend/src/style.css` - current global Element Plus overrides and QPO tokens.
- `quote-frontend/src/App.vue` - sidebar, header, app shell, password dialog.
- `quote-frontend/src/components/MarketDashboard.vue` - market import center.
- `quote-frontend/src/components/GlobalSetup.vue` - engineering setup page.
- `quote-frontend/src/components/ItemPricing.vue` - quotation workbench, currently the densest page.
- `quote-frontend/src/components/OrderManage.vue` - order management page.
- `quote-frontend/src/components/MaterialProcessManage.vue` - dictionary management page.
