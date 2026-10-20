# MES Master Data Async Sync Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Convert QPO MES material and process master-data manual sync into asynchronous submitted jobs, add status polling, and extend schedule settings with daily fixed-time mode.

**Architecture:** Keep `mes_master_data_sync_run` as the sole run/task record and keep existing POST endpoint paths. Controllers submit a `RUNNING` run and return immediately; background workers reuse existing MES fetch, normalize, persist, disable, delete, audit, and schedule-result logic. Schedule config supports `FIXED_DELAY` and `DAILY_TIME`, with frontend polling by `syncRunId`.

**Tech Stack:** Java 17, Spring Boot 3.2.5, MyBatis-Plus, PostgreSQL/Flyway, Vue 3, Element Plus, Vitest, JUnit 5, Mockito, AssertJ.

## Global Constraints

- QPO only; do not mix APS changes.
- Do not modify executed baseline migrations, especially `V1__qpo_baseline_schema.sql` and `V2__qpo_baseline_seed_data.sql`.
- New schema changes go into next unused migration version, currently `V25__...`.
- Keep manual POST paths: `/api/dict/materials/sync-mes` and `/api/dict/process-categories/sync-mes`.
- Use `mes_master_data_sync_run.id` as `syncRunId`; do not create an extra `system_task` row.
- Keep `deletedCount` separate from `disabledCount`.
- Preserve MES deletion semantics: returned disabled status disables; missing MES-source records after a successful full MES response are hard-deleted; `LOCAL` records are untouched; process category delete removes child `dict_process_detail` rows first.
- Keep backend permission guards as the final boundary.
- Do not log full MES request/response bodies, token, Authorization, Cookie, or client secret.
- Use TDD: write and run failing tests before production code for behavior changes.

---

### Task 1: Backend Async Submit and Run Query Contract

**Files:**
- Modify: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncControllerTest.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/MesMaterialMasterSyncServiceTest.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/MesProcessTypeSyncServiceTest.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/DictController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/ProcessMasterController.java`

**Interfaces:**
- Produces: `MesMasterDataSyncService.SyncSubmitResult`, with fields `success`, `submitted`, `alreadyRunning`, `syncRunId`, `syncType`, `status`, `message`.
- Produces: `submitMaterialsManual(Long createdBy)` and `submitProcessTypesManual(Long createdBy)`, both immediate-return methods.
- Produces: `getLatestRun(String syncType)` and `getRun(String syncType, Long syncRunId)`.
- Consumes: existing `syncMaterialsManual` / `syncProcessTypesManual` internals, converted into worker helpers.

- [ ] **Step 1: Write failing controller tests**

Add assertions that POST endpoints return submitted summaries and call `submit*Manual`, not blocking `sync*Manual`:

```java
when(syncService.submitProcessTypesManual(88L))
        .thenReturn(new MesMasterDataSyncService.SyncSubmitResult(true, true, false, 9001L, "MES_PROCESS_TYPE", "RUNNING", "MES工艺主数据同步已提交"));
Map<String, Object> response = controller.syncMesProcessTypes();
assertThat(response)
        .containsEntry("success", true)
        .containsEntry("submitted", true)
        .containsEntry("alreadyRunning", false)
        .containsEntry("syncRunId", 9001L)
        .containsEntry("syncType", "MES_PROCESS_TYPE")
        .containsEntry("status", "RUNNING");
verify(syncService).submitProcessTypesManual(88L);
verify(syncService, never()).syncProcessTypesManual(88L);
```

Repeat for materials and add GET latest / by id endpoint tests using `getLatestRun` and `getRun`.

- [ ] **Step 2: Run backend controller test and verify RED**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataSyncControllerTest`

Expected: fail because `SyncSubmitResult`, `submitMaterialsManual`, `submitProcessTypesManual`, and status query controller methods do not exist or controller still calls blocking methods.

- [ ] **Step 3: Write failing service tests**

Add service-level tests that use a direct executor or mocked `ThreadPoolTaskExecutor` to prove submit returns before worker completion, duplicate active run is reused, run query filters by type, and executor rejection marks the run failed.

