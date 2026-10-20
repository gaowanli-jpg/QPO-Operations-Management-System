# Data Center DB Readonly Direct Connect Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let the data center connect directly to QPO PostgreSQL with a dedicated read-only role and matching deployment configuration.

**Architecture:** Open the host Postgres binding for the QPO deployment, carry the dedicated read-only password through `erp.env`, inject that password into a Flyway migration placeholder, and create the database role plus read-only grants in a new migration. Keep the existing application database connection unchanged.

**Tech Stack:** Docker Compose, PostgreSQL 15, Flyway, Spring Boot configuration, PowerShell, Markdown docs.

**Spec:** `docs/QPO系统配置/06-数据中心数据库只读直连开发文档.md`

## Global Constraints

- QPO only; do not introduce APS artifacts.
- Do not change existing application runtime database credentials.
- Use the next unused Flyway migration version only; do not edit executed migrations.
- Keep the data center access path read-only.
- Store the real read-only password in `erp.env`; leave the example file blank for secrets.

---

### Task 1: Expose PostgreSQL And Add Secret Wiring

**Files:**
- Modify: `erp.env`
- Modify: `erp.env.example`
- Modify: `quote-backend/src/main/resources/application.yaml`
- Test: `quote-backend/src/test/java/com/machining/quote/DatacenterReadonlyDirectConnectConfigTest.java`

**Interfaces:**
- Produces: `POSTGRES_BIND=0.0.0.0`, `POSTGRES_DATACENTER_RO_PASSWORD`, and a Flyway placeholder mapped from the env var.

- [ ] Write a focused test that asserts the bind value and placeholder wiring exist in the checked-in config files.
- [ ] Run the focused test and confirm it fails before the edits.
- [ ] Update the env files and Spring Flyway placeholder mapping.
- [ ] Re-run the focused test and confirm pass.

### Task 2: Create The Readonly Role Migration

**Files:**
- Create: `quote-backend/src/main/resources/db/migration/V15__data_center_readonly_role.sql`
- Test: `quote-backend/src/test/java/com/machining/quote/DatacenterReadonlyRoleMigrationTest.java`

**Interfaces:**
- Produces: a migration that creates `qpo_datacenter_ro`, grants database/schema/table/sequence read access, and uses the injected password placeholder.

- [ ] Write a migration content test that asserts the role name, grants, and password placeholder are present.
- [ ] Run the test and confirm it fails before the migration exists.
- [ ] Add the migration with guarded role creation / password assignment and read-only grants.
- [ ] Re-run the test and confirm pass.

### Task 3: Refresh The Direct-Connect Doc

**Files:**
- Modify: `docs/QPO系统配置/06-数据中心数据库只读直连开发文档.md`

**Interfaces:**
- Produces: the final operator-facing instructions for the new env vars, connection values, and read-only role setup.

- [ ] Update the doc to mention `POSTGRES_BIND=0.0.0.0`, `POSTGRES_DATACENTER_RO_PASSWORD`, and the migration-based role creation.
- [ ] Re-read the doc and confirm the deployment steps match the implemented files.

### Task 4: Verify And Close Out

**Files:**
- None

**Interfaces:**
- Produces: verified config, migration, and documentation changes.

- [ ] Run targeted tests for the new config and migration checks.
- [ ] Check git status and confirm only the intended files changed.
