# 销售变更通知隐藏与恢复实施计划

> 按任务顺序实现。先确定并验证 V65，再继续后端接口、认领逻辑、权限、前端和文档。

**Goal:** 为销售变更通知池增加有效、已隐藏、已发送三种视图，以及带必填理由的批量隐藏和恢复，同时保护现有通知幂等、自动发送和下游状态。

**Architecture:** 在通知记录上持久化软隐藏状态，由后端列表、手动认领和自动认领统一执行可见性约束。批量操作在事务中锁定并校验所有通知，再整体更新；前端提供三种视图和对应批量操作。恢复旧积压不触发自动发送，新修改合并到隐藏的待发送修改通知时则重新显示，并按新变化的自动批次处理。

**Tech Stack:** Spring Boot、MyBatis-Plus、PostgreSQL、Flyway、Vue 3、Element Plus、Vitest、JUnit 5、Mockito。

## Global Constraints

- 新数据库迁移使用 `V65`；不修改 `V1`、`V2` 或任何已执行迁移。
- 隐藏和恢复只修改通知可见性及其审计元数据，不改变销售订单零件、Allocation、提单、WMS、MES 或提库存工单状态。
- `noticeId`、通知快照、下游工单定位键和发送状态在隐藏/恢复时保持不变。
- 隐藏理由必填，去除首尾空格后长度为 1 至 500 字符；恢复理由可选。
- 隐藏通知不得出现在自动认领、手动认领结果或当前活动视图中。
- 自动模式恢复旧待发送通知只重新显示并允许人工发送；新修改合并到隐藏通知时自动重新显示并按当前自动批次规则处理。
- 所有读取和写操作继续执行销售订单数据范围校验，后端权限校验是最终边界。
- 不记录通知 payload、完整外部请求/响应、Token、Authorization、Cookie 或凭据到审计摘要。

---

### Task 1: 增加通知可见性数据库字段与数据模型

**Files:**
- Create: `quote-backend/src/main/resources/db/migration/V65__sales_change_notice_visibility.sql`
- Modify: `quote-backend/src/main/java/com/machining/quote/entity/SalesOrderChangeNotice.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/dto/SalesOrderChangeNoticeDTO.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/dto/SalesOrderChangeNoticeQueryReq.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/SalesOrderLinkedChangeNoticeMigrationTest.java`

**Interfaces:**
- `SalesOrderChangeNotice.isHidden: Boolean`, `hiddenReason: String`, `hiddenAt: LocalDateTime`, `hiddenBy: Long`, `hiddenByName: String`, `restoredAt: LocalDateTime`, `restoredBy: Long`。
- `SalesOrderChangeNoticeDTO` 返回当前隐藏状态、隐藏理由、隐藏时间/操作人及最近恢复时间。
- `SalesOrderChangeNoticeQueryReq.view: String` 接收 `ACTIVE`、`HIDDEN` 或 `SENT`。

- [ ] **Step 1: 写迁移约束测试**

在 `SalesOrderLinkedChangeNoticeMigrationTest` 增加 `version65AddsNoticeVisibilityAndPermissionsWithoutEditingHistory`，读取 `V65__sales_change_notice_visibility.sql` 并断言字段、权限编码、权限 ID `237/238`、父权限 `233`、`BUTTON` 类型、角色授权、ID 冲突保护和序列推进。权限 `237` 为 `change:sales-change:hide`，权限 `238` 为 `change:sales-change:restore`；两项均只授予 `SUPER_ADMIN`、`MARKET_USER`、`MARKET_MANAGER`，不得授予 `READONLY`。断言序列至少推进到 `GREATEST(MAX(system_permission.id), 238)`，且 `V1__qpo_baseline_schema.sql` 和 `V2__qpo_baseline_seed_data.sql` 未被迁移引用。将当前最大迁移版本断言从 `64` 更新为 `65`。

- [ ] **Step 2: 运行迁移测试并确认失败**

Run: `.\mvnw -Dtest=SalesOrderLinkedChangeNoticeMigrationTest test`，工作目录 `quote-backend`。
Expected: 新迁移文件不存在，新增断言失败。

- [ ] **Step 3: 新建追加迁移并扩展实体/DTO**

