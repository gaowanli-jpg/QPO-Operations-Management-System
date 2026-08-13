# MES Master Data Sync Schedule Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add configurable timed sync policies for MES material and process master data, matching the existing U9 customer timed sync model.

**Architecture:** Store one runtime policy per MES sync type in a new database table. Backend controllers expose GET/PUT policy endpoints next to the existing manual sync endpoints, and `MesMasterDataSyncService` runs a lightweight scheduled checker that invokes the existing sync methods without changing sync payload semantics. Frontend adds a strategy dialog beside the existing manual MES sync buttons.

**Tech Stack:** Spring Boot, MyBatis Plus, Flyway, JUnit/Mockito, Vue 3, Element Plus, Vitest.

## Global Constraints

- QPO only; do not introduce APS artifacts.
- Reuse `engineer:dict:material-manage` and `engineer:dict:process-manage`; do not add assignable `common:*` permissions.
- Scheduled sync must reuse the existing manual sync service methods and must not overwrite QPO material prices.
- Scheduled checks must not write noisy operation logs; saving policy writes one safe audit record.

---

### Task 1: Backend Schedule Policy Foundation

**Files:**
- Create: `quote-backend/src/main/resources/db/migration/V9__mes_master_data_sync_schedule_config.sql`
- Create: `quote-backend/src/main/java/com/machining/quote/entity/MesMasterDataSyncScheduleConfig.java`
- Create: `quote-backend/src/main/java/com/machining/quote/mapper/MesMasterDataSyncScheduleConfigMapper.java`
- Create: `quote-backend/src/main/java/com/machining/quote/dto/MesMasterDataSyncScheduleConfigDTO.java`
- Create: `quote-backend/src/main/java/com/machining/quote/dto/MesMasterDataSyncScheduleConfigReq.java`
- Create: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncScheduleConfigService.java`
- Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncScheduleConfigTest.java`

**Interfaces:**
- Produces: `getSetting(String syncType)`, `updateSetting(String syncType, MesMasterDataSyncScheduleConfigReq req)`, `shouldRun(String syncType, LocalDateTime schedulerStartedAt)`, `updateScheduledResult(String syncType, MesMasterDataSyncService.SyncResult result)`.

- [x] Write failing tests for fallback defaults, validation, result updates, migration contents, and safe audit summary.
- [x] Run `./mvnw.cmd test "-Dtest=MesMasterDataSyncScheduleConfigTest"` and confirm failure before implementation.
- [x] Implement migration/entity/mapper/DTO/service by mirroring `U9CustomerSyncScheduleConfigService` with sync-type-specific rows.
- [x] Re-run the same test and confirm pass.

### Task 2: Backend Controllers And Scheduler

**Files:**
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/DictController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/ProcessMasterController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/AuditService.java`
- Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncControllerTest.java`
- Test: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncScheduleConfigTest.java`

**Interfaces:**
- Produces: `GET/PUT /api/dict/materials/sync-schedule` and `GET/PUT /api/dict/process-categories/sync-schedule`.

- [x] Add failing tests for permissions, endpoint paths, current-user schedule updates, audit labels, and scheduled guard behavior.
- [x] Run targeted backend tests and confirm failure.
- [x] Add controller methods and scheduled checker with `AtomicBoolean` guards per sync type.
- [x] Re-run targeted backend tests and confirm pass.

### Task 3: Frontend Strategy Dialog

**Files:**
- Modify: `quote-frontend/src/services/dictApi.js`
- Modify: `quote-frontend/src/components/MaterialProcessManage.vue`
- Test: `quote-frontend/src/mesMasterDataSyncFrontend.test.js`

**Interfaces:**
- Produces: `getMesMaterialSyncSchedule`, `saveMesMaterialSyncSchedule`, `getMesProcessSyncSchedule`, `saveMesProcessSyncSchedule`.

- [x] Add failing Vitest assertions for API helpers, permission-gated strategy buttons, modal fields, status display, and loading guards.
- [x] Run `npm test -- --run src/mesMasterDataSyncFrontend.test.js` and confirm failure.
- [x] Implement helper functions and a reusable schedule dialog in `MaterialProcessManage.vue`.
- [x] Re-run frontend targeted tests and confirm pass.

### Task 4: Docs, Regression, Commit

**Files:**
- Modify: `docs/superpowers/specs/2026-08-13-mes-master-data-sync-design.md`
- Modify: `docs/QPO权限用户体系/02-权限点清单与现有角色映射.md`
- Modify: `docs/QPO日志模块/03-日志运维说明.md`

- [x] Document timed sync policy endpoints, permissions, audit action, defaults, and non-overwrite price rule.
- [x] Run backend targeted tests and frontend targeted tests.
- [ ] Check git status, stage only related files, and commit.
