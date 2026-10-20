# 销售订单零件关联废弃 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 在销售订单零件的 U9/变更操作旁增加关联废弃/关联恢复，一次处理该销售订单行仍处于工作池中的已提、待产和锁库记录，同时保留人工废弃记录的可逆边界。

**Architecture:** 新增销售订单关联废弃服务，锁定销售订单、零件行及关联交付记录，在同一事务中复用现有 U9/变更排除逻辑，并只标记当前工作池中的下游记录。销售订单行保存独立的 `linkedDiscarded` 状态；交付表保存废弃来源和来源销售订单零件 ID，关联恢复只处理来源为销售订单关联操作的记录。

**Tech Stack:** Spring Boot, MyBatis-Plus, PostgreSQL/Flyway, Vue 3, Element Plus, JUnit 5, Vitest.

## Global Constraints

- 不修改现有单项废弃/恢复接口的业务语义和权限。
- 复用 `market:sales-order:update`，不新增可分配权限点。
- 已完成 MES/WMS/生产流程的记录不处理；仍显示在当前工作池中的记录才执行关联废弃。
- 关联恢复不得恢复人工废弃的已提、待产或锁库记录。
- 不删除交付数据，不修改数量、历史快照、MES/WMS 状态或外部系统结果。
- 新增数据库结构使用下一未使用的 Flyway 迁移，不修改 `V1`、`V2` 或已执行迁移。

---

### Task 1: 锁定数据模型和迁移契约

**Files:**
- Create: `quote-backend/src/main/resources/db/migration/V53__sales_order_item_linked_discard.sql`
- Modify: `quote-backend/src/main/java/com/machining/quote/entity/SalesOrderItem.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/entity/OrderDeliveryTicket.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/entity/OrderDeliveryTicketAllocation.java`
- Test: `quote-backend/src/test/java/com/machining/quote/SalesOrderLinkedDiscardMigrationTest.java`

**Interfaces:**
- `sales_order_item.linked_discarded` 及其时间、操作人、原因字段提供销售订单按钮状态。
- 两张交付表提供 `discard_source` 和 `discard_source_sales_order_item_id`，关联操作使用固定来源值 `SALES_ORDER_ITEM_LINKED`。

- [ ] **Step 1: Write the failing migration/entity test**

断言新迁移包含三张表的字段、索引和默认值，实体包含对应驼峰字段，并且 baseline 文件未修改。

- [ ] **Step 2: Run the focused test and verify it fails**

Run: `cd D:\QPO-Operations-Management-System\quote-backend; .\mvnw.cmd -q "-Dtest=SalesOrderLinkedDiscardMigrationTest" test`

Expected: FAIL because the migration and entity contract are not present.

- [ ] **Step 3: Add V53 and entity fields**

迁移增加 `linked_discarded`、`linked_discarded_at`、`linked_discarded_by`、`linked_discard_reason`、`linked_restored_at`、`linked_restored_by`、`linked_restore_reason`，以及两张交付表的来源字段和按来源 ID查询的索引；实体使用 `linkedDiscarded`、`discardSource`、`discardSourceSalesOrderItemId` 等字段。

- [ ] **Step 4: Run the focused test and verify it passes**

Run the same Maven command. Expected: PASS.

---

### Task 2: 实现关联废弃/恢复事务服务

**Files:**
- Create: `quote-backend/src/main/java/com/machining/quote/dto/SalesOrderLinkedDiscardResult.java`
- Create: `quote-backend/src/main/java/com/machining/quote/service/SalesOrderLinkedDiscardService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/DeliveredItemService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/ProductionItemService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/LockedItemService.java`
- Test: `quote-backend/src/test/java/com/machining/quote/SalesOrderLinkedDiscardServiceTest.java`

**Interfaces:**
- `SalesOrderLinkedDiscardService.discard(Long salesOrderId, Long itemId, OrderStatusChangeReq req)`
- `SalesOrderLinkedDiscardService.restore(Long salesOrderId, Long itemId, OrderStatusChangeReq req)`
- Both return counts for linked delivered, production, locked, and skipped completed/already-discarded rows.

- [ ] **Step 1: Write failing service tests**

覆盖：按销售订单行 ID 找到直接销售订单提单和 RFQ 来源提单；只废弃 active 工作池记录；跳过完成记录；回滚 callback pending；恢复只处理 `SALES_ORDER_ITEM_LINKED`；人工废弃记录不被恢复；U9/变更排除和恢复被复用。

- [ ] **Step 2: Run service tests and verify failure**

Run: `cd D:\QPO-Operations-Management-System\quote-backend; .\mvnw.cmd -q "-Dtest=SalesOrderLinkedDiscardServiceTest" test`

Expected: FAIL because the service and result contract are absent.

- [ ] **Step 3: Implement the transaction service**

