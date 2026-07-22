# Project Agent Memory

This repository is the QPO quotation system workspace:

- QPO quotation system: `quote-frontend`, `quote-backend`, `docker-compose.yml`, and `erp.env`.
- APS scheduling system has been moved to `D:\APS-Operations-Management-System`.

Use this file as the root long-term execution memory for future Codex work. Always verify current files with `git status`, `rg`, and direct reads instead of relying on memory alone.

## System Boundaries

- Do not mix QPO and APS changes unless the user explicitly asks for both.
- Do not recreate APS source folders or APS compose/env files inside this QPO workspace.
- For QPO product copy, use `QPO`.
- Keep QPO compatibility identifiers unless the user explicitly approves a separate migration: `erp.env`, `ERP_*`, `erp_token`, `erp_user`, `erp_global_config*`, `erp-page`, `erp-toolbar`, `.erp-*`, `--erp-*`, backend `erp.*`, `ErpUser`, and `erp_user`.

## QPO Source-Of-Truth Docs

- UI baseline and frontend visual direction: `docs/QPO前端优化/`
- Permission system: `docs/QPO权限用户体系/`
- Operation and file logging: `docs/QPO日志模块/`

Before changing QPO UI, permissions, users, roles, logs, audit, deployment, or major module behavior, read the relevant docs above first.

## QPO Module Change Cross-Checks

For any QPO module or business-capability change, check permissions and logs at the same time. The goal is that a feature is visible, callable, data-scoped, and auditable consistently across frontend, backend, and operations.

Permission checks are required when a change adds or changes:

- A left-sidebar module or protected page: add/update an independent `MENU` permission, route `meta.permission`, App menu metadata, backend permission catalog, tests, and docs.
- A key operation such as import, export, delete, reset, submit, approve, batch update, save, refresh, upload, or password/admin action: add/reuse the correct `BUTTON` or `API` permission and enforce it with backend `@RequirePermission`.
- A backend endpoint: confirm backend permission guards remain the final security boundary even if the frontend hides the action.
- Customer selection, order selection, file preview, task viewing, or other reusable abilities: prefer module-owned permissions like `market:release:order-select` or `engineer:setup:file-preview`; do not add new assignable `common:*` permissions.
- Queries over orders, customers, logs, or other scoped data: confirm data permissions apply and empty scope returns an empty list instead of leaking data.

Logging checks are required when a change adds or changes:

- Security/admin actions: login/logout, user changes, role authorization, data-scope changes, password reset, and retention policy changes.
- Key business writes: RFQ import, drawing upload, order delete, quote export, quote-date updates, pricing save/refresh/complete, release generation/reset, template upload, and dictionary maintenance.
- Async jobs: log submitted, started, completed, and failed; failures must include `taskId` and exception stack in backend error logs.
- Configuration or governance changes: write an operation-log record and a concise backend file-log summary.

Do not add noisy logs for normal high-frequency reads, health checks, list polling, or purely auxiliary helpers. Pure UI styling/layout changes usually do not need new permissions or logs, but they must preserve existing permission checks and audit behavior.

## Development Cadence

For implementation tasks:

1. Start from current repo state.
2. Read the relevant docs and local code before editing.
3. Keep changes scoped to the user request.
4. Preserve business behavior unless the user asks to change it.
5. Prefer existing project patterns, shared QPO UI classes, Pinia stores, axios services, permission helpers, and logging conventions.
6. Run fresh verification before claiming completion.
7. Commit when the user asks to commit, or when the current phase explicitly includes a commit step.

## Guardrails

- Do not invent project state.
- Do not introduce a new UI library unless explicitly requested.
- Do not bypass backend permission enforcement with frontend-only checks.
- Do not add new assignable `common:*` permissions.
- Do not add noisy file logs for routine reads.
- Do not remove compatibility identifiers during product rename or cleanup work unless separately approved.
