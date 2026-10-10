# 锁库拆行按选中 Allocation 获取库存实施计划

> **For agentic workers:** Execute this plan task-by-task in the current checkout. Keep the existing order-level WMS action backward compatible.

**Goal:** 在销售订单详情的“锁库拆行”表中支持按行选择待产 Allocation，并通过同一 `WMS_ALLOCATION_BATCH` 任务只获取选中零件的库存信息。

**Architecture:** 复用现有销售订单 WMS 权限、任务、分组、恢复和响应应用逻辑。后端将订单级接口扩展为可选接收 `allocationIds`，空请求保持整单行为，非空请求只接受属于当前订单且满足 WMS 操作门槛的 `PRODUCTION_REQUIRED` Allocation。前端在锁库拆行表增加当前页单选/全选、选中批量按钮、状态提示和提交后刷新。

**Tech Stack:** Spring Boot、MyBatis-Plus、Vue 3、Element Plus、Vitest、JUnit 5、Flyway 文档测试。

## Global Constraints

- WMS 请求必须以有效、数量大于 0 的 `PRODUCTION_REQUIRED` Allocation 为操作对象。
- 既有整单“获取库存信息”入口、旧提单批量接口和 `WMS_ALLOCATION_BATCH` 任务类型保持兼容。
- 后端权限 `market:sales-order:wms-allocate` 是最终安全边界，前端选择限制不能替代后端校验。
- 不新增数据库迁移；不改变 Allocation、WMS Reservation 或 MES 的数量守恒与恢复逻辑。
- WMS 外部请求、凭据和完整响应不得写入审计摘要或日志。

---

### Task 1: 扩展销售订单 WMS 任务为可选 Allocation

**Files:**
- Create: `quote-backend/src/main/java/com/machining/quote/dto/SalesOrderWmsAllocationBatchReq.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/controller/SalesOrderManageController.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/SalesOrderWmsAllocationService.java`
- Modify: `quote-backend/src/main/java/com/machining/quote/service/SystemTaskService.java`
- Test: `quote-backend/src/test/java/com/machining/quote/SalesOrderWmsAllocationServiceTest.java`
- Test: `quote-backend/src/test/java/com/machining/quote/DeliveryIntegrationBatchTaskTest.java`

**Interfaces:**
- Request body: `{ "allocationIds": [101, 102] }`; null、空数组表示整单兼容模式。
- Controller signature: `submitWmsAllocationBatch(Long salesOrderId, SalesOrderWmsAllocationBatchReq req)`。
- Task service signature: `submitSalesOrderWmsAllocationTask(Long salesOrderId, List<Integer> requestedAllocationIds)`。
- `SalesOrderWmsAllocationService` 提供按订单校验并筛选选中 Allocation 的方法；非法、非本订单、非待产、数量非正、物料号缺失、已废弃、MES 已开始或 WMS 已处理的行全部跳过并返回逐行原因。

- [ ] 增加请求 DTO，并让 controller 将请求中的 `allocationIds` 传给任务服务。
- [ ] 保持空选择时调用现有 `resolveAllocationIds(salesOrderId)`；非空选择只从当前订单的生产 Allocation 集合中按 ID 保留合格行。
- [ ] 审计摘要增加 `selectionMode`、`requestedAllocationCount`、`eligibleAllocationCount`，只记录订单和数量，不记录外部载荷。
- [ ] 为跨订单 ID、锁库类型、数量为零、物料号缺失、MES/WMS 已处理和空选择补充单元测试，并保留旧整单测试。

### Task 2: 锁库拆行表增加选择和批量获取按钮

**Files:**
- Modify: `quote-frontend/src/services/orderApi.js`
- Modify: `quote-frontend/src/components/SalesOrderManage.vue`
- Test: `quote-frontend/src/salesOrderWmsAllocation.test.js`

**Interfaces:**
- API helper: `submitSalesOrderWmsAllocationBatch(salesOrderId, allocationIds = [])` posts `{ allocationIds }`; no IDs keeps the existing empty-body call behavior.
- UI state: `selectedAllocationRows` and `selectedAllocationIds` track selectable `PRODUCTION_REQUIRED` rows on the current allocation-split page.

- [ ] 在锁库拆行表增加表头全选和单行复选框，使用 `allocationId` 作为行键；锁库行、零数量、废弃、缺料号和已处理行禁选。
- [ ] 在原“获取库存信息”旁增加“批量获取库存信息”，无选中行时禁用；单选与多选统一走选中 Allocation 请求。
- [ ] 批量确认框显示选中数量，并明确只影响选中待产行；原整单按钮继续显示并保持原确认语义。
- [ ] 提交成功后复用现有任务展示、轮询和 `fetchAllocationSplits()` 刷新；翻页、切换订单和关闭详情时清空选择。
- [ ] 添加前端断言：API 发送 `allocationIds`、表格有选择列、不可选规则和批量按钮存在，且整单入口仍存在。

### Task 3: 文档与回归验证

**Files:**
- Modify: `docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md`
- Modify: `docs/QPO日志模块/03-日志运维说明.md`
- Modify: `docs/QPO-MES-WMS对接/13-销售订单可选WMS库存获取开发文档.md`

- [ ] 说明销售订单 WMS 权限同时覆盖整单和选中 Allocation 获取库存，权限码保持 `market:sales-order:wms-allocate`。
- [ ] 说明锁库拆行选择只提交有效 `PRODUCTION_REQUIRED` Allocation，任务仍使用 `WMS_ALLOCATION_BATCH`，审计仅记录选择数量和订单信息。
- [ ] 运行相关后端单元测试、前端销售订单 WMS 测试和全量前端测试。
- [ ] 运行 `git diff --check`，确认没有格式错误或意外迁移文件。