先在一次性测试数据库中根据已有数据分布检查三个视图查询计划。测试事务中临时增加 `is_hidden` 后执行下列查询的 `EXPLAIN`，随后回滚临时 DDL；只在组合索引能改善计划时，将索引加入 V65，且 V65 写定后不再修改：

```sql
BEGIN;
ALTER TABLE sales_order_change_notice ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE;
EXPLAIN SELECT * FROM sales_order_change_notice WHERE is_hidden = FALSE AND status <> 'SENT' ORDER BY created_at DESC, id DESC LIMIT 50;
EXPLAIN SELECT * FROM sales_order_change_notice WHERE is_hidden = TRUE ORDER BY created_at DESC, id DESC LIMIT 50;
EXPLAIN SELECT * FROM sales_order_change_notice WHERE is_hidden = FALSE AND status = 'SENT' ORDER BY created_at DESC, id DESC LIMIT 50;
ROLLBACK;
```

迁移新增字段：

```sql
ALTER TABLE sales_order_change_notice
    ADD COLUMN is_hidden BOOLEAN NOT NULL DEFAULT FALSE,
    ADD COLUMN hidden_reason VARCHAR(500),
    ADD COLUMN hidden_at TIMESTAMP,
    ADD COLUMN hidden_by BIGINT,
    ADD COLUMN hidden_by_name VARCHAR(100),
    ADD COLUMN restored_at TIMESTAMP,
    ADD COLUMN restored_by BIGINT;
```

在 `SalesOrderChangeNotice` 增加 `Boolean isHidden`、`String hiddenReason`、`LocalDateTime hiddenAt`、`Long hiddenBy`、`String hiddenByName`、`LocalDateTime restoredAt`、`Long restoredBy`；在 DTO 增加 `Boolean hidden`、`String hiddenReason`、`LocalDateTime hiddenAt`、`String hiddenByName`、`LocalDateTime restoredAt`；在 `SalesOrderChangeNoticeQueryReq` 增加 `String view`。V65 为隐藏、恢复权限分别使用 ID `237`、`238`，父 ID 为 `233`，类型为 `BUTTON`，按角色代码授予 `SUPER_ADMIN`、`MARKET_USER`、`MARKET_MANAGER`，不授予 `READONLY`。写入权限种子前增加 SQL 前置检查：ID `237/238` 若已被其他权限代码占用，或目标权限代码已绑定其他 ID，则使迁移明确失败，不覆盖既有权限。通过前置检查后，按 V64 的迁移模式对权限目录使用 `ON CONFLICT (id) DO UPDATE`，对角色映射使用 `ON CONFLICT (role_id, permission_id) DO NOTHING`，并执行 `setval('system_permission_id_seq', GREATEST((SELECT COALESCE(MAX(id), 1) FROM system_permission), 238), TRUE)`。权限种子只在 V65 写入，不在后续任务修改已执行迁移。

- [ ] **Step 4: 运行迁移测试确认通过**

Run: `.\mvnw -Dtest=SalesOrderLinkedChangeNoticeMigrationTest test`，工作目录 `quote-backend`。
Expected: 测试通过，`V1`、`V2` 保持不变。

### Task 2: 实现事务性批量隐藏与恢复接口

**Files:**
- Create: `quote-backend/src/main/java/com/machining/quote/dto/SalesOrderChangeNoticeVisibilityReq.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/SalesOrderChangeNoticeController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/SalesOrderChangeNoticeService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/mapper/SalesOrderChangeNoticeMapper.java`
- Test: `quote-backend/src/test/java/com/machining/quote/SalesOrderChangeNoticeServiceTest.java`

**Interfaces:**
- Request body: `{ "noticeIds": ["QPO-SOCN-..."], "reason": "..." }`。
- `SalesOrderChangeNoticeService.hide(List<String> noticeIds, String reason): int`。
- `SalesOrderChangeNoticeService.restore(List<String> noticeIds, String reason): int`。
- `POST /api/change/sales-order-changes/batch-hide` returns `{ "affectedCount": n }`。
- `POST /api/change/sales-order-changes/batch-restore` returns `{ "affectedCount": n }`。

- [ ] **Step 1: 写批量操作失败路径测试**

