# Project Agent Memory

This repository contains two related systems:

- ERP quotation system: `quote-frontend` plus the ERP backend and compose files.
- APS system: `aps-frontend` plus the APS backend and compose files.

Use this file as long-term execution memory for future Codex work. Do not replace the project-specific docs below with guesses.

## ERP UI Redesign Source Of Truth

Primary guidance folder:

- `docs/erp-ui-redesign/`

Files and meaning:

- `README.md`: entry point for the ERP visual redesign direction.
- `01-current-ui-audit.md`: original high-level audit of why the current ERP UI feels inconsistent.
- `02-open-source-reference.md`: external UI references and what to borrow conceptually.
- `03-design-direction.md`: chosen direction: clean, professional, manufacturing ERP workspace; data-dense, calm, low decoration.
- `04-design-tokens.md`: target token system for colors, typography, spacing, radius, and Element Plus mapping.
- `05-layout-navigation.md`: target app shell, sidebar, header, page frame, and responsive behavior.
- `06-component-guidelines.md`: shared rules for page headers, toolbars, tables, forms, cards, buttons, dialogs, tags, loading/empty/error states.
- `07-page-patterns.md`: page-level patterns and migration guidance.
- `08-implementation-roadmap.md`: staged execution plan. Follow this order unless the user explicitly changes it.
- `09-acceptance-checklist.md`: checklist to apply after each UI stage.
- `10-stage-0-baseline-audit.md`: Stage 0 baseline verification, current style inventory, risks, and Stage 1 recommendation.
- `screenshots/README.md`: expected location and names for before/after visual screenshots.

## Current ERP UI Redesign Status

- Stage 0 is complete and committed in `25e9fd3` (`docs: add ERP UI stage 0 audit`).
- Stage 1 is complete: `quote-frontend/src/style.css` now contains expanded ERP tokens, Element Plus base mappings, and shared classes for later page migrations.
- Stage 2 is complete: `quote-frontend/src/App.vue` now has grouped short sidebar labels, route-aware header context, clearer active navigation, and polished user controls.
- Stage 3 is complete: `MarketDashboard.vue`, `OrderManage.vue`, and `MaterialProcessManage.vue` now use shared `erp-page`, `erp-toolbar`, `erp-surface`, and `erp-table-surface` layout patterns without changing business behavior.
- Stage 4 is complete: `OrderManage.vue` and `MaterialProcessManage.vue` now use shared dialog form, dialog section, table action, upload surface, footer, and danger action classes without changing business behavior.
- Stage 5 is complete: `GlobalSetup.vue` now uses the shared ERP page shell, prominent order context, clearer drawing review, grouped global configuration sections, and an obvious save/continue panel without changing business behavior.
- Stage 6 is complete: `ItemPricing.vue` now uses the shared ERP page shell, workbench context metrics, normalized toolbar/table actions, and clearer pricing dialog sections while preserving calculation logic, API calls, Pinia/store behavior, and submission flow.
- Stage 7 is complete: `HistoryOrderManage.vue`, `MarketRelease.vue`, `QuoteProcess.vue`, `QuoteTemplateManage.vue`, and `UserManage.vue` now use shared ERP page/table/action/dialog patterns where appropriate, with obsolete local card/header/table shells removed.
- Post Stage 7 workflow polish is complete for `QuoteProcess.vue` and `MarketRelease.vue`: dense market workflow pages now use shared `workflow-*` workbench classes and quieter neutral secondary actions while preserving business behavior.
- The staged ERP UI redesign is structurally complete. Next recommended follow-up is a browser walkthrough plus optional updated screenshots for `QuoteProcess.vue` and `MarketRelease.vue` in `docs/erp-ui-redesign/screenshots/`; defer bundle splitting, API/Pinia changes, pricing changes, and component decomposition to separate tasks.
- Stage 0 screenshots are now captured in `docs/erp-ui-redesign/screenshots/` using local Microsoft Edge plus a temporary Playwright package from npm cache. Do not add screenshot tooling to project dependencies unless the user explicitly asks.

## Work Cadence For ERP UI Stages

For every stage:

1. Start from the current repo state, not memory alone.
2. Read `AGENTS.md`, `docs/erp-ui-redesign/08-implementation-roadmap.md`, the latest completed stage document, and the files named by the current stage.
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
- Do not mix ERP and APS changes unless the user asks for both.
- Do not introduce a new UI library for the ERP redesign.
- Do not remove existing page-local CSS until a shared replacement is proven.
- Do not rewrite dense pages like `ItemPricing.vue` before the roadmap reaches that stage.
- Keep commits stage-scoped and easy to review.
