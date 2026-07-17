# Project Agent Memory

This repository contains two related systems:

- QPO quotation system: `quote-frontend` plus the QPO backend and compose files.
- APS system: `aps-frontend` plus the APS backend and compose files.

Use this file as long-term execution memory for future Codex work. Do not replace the project-specific docs below with guesses.

## QPO Brand Rename Compatibility

QPO is the product-facing name for the quotation system. This rename is not a full technical-prefix migration.

Keep these compatibility identifiers unless the user explicitly approves a separate migration:

- `erp.env` and `erp.env.example`
- `ERP_*` environment variables
- localStorage keys such as `erp_token`, `erp_user`, and `erp_global_config*`
- CSS classes and tokens such as `erp-page`, `erp-toolbar`, `.erp-*`, and `--erp-*`
- backend configuration prefixes such as `erp.*`
- backend entities, database tables, and migration names such as `ErpUser` or `erp_user`

Docs and UI copy should say QPO for the product name, but technical references above should remain unchanged for compatibility.

## QPO UI Redesign Source Of Truth

Primary guidance folder:

- `docs/QPO前端优化/`

Files and meaning:

- `README.md`: entry point for the QPO visual redesign direction.
- `01-current-ui-audit.md`: original high-level audit of why the current QPO UI feels inconsistent.
- `02-open-source-reference.md`: external UI references and what to borrow conceptually.
- `03-design-direction.md`: chosen direction: clean, professional, manufacturing QPO workspace; data-dense, calm, low decoration.
- `04-design-tokens.md`: target token system for colors, typography, spacing, radius, and Element Plus mapping.
- `05-layout-navigation.md`: target app shell, sidebar, header, page frame, and responsive behavior.
- `06-component-guidelines.md`: shared rules for page headers, toolbars, tables, forms, cards, buttons, dialogs, tags, loading/empty/error states.
- `07-page-patterns.md`: page-level patterns and migration guidance.
- `08-implementation-roadmap.md`: staged execution plan. Follow this order unless the user explicitly changes it.
- `09-acceptance-checklist.md`: checklist to apply after each UI stage.
- `10-stage-0-baseline-audit.md`: Stage 0 baseline verification, current style inventory, risks, and Stage 1 recommendation.
- `20-performance-code-splitting.md`: separate performance pass that lazy-loads business routes after the UI baseline and documents why fragile manual vendor chunking is disabled.
- `21-stable-maintenance-baseline.md`: final QPO UI baseline freeze and maintenance rules.
- `screenshots/README.md`: expected location and names for before/after visual screenshots.

## Current QPO UI Redesign Status