在 `SalesOrderChangeNoticeServiceTest` 增加测试，覆盖：空白/超 500 字符隐藏理由拒绝；空 ID 集合拒绝；`SENDING`、`SENT`、已隐藏通知不能再次隐藏；非隐藏通知不能恢复；传入 IDs 中任意一条不在当前用户数据范围或不可操作时整批拒绝且不更新任何通知。

- [ ] **Step 2: 运行目标测试确认失败**

Run: `.\mvnw -Dtest=SalesOrderChangeNoticeServiceTest test`，工作目录 `quote-backend`。
Expected: 批量接口/服务方法不存在，新增测试失败。

- [ ] **Step 3: 实现原子校验与状态更新**

新增请求 DTO 的 `List<String> noticeIds`、`String reason`。服务方法先用现有 `normalizeIds` 限制 1 至 1000 个去重 ID，再通过 `QueryWrapper` 查询 `notice_id IN (...)`，应用现有 `applyScope(query)`，按 `id` 排序并追加 `FOR UPDATE`。隐藏要求查询行数等于请求 ID 数量且每行 `is_hidden=false`、状态属于 `PENDING/FAILED/RESULT_UNKNOWN`；恢复要求每行 `is_hidden=true` 且状态属于相同集合。校验全量通过后才逐行更新，保证整批事务原子性。更新行数必须等于锁定行数；出现并发状态变化或条件更新不完整时回滚整批，不返回部分成功数量。

隐藏将 `is_hidden=true`、保存去空格后的理由、`hidden_at`、当前用户 ID/姓名；恢复将 `is_hidden=false`、写入 `restored_at` 和 `restored_by`，保留通知状态、快照、attempt count、`noticeId` 和外部工单 ID。恢复时将 `PENDING.autoDispatchEpoch` 设为 `0`，使自动模式调度器不会把恢复积压当作当前批次新通知。

在控制器新增两个 `@PostMapping`，分别使用 `@RequirePermission("change:sales-change:hide")` 和 `@RequirePermission("change:sales-change:restore")`，以 `Map.of("affectedCount", count)` 返回结果。

- [ ] **Step 4: 运行服务测试并确认通过**

Run: `.\mvnw -Dtest=SalesOrderChangeNoticeServiceTest test`，工作目录 `quote-backend`。
Expected: 批量全量校验和理由校验测试通过；已有发送认领测试不回归。

### Task 3: 统一列表视图、发送认领与修改合并行为

**Files:**
- Modify: `quote-backend/src/main/java/com/machining/quote/service/SalesOrderChangeNoticeService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/mapper/SalesOrderChangeNoticeMapper.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/SalesOrderChangeNoticeServiceTest.java`
- Create: `quote-backend/src/test/java/com/machining/quote/SalesOrderChangeNoticeVisibilityConcurrencyIntegrationTest.java`

**Interfaces:**
- `GET /api/change/sales-order-changes` 接收 `view=ACTIVE|HIDDEN|SENT`。
- `SalesOrderChangeNoticeDTO.hidden` 用于页面展示当前状态和选择资格。
- 自动认领 SQL 与手动认领 SQL 都排除 `is_hidden=true`。

- [ ] **Step 1: 写活动视图、隐藏视图和发送边界测试**

为 `noticeQuery` 增加 wrapper 条件测试：默认 `ACTIVE` 限定 `is_hidden=false AND status <> 'SENT'`；`HIDDEN` 限定 `is_hidden=true`；`SENT` 限定 `is_hidden=false AND status='SENT'`。同时保留客户、订单、行号、类型、状态、日期和数据范围条件。由数据库集成测试验证稳定隐藏行无法通过手动或自动认领。手动和自动认领 mapper 的条件更新都必须含 `is_hidden = FALSE`；服务只有在实际更新行数等于待认领 ID 数量时才能返回这些 ID，数量不一致时回滚，避免异步发送任务收到并未进入 `SENDING` 的通知。

- [ ] **Step 2: 写隐藏通知修改合并测试**

扩展现有 `modifyingAnExistingPendingNoticeMovesItIntoTheCurrentAutomaticEpoch` 测试：令既有 `PENDING MODIFY` 通知 `isHidden=true`，调用 `captureModify` 后断言 `isHidden=false`、原 `noticeId` 不变、原 `changedFields` 与本次字段累积、payload 更新、`autoDispatchEpoch` 更新为当前 epoch。