```java
MesMasterDataSyncRun active = new MesMasterDataSyncRun();
active.setId(910L);
active.setSyncType("MES_MATERIAL");
active.setStatus("RUNNING");
when(syncRunMapper.selectOne(any(QueryWrapper.class))).thenReturn(active);

MesMasterDataSyncService.SyncSubmitResult result = service.submitMaterialsManual(7L);

assertThat(result.alreadyRunning()).isTrue();
assertThat(result.submitted()).isFalse();
assertThat(result.syncRunId()).isEqualTo(910L);
verify(taskExecutor, never()).execute(any(Runnable.class));
```

- [ ] **Step 4: Run service tests and verify RED**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMaterialMasterSyncServiceTest,MesProcessTypeSyncServiceTest`

Expected: fail because async submit APIs and executor handling do not exist.

- [ ] **Step 5: Implement async submit and query APIs**

Inject `@Qualifier("systemTaskExecutor") ThreadPoolTaskExecutor taskExecutor` into `MesMasterDataSyncService`. Add submit methods that create/reuse `RUNNING` records, call `taskExecutor.execute(...)`, and return `SyncSubmitResult`. Move existing blocking public methods into worker methods that accept an existing run record and update the run in the existing success/failure paths.

- [ ] **Step 6: Update controllers**

Change POST endpoints to call `submitMaterialsManual` / `submitProcessTypesManual`. Add GET latest / by id methods under material and process routes, with the same manage permissions as submit endpoints.

- [ ] **Step 7: Verify Task 1 GREEN**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataSyncControllerTest,MesMaterialMasterSyncServiceTest,MesProcessTypeSyncServiceTest`

Expected: all selected tests pass.

- [ ] **Step 8: Commit Task 1**

Run: `git add quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncControllerTest.java quote-backend/src/test/java/com/machining/quote/MesMaterialMasterSyncServiceTest.java quote-backend/src/test/java/com/machining/quote/MesProcessTypeSyncServiceTest.java quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java quote-backend/src/main/java/com/machining/quote/controller/DictController.java quote-backend/src/main/java/com/machining/quote/controller/ProcessMasterController.java && git commit -m "feat: submit MES master sync asynchronously"`

---

### Task 2: Schedule Mode and Active Run Migration

**Files:**
- Create: `quote-backend/src/main/resources/db/migration/V25__mes_master_data_async_schedule.sql`
- Modify: `quote-backend/src/test/java/com/machining/quote/MesMasterDataMigrationTest.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncScheduleConfigTest.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/entity/MesMasterDataSyncScheduleConfig.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/dto/MesMasterDataSyncScheduleConfigDTO.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/dto/MesMasterDataSyncScheduleConfigReq.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncScheduleConfigService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java`

**Interfaces:**
- Produces: `scheduleMode` values `FIXED_DELAY` and `DAILY_TIME`.
- Produces: `dailyRunTime` as `java.time.LocalTime` in entity/DTO/request.
- Consumes: `MesMasterDataSyncService.submitScheduled(String syncType)` from Task 1.

- [ ] **Step 1: Write failing migration tests**

Assert `V25__mes_master_data_async_schedule.sql` exists and contains:

```text
ALTER TABLE mes_master_data_sync_schedule_config ADD COLUMN IF NOT EXISTS schedule_mode
ALTER TABLE mes_master_data_sync_schedule_config ADD COLUMN IF NOT EXISTS daily_run_time
CREATE UNIQUE INDEX IF NOT EXISTS uk_mes_master_data_sync_run_active_type
WHERE status = 'RUNNING'
```

- [ ] **Step 2: Run migration test and verify RED**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataMigrationTest`

Expected: fail because `V25__mes_master_data_async_schedule.sql` does not exist.

- [ ] **Step 3: Write failing schedule service tests**

Add tests for `DAILY_TIME=02:00:00`: before time does not run; at/after time with no run today runs; already ran today does not run; server starts after time and no run today runs; disabled never runs. Keep existing fixed-delay tests green.

- [ ] **Step 4: Run schedule tests and verify RED**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataSyncScheduleConfigTest`

Expected: fail because `scheduleMode` and `dailyRunTime` are not implemented.

- [ ] **Step 5: Add migration and DTO/entity fields**

Create migration with append-only schema changes. Add `String scheduleMode` and `LocalTime dailyRunTime` fields to entity, DTO, and request.

- [ ] **Step 6: Implement schedule normalization and shouldRun mode branching**

Normalize missing mode to `FIXED_DELAY`. Validate mode. Normalize missing `dailyRunTime` in daily mode to `02:00:00`. Keep fixed-delay validation unchanged. Include `scheduleMode` and `dailyRunTime` in audit summary.

- [ ] **Step 7: Update scheduled execution to submit async worker**

Change scheduled patrol so it calls `submitScheduled(syncType)` instead of blocking `syncMaterialsManual(null)` / `syncProcessTypesManual(null)`. Update schedule result when async worker finishes, not when submit returns.

- [ ] **Step 8: Verify Task 2 GREEN**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataMigrationTest,MesMasterDataSyncScheduleConfigTest,MesMasterDataSyncControllerTest,MesMaterialMasterSyncServiceTest,MesProcessTypeSyncServiceTest`

Expected: all selected tests pass.

- [ ] **Step 9: Commit Task 2**

Run: `git add quote-backend/src/main/resources/db/migration/V25__mes_master_data_async_schedule.sql quote-backend/src/test/java/com/machining/quote/MesMasterDataMigrationTest.java quote-backend/src/test/java/com/machining/quote/MesMasterDataSyncScheduleConfigTest.java quote-backend/src/main/java/com/machining/quote/entity/MesMasterDataSyncScheduleConfig.java quote-backend/src/main/java/com/machining/quote/dto/MesMasterDataSyncScheduleConfigDTO.java quote-backend/src/main/java/com/machining/quote/dto/MesMasterDataSyncScheduleConfigReq.java quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncScheduleConfigService.java quote-backend/src/main/java/com/machining/quote/service/mes/MesMasterDataSyncService.java && git commit -m "feat: add daily MES sync schedule mode"`

---

### Task 3: Frontend Submission Polling and Schedule UI

**Files:**
- Modify: `quote-frontend/src/mes-master-data-sync.workflow.test.js`
- Modify: `quote-frontend/src/adminDuplicateClickGuard.test.js`
- Modify: `quote-frontend/src/services/dictApi.js`
- Modify: `quote-frontend/src/components/MaterialProcessManage.vue`

**Interfaces:**
- Consumes: backend submit response with `submitted`, `alreadyRunning`, `syncRunId`, `status`.
- Consumes: backend GET latest / by id run endpoints.
- Produces: `getMesMaterialSyncRun`, `getLatestMesMaterialSyncRun`, `getMesProcessSyncRun`, `getLatestMesProcessSyncRun` in `dictApi.js`.

- [ ] **Step 1: Write failing frontend characterization tests**

Extend `mes-master-data-sync.workflow.test.js` to assert new API methods, status polling helpers, submitted message handling, `deletedCount` success copy, latest-running resume, schedule mode selector, and `dailyRunTime` payload.

- [ ] **Step 2: Run frontend tests and verify RED**

Run: `cd quote-frontend; npm test -- mes-master-data-sync.workflow.test.js adminDuplicateClickGuard.test.js`

Expected: fail because polling APIs and UI strings do not exist.

- [ ] **Step 3: Add dict API methods**

Add GET helpers for material and process run status. Keep existing POST paths unchanged.

- [ ] **Step 4: Add component polling state**

Track active run IDs per sync type, poll every few seconds while status is `RUNNING`, stop on `SUCCESS`/`FAILED`, reload list only on `SUCCESS`, and restore polling from latest run on page load or tab activation.

- [ ] **Step 5: Add schedule mode UI and payload mapping**

Add mode selector for `FIXED_DELAY` / `DAILY_TIME`, show interval inputs only for fixed delay, show time picker for daily mode, and map `scheduleMode` / `dailyRunTime` in `saveMesScheduleSetting` and `applyMesScheduleSetting`.

- [ ] **Step 6: Verify Task 3 GREEN**

Run: `cd quote-frontend; npm test -- mes-master-data-sync.workflow.test.js adminDuplicateClickGuard.test.js`

Expected: selected frontend tests pass.

- [ ] **Step 7: Commit Task 3**

Run: `git add quote-frontend/src/mes-master-data-sync.workflow.test.js quote-frontend/src/adminDuplicateClickGuard.test.js quote-frontend/src/services/dictApi.js quote-frontend/src/components/MaterialProcessManage.vue && git commit -m "feat: poll MES master sync runs"`

---

### Task 4: Documentation, Characterization, and Final Verification

**Files:**
- Modify: `quote-backend/src/test/java/com/machining/quote/MesMasterDataDocumentationCharacterizationTest.java`
- Modify: `docs/QPO-MES-WMS对接/10-MES工艺材料主数据同步说明.md`
- Modify: `docs/QPO日志模块/03-日志运维说明.md`
- Modify: `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md`

**Interfaces:**
- Consumes: final backend/frontend contract from Tasks 1-3.
- Produces: docs that explain async submit, `syncRunId` polling, `FIXED_DELAY`, `DAILY_TIME`, `deletedCount`, and safe logs.

- [ ] **Step 1: Write failing documentation characterization assertions**

Assert docs contain `异步提交`, `syncRunId`, `状态查询`, `submitted`, `started`, `completed`, `failed`, `FIXED_DELAY`, `DAILY_TIME`, `Asia/Shanghai`, `deletedCount`, and `disabledCount`.

- [ ] **Step 2: Run documentation test and verify RED**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataDocumentationCharacterizationTest`

Expected: fail until docs are updated.

- [ ] **Step 3: Update docs**

Update the three source-of-truth docs with async manual sync behavior, polling by `syncRunId`, fixed daily time behavior, and safe audit/file-log summaries.

- [ ] **Step 4: Verify documentation GREEN**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataDocumentationCharacterizationTest`

Expected: selected documentation tests pass.

- [ ] **Step 5: Run targeted backend verification**

Run: `cd quote-backend; ./mvnw test -Dtest=MesMasterDataSyncControllerTest,MesMasterDataSyncScheduleConfigTest,MesMaterialMasterSyncServiceTest,MesProcessTypeSyncServiceTest,MesMasterDataMigrationTest,MesMasterDataDocumentationCharacterizationTest`

Expected: all selected backend tests pass.

- [ ] **Step 6: Run targeted frontend verification**

Run: `cd quote-frontend; npm test -- mes-master-data-sync.workflow.test.js adminDuplicateClickGuard.test.js`

Expected: selected frontend tests pass.

- [ ] **Step 7: Run full backend verification**

Run: `cd quote-backend; ./mvnw test`

Expected: full backend test suite passes.

- [ ] **Step 8: Commit Task 4**

Run: `git add quote-backend/src/test/java/com/machining/quote/MesMasterDataDocumentationCharacterizationTest.java docs/QPO-MES-WMS对接/10-MES工艺材料主数据同步说明.md docs/QPO日志模块/03-日志运维说明.md docs/QPO日志模块/04-主流程文件日志规范化开发文档.md && git commit -m "docs: update MES async sync operations guide"`

---

## Self-Review

- Spec coverage: tasks cover async POST submission, run status query, duplicate active guard, stale run cleanup, fixed-delay compatibility, daily fixed-time mode, frontend polling, docs, and targeted/full verification.
- Placeholder scan: no unresolved markers or unnamed implementation steps are present.
- Type consistency: plan consistently uses `SyncSubmitResult`, `syncRunId`, `scheduleMode`, `dailyRunTime`, `FIXED_DELAY`, and `DAILY_TIME`.