- Stage 0 is complete and committed in `25e9fd3` (`docs: add QPO UI stage 0 audit`).
- Stage 1 is complete: `quote-frontend/src/style.css` now contains expanded QPO tokens, Element Plus base mappings, and shared classes for later page migrations.
- Stage 2 is complete: `quote-frontend/src/App.vue` now has grouped short sidebar labels, route-aware header context, clearer active navigation, and polished user controls.
- Stage 3 is complete: `MarketDashboard.vue`, `OrderManage.vue`, and `MaterialProcessManage.vue` now use shared `erp-page`, `erp-toolbar`, `erp-surface`, and `erp-table-surface` layout patterns without changing business behavior.
- Stage 4 is complete: `OrderManage.vue` and `MaterialProcessManage.vue` now use shared dialog form, dialog section, table action, upload surface, footer, and danger action classes without changing business behavior.
- Stage 5 is complete: `GlobalSetup.vue` now uses the shared QPO page shell, prominent order context, clearer drawing review, grouped global configuration sections, and an obvious save/continue panel without changing business behavior.
- Stage 6 is complete: `ItemPricing.vue` now uses the shared QPO page shell, workbench context metrics, normalized toolbar/table actions, and clearer pricing dialog sections while preserving calculation logic, API calls, Pinia/store behavior, and submission flow.
- Stage 7 is complete: `HistoryOrderManage.vue`, `MarketRelease.vue`, `QuoteProcess.vue`, `QuoteTemplateManage.vue`, and `UserManage.vue` now use shared QPO page/table/action/dialog patterns where appropriate, with obsolete local card/header/table shells removed.
- Post Stage 7 workflow polish is complete: `QuoteProcess.vue` uses shared workflow workbench classes with a compact one-row manual quote-date control; `MarketRelease.vue` keeps the tighter Stage 7 layout with quieter secondary action buttons and a compact one-row manual internal target-date control. Business behavior is preserved.
- The staged QPO UI redesign is structurally complete.
- Final browser walkthrough is complete and documented in `docs/QPO前端优化/19-final-browser-walkthrough.md`. Final screenshots are captured in `docs/QPO前端优化/screenshots/final-*.png` against the Vite hot-start frontend at `http://localhost:5173` with Docker QPO backend data.
- Final punch-list sprint is complete and documented in `docs/QPO前端优化/19-final-walkthrough-and-punch-list.md`. It only tightened the market import fallback folder button and dense table/action button spacing.
- Performance code-splitting step is complete and documented in `docs/QPO前端优化/20-performance-code-splitting.md`: QPO business routes are lazy-loaded without changing page logic. Manual Vite/Rolldown third-party vendor chunk groups are intentionally disabled because Docker/browser verification showed they can break runtime module bindings and leave the app blank before Vue mounts.
- QPO UI redesign is now in stable maintenance, documented in `docs/QPO前端优化/21-stable-maintenance-baseline.md`. New pages must use the existing QPO tokens and shared classes such as `erp-page`, `erp-page-header`, `erp-toolbar`, `erp-surface`, `erp-table-surface`, `erp-dialog-form`, and `erp-table-actions`; do not invent a separate card/header/table visual system inside individual pages.
- Next recommended follow-up is to treat QPO UI as frozen unless a new feature requires page-scoped work. Defer deeper startup profiling, Element Plus on-demand registration, API/Pinia changes, pricing changes, and component decomposition to separate tasks.
- Stage 0 screenshots are now captured in `docs/QPO前端优化/screenshots/` using local Microsoft Edge plus a temporary Playwright package from npm cache. Do not add screenshot tooling to project dependencies unless the user explicitly asks.

## Work Cadence For QPO UI Stages

For every stage:

1. Start from the current repo state, not memory alone.
2. Read `docs/QPO前端优化/AGENTS.md`, `docs/QPO前端优化/08-implementation-roadmap.md`, the latest completed stage document, and the files named by the current stage.
3. Make the smallest stage-scoped change that advances the roadmap.
4. Preserve business behavior unless the current stage explicitly says otherwise.
5. Prefer shared tokens/classes before page-by-page visual rewrites.
6. Run fresh verification before claiming completion:
   - `npm test` in `quote-frontend`
   - `npm run build` in `quote-frontend`
7. Update or add a stage note when it helps future continuity.
8. Commit the completed stage.
9. In the final response, summarize what changed, give verification evidence, mention the commit, and explain the next stage based on the roadmap plus current project state.

## Next-Step Guidance Rule

After each completed step, recommend the next step from actual evidence:

- Read the roadmap stage that follows.
- Check the current files that stage will touch.
- Mention what is now safe to do because the previous stage is complete.
- Mention what should still be deferred.

Avoid generic advice. The next-step recommendation must cite the current stage status and relevant project files.

## Guardrails

- Do not invent project state. Verify with `git status`, `rg`, and direct file reads.
- Do not mix QPO and APS changes unless the user asks for both.
- Do not introduce a new UI library for the QPO redesign.
- Do not remove existing page-local CSS until a shared replacement is proven.
- Do not rewrite dense pages like `ItemPricing.vue` before the roadmap reaches that stage.
- Keep commits stage-scoped and easy to review.