- [ ] **Step 3: 实现查询和认领过滤**

在 `noticeQuery` 中按 view 注入严格数据库条件，未知 view 拒绝请求；缺省 view 按 `ACTIVE` 处理。`SalesOrderChangeNoticeMapper.selectAutoPendingForUpdate` 增加 `AND is_hidden = FALSE`；`claimAuto` 更新条件增加 `AND is_hidden = FALSE`；`claimManual` 更新条件增加相同条件。服务层 `claimManual` 的锁定查询也增加 `is_hidden=false`。两个认领流程都校验条件更新行数与候选行数相同，否则回滚且不向异步任务返回该批 ID。DTO 映射补齐隐藏元数据。

在 `upsert` 合并既有 `PENDING MODIFY` 时清除 `is_hidden`，保留最近隐藏/恢复元数据作为可追溯历史；保留已有 `noticeId`，继续合并字段、刷新 payload，并沿用当前逻辑设置当前自动 epoch 或手动模式 epoch `0`。恢复操作和合并操作分别保持：恢复只设 epoch `0`，合并新变化按当前发送模式设 epoch。

新增 `SalesOrderChangeNoticeVisibilityConcurrencyIntegrationTest`，使用 `@SpringBootTest`、`JdbcTemplate` 和独立线程/事务验证稳定隐藏行不能被手动或自动认领，并并发执行隐藏与手动认领、隐藏与自动认领。允许两种串行结果：隐藏先完成时认领不得返回该 ID；认领先完成时状态为 `SENDING`，隐藏必须拒绝。每种并发结果都断言数据库中不存在 `is_hidden = TRUE AND status = 'SENDING'`，并断言认领接口返回的 ID 确实处于 `SENDING`。测试沿用现有集成测试的测试数据库、数据范围和 `AuthContext` 初始化方式，不调用数据中心或 MES。

- [ ] **Step 4: 运行通知服务和 SQL 迁移相关测试**

Run: `.\mvnw -Dtest=SalesOrderChangeNoticeServiceTest,SalesOrderLinkedChangeNoticeMigrationTest,SalesOrderChangeNoticeVisibilityConcurrencyIntegrationTest test`，工作目录 `quote-backend`。
Expected: 三种视图条件、隐藏排除认领、恢复积压不自动认领、新变化自动解隐藏和并发互斥测试通过。

### Task 4: 登记权限与操作审计

**Files:**
- Modify: `quote-backend/src/main/java/com/machining/quote/service/PermissionService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/AuditService.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/SalesOrderLinkedChangeNoticeMigrationTest.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/AuditServiceTests.java`
- Create: `quote-backend/src/test/java/com/machining/quote/SalesOrderChangeNoticePermissionTest.java`

**Interfaces:**
- `change:sales-change:hide` 和 `change:sales-change:restore` 均为 `BUTTON` 权限，父权限为 `change:sales-change:view`。
- 默认角色：`SUPER_ADMIN`、`MARKET_USER`、`MARKET_MANAGER` 可隐藏/恢复；`READONLY` 只查看。
- 审计动作：`SALES_ORDER_CHANGE_NOTICE_HIDE`、`SALES_ORDER_CHANGE_NOTICE_RESTORE`。

- [ ] **Step 1: 写权限目录与控制器守卫测试**

在 `SalesOrderChangeNoticePermissionTest` 检查列表、隐藏、恢复端点的 `@RequirePermission`，并使用 `PermissionService` 权限目录断言新增两项为按钮权限且挂在销售变更菜单下。扩展 V65 迁移测试，断言迁移为权限分配对应角色并保留 `READONLY` 查看但不给写权限。

- [ ] **Step 2: 运行权限测试确认失败**

Run: `.\mvnw -Dtest=SalesOrderChangeNoticePermissionTest,SalesOrderLinkedChangeNoticeMigrationTest test`，工作目录 `quote-backend`。
Expected: 新权限和控制器守卫测试失败。

- [ ] **Step 3: 增加权限种子、运行时目录和审计标签**

在 `PermissionService` 使用 ID `237`、`238` 注册 `change:sales-change:hide`、`change:sales-change:restore`，挂到父 ID `233`；默认角色映射为 `SUPER_ADMIN`、`MARKET_USER`、`MARKET_MANAGER`，保持 `READONLY` 只有查看权限。V65 已按 Task 1 写入相同种子和序列更新，本任务不得再修改 V65。在 `AuditService.labelForAction` 增加两个中文动作名。