锁定顺序固定为订单、销售订单行、提单、分配行；关联提单使用 `source_order_id/source_item_id` 或销售订单行 `source_ticket_id` 定位；生产行按 `PRODUCTION_REQUIRED + mes_production_status != SUCCESS`，锁库行按 `IN_STOCK_LOCK/IN_PRODUCTION_LOCK + stock_picking_status != SUCCESS`，已完成提单按现有 `DeliveredItemService` 的完成口径跳过。废弃前预检查 callback pending，失败时整笔事务回滚。关联恢复只清理本次来源标记，并独立维护销售订单行关联状态。

- [ ] **Step 4: Mark manual discard/restore source metadata**

现有三个模块的人工废弃写入 `discardSource = MANUAL`；人工恢复清理来源字段，但不改变 MES/WMS/生产状态。

- [ ] **Step 5: Run service tests and verify pass**

Run the same Maven command. Expected: PASS.

---

### Task 3: 暴露后端接口并补齐审计目录

**Files:**
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/SalesOrderManageController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/AuditService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/AuditLogQueryService.java`
- Modify: `quote-backend/src/test/java/com/machining/quote/SalesOrderManagementGovernanceTest.java`
- Test: `quote-backend/src/test/java/com/machining/quote/SalesOrderLinkedDiscardApiTest.java`

**Interfaces:**
- `POST /api/market/sales-order-management/orders/{salesOrderId}/items/{itemId}/linked-discard`
- `POST /api/market/sales-order-management/orders/{salesOrderId}/items/{itemId}/linked-restore`
- Both require `market:sales-order:update` and accept `{ "reason": "..." }`.

- [ ] **Step 1: Write failing controller/governance tests**

断言两个接口路径、权限、服务委托和审计动作 `SALES_ORDER_ITEM_LINKED_DISCARD` / `SALES_ORDER_ITEM_LINKED_RESTORE` 存在。

- [ ] **Step 2: Implement controller and audit catalog**

新增接口并加入市场模块识别、动作白名单/标签和日志文档口径，摘要只包含订单行、关联数量、跳过数量和原因。

- [ ] **Step 3: Run backend API/governance tests**

Run: `cd D:\QPO-Operations-Management-System\quote-backend; .\mvnw.cmd -q "-Dtest=SalesOrderLinkedDiscardApiTest,SalesOrderManagementGovernanceTest,AuditActionCatalogCharacterizationTest" test`

Expected: PASS.

---

### Task 4: 增加销售订单详情按钮和 API 调用

**Files:**
- Modify: `quote-frontend/src/services/orderApi.js`
- Modify: `quote-frontend/src/components/SalesOrderManage.vue`
- Test: `quote-frontend/src/salesOrderLinkedDiscard.test.js`

**Interfaces:**
- `discardSalesOrderItemLinked(salesOrderId, itemId, payload)`
- `restoreSalesOrderItemLinked(salesOrderId, itemId, payload)`

- [ ] **Step 1: Write failing frontend tests**

断言现有 U9/变更按钮保持不变，右侧出现“关联废弃/关联恢复”，绑定 `linkedDiscarded`，调用新 endpoint，复用 `market:sales-order:update`，并在 U9 同步中/结果待确认时禁用。

- [ ] **Step 2: Implement API and UI**

增加按钮、原因弹窗、请求中防重复、成功后刷新详情；普通废弃和关联废弃使用独立 loading key。

- [ ] **Step 3: Run frontend tests**

Run: `cd D:\QPO-Operations-Management-System\quote-frontend; npm test -- salesOrderLinkedDiscard.test.js salesOrderManagementEdit.test.js services/orderApi.test.js`

Expected: PASS.

---

### Task 5: 回归、文档和提交

**Files:**
- Modify: `docs/QPO权限用户体系/02-权限点清单与现有角色映射.md`
- Modify: `docs/QPO日志模块/03-日志运维说明.md`

- [ ] **Step 1: Run focused backend and frontend suites**

Run the commands from Tasks 1-4, then run `git diff --check`.

- [ ] **Step 2: Run Flyway policy regression**

Run: `cd D:\QPO-Operations-Management-System\quote-backend; .\mvnw.cmd -q "-Dtest=FlywayMigrationPolicyTest" test`

Expected: PASS and baseline checksum policy remains clean.

- [ ] **Step 3: Update docs**

权限文档说明关联废弃/恢复复用 `market:sales-order:update`；日志文档说明两个新动作、来源字段和不记录完整快照/外部请求。

- [ ] **Step 4: Commit**

```powershell
git add docs/superpowers/plans/2026-09-22-sales-order-linked-discard.md docs/QPO权限用户体系/02-权限点清单与现有角色映射.md docs/QPO日志模块/03-日志运维说明.md quote-backend quote-frontend
git commit -m "feat: 销售订单零件关联废弃恢复"
```