每次批量操作写一条审计摘要，记录受影响 notice ID、销售订单 ID、零件行 ID、Allocation ID、原通知状态、操作人、影响数量和耗时；隐藏操作记录必填隐藏理由，恢复操作在理由有值时记录理由。不把 payload 快照或外部请求/响应写入摘要。隐藏理由同时保存在通知记录和审计摘要中。

- [ ] **Step 4: 运行权限与审计相关测试**

Run: `.\mvnw -Dtest=SalesOrderChangeNoticePermissionTest,SalesOrderLinkedChangeNoticeMigrationTest,AuditServiceTests,PermissionCatalogCharacterizationTest test`，工作目录 `quote-backend`。
Expected: 端点守卫、权限类型/角色范围和迁移断言通过。

### Task 5: 增加三视图和前端批量操作

**Files:**
- Modify: `quote-frontend/src/services/salesOrderChangeApi.js`
- Modify: `quote-frontend/src/services/salesOrderChangePolicy.js`
- Modify: `quote-frontend/src/components/SalesOrderChangeNoticeManage.vue`
- Modify: `quote-frontend/src/salesOrderChangeNotice.workflow.test.js`
- Modify: `quote-frontend/src/salesOrderChangePolicy.test.js`

**Interfaces:**
- API helpers：`hideSalesOrderChangeNotices(noticeIds, reason)`、`restoreSalesOrderChangeNotices(noticeIds, reason = '')`。
- 查询参数新增 `view`，页面视图值为 `ACTIVE`、`HIDDEN`、`SENT`。
- 页面权限常量：`change:sales-change:hide`、`change:sales-change:restore`。

- [ ] **Step 1: 写前端策略与页面工作流测试**

新增选择策略断言：活动视图允许选择可隐藏状态，即使自动模式当前 epoch 的 `PENDING` 不可手动发送；隐藏视图允许选择可恢复状态；已发送视图不允许选择。页面工作流断言三个视图、批量隐藏/恢复权限、必填隐藏理由提示、调用新 API 并刷新当前视图。

- [ ] **Step 2: 运行 Vitest 确认新断言失败**

Run: `npm test -- src/salesOrderChangeNotice.workflow.test.js src/salesOrderChangePolicy.test.js`，工作目录 `quote-frontend`。
Expected: 新视图、API 和策略断言失败。

- [ ] **Step 3: 实现 API 与选择策略**

在 `salesOrderChangeApi.js` 增加：

```js
export const hideSalesOrderChangeNotices = (noticeIds = [], reason) => request(`${BASE}/batch-hide`, {
  method: 'POST',
  data: { noticeIds, reason }
})
export const restoreSalesOrderChangeNotices = (noticeIds = [], reason = '') => request(`${BASE}/batch-restore`, {
  method: 'POST',
  data: { noticeIds, reason }
})
```

策略函数分别计算 `canHideSalesChangeNotice`、`canRestoreSalesChangeNotice`，隐藏资格为 `!row.hidden && status in PENDING/FAILED/RESULT_UNKNOWN`，恢复资格为 `row.hidden && status in PENDING/FAILED/RESULT_UNKNOWN`。发送资格继续使用现有 `canSelectSalesChangeNotice`，不得把显示资格和发送资格混为一谈。

- [ ] **Step 4: 实现页面视图与批量交互**

沿用 QPO 页面标题、筛选工具栏、表格容器和分页样式；筛选条件放左侧，操作放右侧，批量隐藏与发送动作视觉分组，避免多个竞争的主按钮。复用 `erp-table-surface` 等现有类，不增加 UI 依赖。按前端规范为异步写操作增加加载状态和重复点击保护。

在页面顶部增加 Element Plus `el-radio-group` 或 `el-radio-button` 视图切换，默认 `ACTIVE`。将 `view` 加入 `queryParams`，切换视图时清空选中行、页码归 1 并重新请求。

活动视图显示“批量隐藏”和批量发送；隐藏视图显示“批量恢复”；已发送视图只读。表格勾选资格根据当前视图的隐藏/恢复可操作状态计算。批量隐藏使用 `ElMessageBox.prompt` 要求非空理由，去除首尾空格并限制 500 字符；取消时不调用接口。批量恢复确认后调用恢复 API。成功后清空选择并刷新列表。详情在隐藏视图展示隐藏理由、时间和操作人。

- [ ] **Step 5: 运行前端定向测试和构建**

Run: `npm test -- src/salesOrderChangeNotice.workflow.test.js src/salesOrderChangePolicy.test.js`，Expected: 相关 Vitest 测试通过。
Run: `npm run build`，Expected: Vite 构建退出码为 0。

### Task 6: 更新业务文档并完成集成验证

**Files:**
- Modify: `docs/QPO报价状态流转/47-销售变更模块开发文档.md`
- Modify: `docs/QPO权限用户体系/02-权限点清单与现有角色映射.md`
- Modify: `docs/QPO日志模块/03-日志运维说明.md`

**Interfaces:**
- 文档统一说明活动、隐藏、已发送视图、批量接口、权限、审计动作、自动模式恢复语义。
- 将 Task 1 中得到的 V65 索引执行计划结论写入销售变更模块文档；本任务不再修改 V65。

- [ ] **Step 1: 对照设计逐项更新文档**

在销售变更开发文档增加隐藏状态与三视图说明，写明隐藏不会修改业务/下游状态、隐藏理由规则、`SENDING/SENT` 禁止隐藏、恢复积压需手动发送、新修改合并会重新显示。权限文档增加两项权限及角色分配。日志文档增加两个审计动作和摘要边界。

- [ ] **Step 2: 执行 V65 数据库执行计划检查**

复核 Task 1 对活动、隐藏、已发送视图执行的 PostgreSQL `EXPLAIN` 结果，并将实际结论写入模块文档。若 Task 1 确认索引有收益，验证 V65 中已经包含该索引；不得在 V65 写定后修改它，也不得修改 V64 或已应用迁移。

- [ ] **Step 3: 运行后端、前端和迁移策略验证**

Run，工作目录 `quote-backend`：

```powershell
.\mvnw -Dtest=SalesOrderChangeNoticeServiceTest,SalesOrderChangeNoticePermissionTest,SalesOrderLinkedChangeNoticeMigrationTest,SalesOrderChangeNoticeVisibilityConcurrencyIntegrationTest,AuditServiceTests,PermissionCatalogCharacterizationTest test
```

Expected: Maven 退出码为 0，失败数为 0。

Run，工作目录 `quote-frontend`：

```powershell
npm test
npm run build
```

Expected: Vitest 全部通过，Vite 构建退出码为 0。

Run，仓库根目录：

```powershell
git diff --check
```

Expected: 无空白错误；`git status` 中包含本功能预期文件，不覆盖或清理实现开始前已有的其他改动。

- [ ] **Step 4: 对照验收矩阵复核**

确认以下场景均有测试或明确的数据库/接口验证：批量理由校验和全有/全无；隐藏项无法手动/自动认领；自动模式恢复只变可见不自动发送；新修改合并解除隐藏并使用当前 epoch；`SENDING/SENT` 不能隐藏；成功发送项离开活动视图并出现在已发送视图；权限和数据范围不变；隐藏/恢复不修改下游业务字段。

V65 在 Task 1 写定后不得修改。实现期间不自动提交 Git；是否提交由用户另行决定。

## Self-Review

- **Spec coverage:** 数据模型/迁移在 Task 1；事务性隐藏恢复接口在 Task 2；三种视图、发送认领排除和合并解隐藏在 Task 3；权限与审计在 Task 4；前端视图和批量交互在 Task 5；文档、执行计划和全量验证在 Task 6。
- **Placeholder scan:** 本计划未留下待补信息、伪代码、未定义函数签名或仅指向前文的测试步骤。条件索引仅由真实 `EXPLAIN` 决定，并明确保持 V64 及历史迁移不变。
- **Type consistency:** API helper、请求 DTO、service 方法、`view` 参数、DTO 隐藏属性和权限编码在各任务间保持一致。
- **Scope:** 这是一个闭环能力，涉及一个通知表、一组列表与批量 API、相同权限/审计模块和一个 Vue 页面；这些部分共享隐藏状态与发送资格，不适合拆成互不依赖的子项目。
