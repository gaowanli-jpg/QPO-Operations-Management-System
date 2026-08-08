# QPO WMS 锁库分配与锁库零件模块开发文档

> 数据中心统一对接阶段 8 收口：WMS 库存锁定底层调用使用 `WMS_INVENTORY_ALLOCATE` endpoint，链路为 `QPO -> 数据中心 -> WMS`。旧 `ERP_WMS_*` 仅作为 deprecated fallback，新部署应优先在系统配置的出站接口中维护数据中心连接和该功能 path。

> 销售订单导入即提单阶段 7 收口：销售订单来源提单行由销售订单导入自动生成，不再要求市场用户手动销售匹配或手动议价提单。销售订单导入后直接进入已提零件需求池，WMS 锁库仍只消费 `order_delivery_ticket` 中已取得 MES 物料号的需求行。

> 2026-08-08 两级工艺主数据阶段 15 收口：WMS 锁库不重新查询当前工艺字典，不重新计价。`order_delivery_ticket_allocation` 只复制原始提单行的标准工艺快照，锁库零件和待产零件页面展示该快照用于追溯；文件日志和审计摘要只记录 `processRouteCount`，不得记录完整工艺 JSON。

## 1. 背景

`02-基础数据模型与已提零件模块开发文档.md` 完成后，QPO 已具备 `order_delivery_ticket_allocation` 锁库拆分明细表和“已提零件”需求池页面。`03-MES物料号同步开发文档.md` 完成后，提单需求行可获得 MES 返回的 `material_no`。

本开发文档在上述基础上实现 WMS 锁库分配：

- 用户在“已提零件”页面对已有物料号的提单需求主动提交 WMS 锁库。
- WMS 返回已入库可锁数量、动态长度在产订单锁定数组。
- QPO 将一个提单需求拆分为多条 `order_delivery_ticket_allocation` 明细。
- 新增“锁库零件”页面展示拆分后的锁库结果。

## 2. 目标

### 2.1 本阶段目标

- 新增 WMS 客户端配置和调用封装。
- 新增 WMS 锁库任务接口和异步任务。
- 支持 WMS 返回多个在产订单及对应锁定数量。
- 成功后写入 `order_delivery_ticket_allocation`。
- 成功后更新 `order_delivery_ticket.wms_allocate_status=SUCCESS`。
- 失败后更新 `order_delivery_ticket.wms_allocate_status=FAILED` 和失败摘要。
- 新增“锁库零件”页面，放在左侧 **订单** 分组下。
- 新增并接入 `market:delivered-item:wms-allocate` 和 `market:locked-item:view` 权限。
- 操作日志和后端文件日志覆盖提交、开始、成功、失败。

### 2.2 非目标

- 不实现 WMS 锁库释放、撤销或反向回滚。
- 不实现成功分配后的覆盖重算。
- 不把库存台账维护搬进 QPO。
- 不自动定时提交 WMS。
- 不修改原始 `order_delivery_ticket.qty`。
- 不删除旧 APS 代码，旧 APS 收口放到 `05-权限日志运维与回归收口开发文档.md`。

## 3. 前置条件

必须先完成：

- `02-基础数据模型与已提零件模块开发文档.md`
  - `order_delivery_ticket_allocation` 已存在。
  - `order_delivery_ticket` 已有 WMS 状态字段。
  - “已提零件”页面已存在。
- `03-MES物料号同步开发文档.md`
  - `material_no` 可通过 MES 写回。
  - “已提零件”页面已有 MES 状态展示。

## 4. 目标流程

```text
用户进入 已提零件
  -> 选择 material_no 已存在且 WMS 状态不是 SUCCESS/PROCESSING 的提单行
  -> 点击 提交WMS锁库 / 重试
  -> 后端校验权限、数据范围、物料号、状态
  -> 创建 system_task: WMS_ALLOCATION
  -> ticket.wms_allocate_status = PROCESSING
  -> 异步调用 WMS
  -> WMS 返回已入库数量 + 在产订单数组
  -> QPO 校验数量不超过需求数量
  -> QPO 自动计算待生产数量
  -> QPO 在一个事务中写入 allocation 明细
  -> ticket.wms_allocate_status = SUCCESS
  -> 锁库零件页面展示拆分结果
```

失败流程：

```text
WMS 调用异常 / 返回数量超额 / 在产订单缺单号 / 物料号缺失
  -> system_task.status = FAILED
  -> ticket.wms_allocate_status = FAILED
  -> ticket.wms_allocate_message = 失败摘要
  -> 不写入半成品 allocation
  -> qpo-error.log 记录 taskId + ticketId + 异常堆栈
```

## 5. 文件清单

### 5.1 后端新增或修改

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 新增 | `quote-backend/src/main/resources/db/migration/V47__delivery_ticket_wms_allocation_permissions.sql` | 新增 WMS 分配权限和锁库零件菜单权限。 |
| 修改 | `quote-backend/src/main/resources/schema.sql` | 如 02 未完全同步 allocation 表，本阶段补齐。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/service/PermissionService.java` | fallback catalog 和默认角色映射。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/dto/WmsAllocationReq.java` | QPO 调 WMS 请求 DTO。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/dto/WmsAllocationResp.java` | WMS 返回 DTO。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/dto/LockedItemDTO.java` | 锁库零件列表 DTO。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/dto/LockedItemPageDTO.java` | 锁库零件分页 DTO。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/service/WmsAllocationClient.java` | WMS HTTP 客户端。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/service/WmsAllocationService.java` | WMS 分配业务服务。 |
| 新增或扩展 | `quote-backend/src/main/java/com/machining/quote/service/LockedItemService.java` | 锁库零件查询服务。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/service/SystemTaskService.java` | 增加 `submitWmsAllocationTask(ticketId)`。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/controller/OrderDeliveryController.java` | 增加 WMS 触发接口和 allocation 查询接口。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/service/AuditService.java` | 增加 WMS 动作中文化。 |
| 修改 | `docker-compose.yml` | 注入 WMS 配置。 |
| 修改 | `erp.env` | 增加 WMS 配置项。 |

### 5.2 前端新增或修改

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 修改 | `quote-frontend/src/services/deliveredItemApi.js` | 增加 `submitWmsAllocation(ticketId)`、`listLockedItems(params)`。 |
| 修改 | `quote-frontend/src/components/DeliveredItemManage.vue` | 增加 WMS 分配按钮、查看锁库结果入口。 |
| 新增 | `quote-frontend/src/components/LockedItemManage.vue` | 锁库零件页面。 |
| 修改 | `quote-frontend/src/router/index.js` | 新增 `/locked-items` 路由。 |
| 修改 | `quote-frontend/src/App.vue` | 订单分组新增“锁库零件”。 |

### 5.3 测试和文档

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 新增 | `quote-backend/src/test/java/com/machining/quote/WmsAllocationTest.java` | WMS 分配服务测试。 |
| 新增 | `quote-backend/src/test/java/com/machining/quote/WmsAllocationClientTest.java` | WMS 客户端契约测试。 |
| 新增 | `quote-backend/src/test/java/com/machining/quote/LockedItemQueryTest.java` | 锁库零件查询测试。 |
| 修改 | `quote-backend/src/test/java/com/machining/quote/AuditActionCoverageTests.java` | 增加 WMS 操作日志动作。 |
| 修改 | `quote-backend/src/test/java/com/machining/quote/PermissionModuleTreeTest.java` | 冻结 WMS 和锁库零件权限。 |
| 修改 | `quote-frontend/src/deliveredItemApi.test.js` | 增加 WMS API 测试。 |
| 新增 | `quote-frontend/src/deliveredItemWmsAllocation.test.js` | 已提零件 WMS 按钮测试。 |
| 新增 | `quote-frontend/src/lockedItemManage.test.js` | 锁库零件页面测试。 |
| 修改 | `quote-frontend/src/permissionStage4.test.js` | 冻结 `/locked-items` 权限。 |
| 修改 | `quote-frontend/src/appShell.test.js` | 冻结左侧菜单四字模块名。 |

## 6. 配置设计

### 6.1 `erp.env`

新增：

```env
ERP_WMS_BASE_URL=http://wms.example.local
ERP_WMS_AUTH_TOKEN=
ERP_WMS_ALLOCATION_PATH=/api/wms/inventory/allocate
ERP_WMS_TIMEOUT_MS=10000
```

说明：

- 继续使用 `ERP_` 前缀，保持 QPO 部署兼容。
- token 为空时不发送 Authorization header。
- 本地测试使用 mock，不依赖真实 WMS。

### 6.2 `docker-compose.yml`

backend 环境变量映射：

```yaml
ERP_WMS_BASE_URL: ${ERP_WMS_BASE_URL:-}
ERP_WMS_AUTH_TOKEN: ${ERP_WMS_AUTH_TOKEN:-}
ERP_WMS_ALLOCATION_PATH: ${ERP_WMS_ALLOCATION_PATH:-/api/wms/inventory/allocate}
ERP_WMS_TIMEOUT_MS: ${ERP_WMS_TIMEOUT_MS:-10000}
```

Spring 读取建议：

```java
@Value("${erp.wms.base-url:}")
private String wmsBaseUrl;

@Value("${erp.wms.auth-token:}")
private String wmsAuthToken;

@Value("${erp.wms.allocation-path:/api/wms/inventory/allocate}")
private String allocationPath;

@Value("${erp.wms.timeout-ms:10000}")
private Integer timeoutMs;
```

## 7. WMS 接口契约

### 7.1 QPO 请求 WMS

HTTP：

```text
POST {ERP_WMS_BASE_URL}{ERP_WMS_ALLOCATION_PATH}
Content-Type: application/json
Authorization: Bearer {ERP_WMS_AUTH_TOKEN}
```

请求体：

```json
{
  "ticketId": 123,
  "rfqId": 88,
  "rfqNo": "RFQ-20260721-001",
  "customerName": "ACME",
  "materialNo": "MAT-000123",
  "partNo": "P-1001",
  "version": "A",
  "internalProjectNo": "IP-20260721-001",
  "demandQty": 10
}
```

字段规则：

| 字段 | 必填 | 来源 |
| --- | --- | --- |
| `ticketId` | 是 | `order_delivery_ticket.id` |
| `rfqId` | 是 | `order_delivery_ticket.rfq_id` |
| `rfqNo` | 是 | `order_rfq.rfq_no` |
| `customerName` | 是 | `order_rfq.customer_name` |
| `materialNo` | 是 | `order_delivery_ticket.material_no` |
| `partNo` | 是 | `order_delivery_ticket.part_no` |
| `version` | 否 | `order_delivery_ticket.version` |
| `internalProjectNo` | 是 | `order_delivery_ticket.internal_project_no` |
| `demandQty` | 是 | `order_delivery_ticket.qty` |

### 7.2 WMS 返回 QPO

成功：

```json
{
  "success": true,
  "materialNo": "MAT-000123",
  "inStockLockedQty": 3,
  "inProductionLocks": [
    { "productionOrderNo": "MO-001", "lockedQty": 4 },
    { "productionOrderNo": "MO-002", "lockedQty": 2 }
  ],
  "message": "allocated",
  "externalTraceId": "WMS-TRACE-001"
}
```

失败：

```json
{
  "success": false,
  "message": "material not found",
  "externalTraceId": "WMS-TRACE-002"
}
```

QPO 处理规则：

- `success=true` 才进入数量校验。
- `inStockLockedQty` 为空时按 0。
- `inProductionLocks` 为空时按空数组。
- 每条在产锁定必须有 `productionOrderNo` 和大于 0 的 `lockedQty`。
- `inStockLockedQty + sum(inProductionLocks.lockedQty)` 不得超过 `demandQty`。
- 覆盖不足时自动生成 `PRODUCTION_REQUIRED` 行。
- 覆盖为 0 时生成一条 `PRODUCTION_REQUIRED` 行，数量为 `demandQty`。
- WMS 返回 `materialNo` 与 QPO 当前 `material_no` 不一致时失败，不写入 allocation。

## 8. 数据写入规则

### 8.1 allocation 类型

| 类型 | 中文 | 必填字段 |
| --- | --- | --- |
| `IN_STOCK_LOCK` | 已入库库存锁定 | `ticket_id`、`qty`、`material_no` |
| `IN_PRODUCTION_LOCK` | 在产库存锁定 | `ticket_id`、`qty`、`material_no`、`wms_production_order_no` |
| `PRODUCTION_REQUIRED` | 待生产数量 | `ticket_id`、`qty`、`material_no` |

### 8.2 拆分算法

伪代码：

```text
demandQty = ticket.qty
inStockQty = max(0, response.inStockLockedQty)
productionLocks = response.inProductionLocks

validate each production lock:
  productionOrderNo not blank
  lockedQty > 0

coveredQty = inStockQty + sum(productionLocks.lockedQty)
if coveredQty > demandQty:
  fail

productionRequiredQty = demandQty - coveredQty

rows = []
if inStockQty > 0:
  rows.add(IN_STOCK_LOCK, inStockQty)
for lock in productionLocks:
  rows.add(IN_PRODUCTION_LOCK, lock.lockedQty, lock.productionOrderNo)
if productionRequiredQty > 0:
  rows.add(PRODUCTION_REQUIRED, productionRequiredQty)

assert sum(rows.qty) == demandQty
```

### 8.3 事务边界

WMS 成功响应后的数据库写入必须在一个事务中完成：

1. 锁定 `order_delivery_ticket`。
2. 再次校验 `wms_allocate_status` 不是 `SUCCESS`。
3. 校验 `material_no` 未变化。
4. 删除该 `ticket_id` 下历史非成功半成品 allocation，或严格保证失败不产生 allocation。
5. 插入 allocation 明细。
6. 更新 ticket WMS 状态为 `SUCCESS`。
7. 写操作日志成功摘要。

建议默认规则：

- 成功分配后不允许再次覆盖。
- 失败可重试。
- `PROCESSING` 时拒绝重复提交。
- 如果后续确实需要重新锁库，单独设计“管理员重算 WMS 分配”权限，不在本阶段做。

## 9. 后端设计

### 9.1 权限

新增权限：

| 权限编码 | 类型 | 父级 | 中文 |
| --- | --- | --- | --- |
| `market:delivered-item:wms-allocate` | `BUTTON/API` | `market:delivered-item:view` | 提交WMS锁库 |
| `market:locked-item:view` | `MENU` | 无 | 锁库零件 |

默认角色：

| 角色 | `market:delivered-item:wms-allocate` | `market:locked-item:view` |
| --- | --- | --- |
| `SUPER_ADMIN` | 是 | 是 |
| `MARKET_MANAGER` | 是 | 是 |
| `MARKET_USER` | 按业务默认是 | 是 |
| `READONLY` | 否 | 是 |
| `ENGINEER_USER` | 否 | 否 |

### 9.2 Controller

`OrderDeliveryController` 新增：

```java
@PostMapping("/tickets/{ticketId}/wms-allocation")
@RequirePermission("market:delivered-item:wms-allocate")
public Map<String, Object> submitWmsAllocation(@PathVariable Integer ticketId) {
    SystemTask task = systemTaskService.submitWmsAllocationTask(ticketId);
    return Map.of("success", true, "message", "WMS allocation submitted", "task", task);
}

@GetMapping("/locked-items")
@RequirePermission("market:locked-item:view")
public LockedItemPageDTO listLockedItems(LockedItemQueryReq req) {
    return lockedItemService.listAllocations(req);
}

@GetMapping("/tickets/{ticketId}/allocations")
@RequirePermission("market:locked-item:view")
public List<LockedItemDTO> listTicketAllocations(@PathVariable Integer ticketId) {
    return lockedItemService.listTicketAllocations(ticketId);
}
```

### 9.3 SystemTaskService

新增任务类型：

```text
WMS_ALLOCATION
```

新增方法：

```java
public SystemTask submitWmsAllocationTask(Integer ticketId)
```

行为：

- `ticketId` 为空时报业务错误。
- 校验提单行存在。
- 校验当前用户可访问该订单。
- `material_no` 为空时拒绝提交。
- `wms_allocate_status=SUCCESS` 时拒绝重复提交。
- `wms_allocate_status=PROCESSING` 时拒绝重复提交。
- 创建 `system_task`。
- 记录 `DELIVERY_WMS_ALLOCATION_SUBMIT`。
- 异步执行 `WmsAllocationService.allocate(ticketId, taskId)`。

### 9.4 WmsAllocationService

职责：

- 锁定 ticket。
- 组装 WMS 请求。
- 更新 ticket 状态为 `PROCESSING`。
- 调用 WMS。
- 校验并拆分返回结果。
- 写入 allocation。
- 更新 ticket 状态。
- 记录操作日志和文件日志。

请求前校验：

- `materialNo` 不能为空。
- `partNo` 不能为空。
- `internalProjectNo` 不能为空。
- `qty` 必须大于 0。
- `rfqId` 必须能查到订单。

成功摘要：

```json
{
  "ticketId": 123,
  "taskId": 456,
  "materialNo": "MAT-000123",
  "demandQty": 10,
  "allocationCount": 4,
  "inStockQty": 3,
  "inProductionQty": 6,
  "productionRequiredQty": 1
}
```

### 9.5 WmsAllocationClient

职责：

- 封装 HTTP 调用。
- 隐藏 token。
- 统一超时。
- 把 HTTP 错误转换为业务异常摘要。

要求：

- `ERP_WMS_BASE_URL` 为空时报配置错误。
- token 为空不发 Authorization。
- token 非空发送 Bearer。
- 2xx 但 `success=false` 返回失败响应，由业务服务标记失败。
- HTTP 非 2xx 抛异常。

### 9.6 LockedItemService

查询 `order_delivery_ticket_allocation`，join `order_rfq` 或使用 allocation 冗余字段。

必须支持：

- 分页。
- 客户筛选。
- RFQ 号筛选。
- 图号筛选。
- 版本筛选。
- 物料号筛选。
- 内部项目号筛选。
- 分配类型筛选。
- 在产订单号筛选。
- 创建时间范围筛选。
- 订单数据范围过滤。

默认排序：

```text
order_delivery_ticket_allocation.id DESC
```

## 10. 前端设计

### 10.1 已提零件页面

在 `DeliveredItemManage.vue` 增加 WMS 操作按钮：

| 行状态 | 按钮展示 |
| --- | --- |
| `materialNo` 为空 | 不显示或禁用“提交WMS锁库”，提示需先获取物料号。 |
| `wmsAllocateStatus=PENDING` 且有物料号 | 显示“提交WMS锁库”。 |
| `wmsAllocateStatus=FAILED` | 显示“重试WMS”。 |
| `wmsAllocateStatus=PROCESSING` | 显示“处理中”，按钮禁用。 |
| `wmsAllocateStatus=SUCCESS` | 不显示提交按钮，显示“查看锁库”。 |

按钮权限：

```vue
v-permission="'market:delivered-item:wms-allocate'"
```

确认文案：

```text
确认向 WMS 提交该零件锁库分配？
```

成功提示：

```text
WMS 锁库任务已提交，任务ID：{taskId}
```

### 10.2 锁库零件页面

新增 `LockedItemManage.vue`。

页面定位：查询 WMS 拆分结果。

筛选区：

- 客户
- RFQ 号
- 图号
- 版本
- 物料号
- 内部项目号
- 分配类型
- 在产订单号
- 创建时间范围

表格列：

| 列 | 要求 |
| --- | --- |
| 分配ID | 固定左侧。 |
| 提单ID | 可点击查看原始已提零件。 |
| RFQ号 | tooltip。 |
| 客户 | tooltip。 |
| 图号 | 靠前展示。 |
| 版本 | 空显示 `-`。 |
| 物料号 | tooltip。 |
| 分配类型 | tag 中文化。 |
| 数量 | 数字右对齐。 |
| 在产订单号 | 仅在产锁定有值，空显示 `-`。 |
| 内部项目号 | tooltip。 |
| 创建时间 | 日期时间。 |

分配类型 tag：

| 值 | 中文 | Tag 类型 |
| --- | --- | --- |
| `IN_STOCK_LOCK` | 已入库库存锁定 | `success` |
| `IN_PRODUCTION_LOCK` | 在产库存锁定 | `warning` |
| `PRODUCTION_REQUIRED` | 待生产数量 | `danger` |

### 10.3 API

`deliveredItemApi.js` 增加：

```js
export const submitWmsAllocation = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/wms-allocation`, {
  method: 'POST'
})

export const listLockedItems = (params = {}) => request('/api/order-delivery/locked-items', { params })

export const listTicketAllocations = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/allocations`)
```

### 10.4 路由和菜单

`router/index.js`：

```js
const LockedItemManage = () => import('../components/LockedItemManage.vue')

{ path: '/locked-items', component: LockedItemManage, meta: { permission: 'market:locked-item:view' } }
```

`App.vue`：

- `orders` 分组 paths 增加 `/locked-items`。
- `menuMetaByPath` 增加：

```js
'/locked-items': { title: '锁库零件', permission: 'market:locked-item:view', icon: Files }
```

- `shellMetaByPath` 增加：

```js
'/locked-items': {
  title: '锁库零件',
  description: '查看 WMS 返回后的库存锁定、在产锁定和待生产明细。'
}
```

## 11. 操作日志与文件日志

### 11.1 操作日志动作

新增动作：

| 动作 | 模块 | 目标 | 说明 |
| --- | --- | --- | --- |
| `DELIVERY_WMS_ALLOCATION_SUBMIT` | `DELIVERY` | `order_delivery_ticket` | 用户提交 WMS 锁库。 |
| `DELIVERY_WMS_ALLOCATION_SUCCESS` | `DELIVERY` | `order_delivery_ticket` | WMS 锁库分配成功。 |
| `DELIVERY_WMS_ALLOCATION_FAILED` | `DELIVERY` | `order_delivery_ticket` | WMS 锁库分配失败。 |

### 11.2 文件日志事件名

```text
WMS allocation submitted ticketId={} taskId={} rfqId={} materialNo={} demandQty={}
WMS allocation started ticketId={} taskId={} rfqId={} materialNo={} demandQty={}
WMS allocation completed ticketId={} taskId={} allocationCount={} inStockQty={} inProductionQty={} productionRequiredQty={} durationMs={}
WMS allocation rejected ticketId={} reason={}
WMS allocation failed ticketId={} taskId={} rfqId={} materialNo={} durationMs={}
```

要求：

- failed 必须 `log.error(..., error)`。
- 不记录 WMS token。
- INFO 只记录数量摘要，不打印完整 WMS 大 JSON。
- source_payload 可入库保存摘要，但操作日志不展示完整 payload。

## 12. 测试计划

### 12.1 后端测试

新增 `WmsAllocationTest`：

- 无物料号时拒绝提交。
- `SUCCESS` 状态拒绝重复提交。
- `PROCESSING` 状态拒绝重复提交。
- WMS 返回已入库 3、在产 4+2、需求 10 时生成 4 条 allocation。
- allocation 合计数量等于 ticket.qty。
- WMS 返回覆盖数量超过需求时失败，allocation 不写入。
- WMS 返回在产订单号为空时失败，allocation 不写入。
- WMS 返回 materialNo 与 QPO 不一致时失败。
- 成功后 ticket 状态为 `SUCCESS`。
- 失败后 ticket 状态为 `FAILED`。
- 操作日志包含 submit/success/failed。

新增 `WmsAllocationClientTest`：

- baseUrl 为空时报配置错误。
- token 为空不发送 Authorization。
- token 非空发送 Bearer。
- HTTP 500 转换为异常。
- 2xx 且 `success=false` 返回失败响应。
- 动态长度 `inProductionLocks` 可反序列化。

新增 `LockedItemQueryTest`：

- 支持分页查询 allocation。
- 支持按 `allocationType` 筛选。
- 支持按在产订单号筛选。
- 数据范围按客户生效。
- 无数据范围返回空列表。

扩展 `PermissionModuleTreeTest`：

- `market:delivered-item:wms-allocate` 挂在 `market:delivered-item:view` 下。
- `market:locked-item:view` 是独立 `MENU` 权限。
- `/locked-items` 有独立权限。

扩展 `AuditActionCoverageTests`：

- 三个 WMS 操作日志动作中文化。

### 12.2 前端测试

扩展 `deliveredItemApi.test.js`：

- `submitWmsAllocation(123)` 调用 `POST /api/order-delivery/tickets/123/wms-allocation`。
- `listLockedItems(params)` 调用 `/api/order-delivery/locked-items`。
- `listTicketAllocations(123)` 调用 `/api/order-delivery/tickets/123/allocations`。

新增 `deliveredItemWmsAllocation.test.js`：

- 有物料号且 `PENDING` 显示“提交WMS锁库”。
- `FAILED` 显示“重试WMS”。
- `PROCESSING` 禁用按钮。
- `SUCCESS` 显示“查看锁库”。
- 按钮带 `market:delivered-item:wms-allocate` 权限。

新增 `lockedItemManage.test.js`：

- 页面包含“锁库零件”。
- 页面包含筛选项：客户、RFQ号、图号、版本、物料号、内部项目号、分配类型、在产订单号。
- 表格包含分配类型、数量、在产订单号。
- 分配类型中文化。

## 13. 分阶段实施

### 阶段 0：测试护栏与口径冻结

目标：先冻结 WMS 分配算法、动态在产订单、权限和页面口径。

后端：

- 新增 `WmsAllocationTest`。
- 新增 `WmsAllocationClientTest`。
- 新增 `LockedItemQueryTest`。
- 扩展 `PermissionModuleTreeTest`。
- 扩展 `AuditActionCoverageTests`。

前端：

- 扩展 `deliveredItemApi.test.js`。
- 新增 `deliveredItemWmsAllocation.test.js`。
- 新增 `lockedItemManage.test.js`。

验收：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=WmsAllocationTest,WmsAllocationClientTest,LockedItemQueryTest,PermissionModuleTreeTest,AuditActionCoverageTests

cd D:\QPO-Operations-Management-System\quote-frontend
npm test -- deliveredItemApi.test.js deliveredItemWmsAllocation.test.js lockedItemManage.test.js permissionStage4.test.js appShell.test.js
```

### 阶段 1：权限迁移与配置

目标：系统认识 WMS 操作权限和锁库零件菜单。

后端：

- 新增 `V47__delivery_ticket_wms_allocation_permissions.sql`。
- 更新 `PermissionService` fallback catalog。
- 更新默认角色映射。
- 修改 `docker-compose.yml`。
- 修改 `erp.env`。

验收：

- 权限树中“已提零件”下出现“提交WMS锁库”。
- 权限树中订单分组出现“锁库零件”。
- 未授权用户后端返回 403。

### 阶段 2：WMS Client

目标：封装外部系统调用。

后端：

- 新增 `WmsAllocationReq`。
- 新增 `WmsAllocationResp`。
- 新增 `WmsAllocationClient`。

验收：

- 客户端测试覆盖动态在产订单数组。
- 不依赖真实 WMS。

### 阶段 3：WMS 分配服务与任务

目标：完成核心拆分逻辑。

后端：

- 新增 `WmsAllocationService`。
- 修改 `SystemTaskService` 增加 `submitWmsAllocationTask(ticketId)`。
- 修改 `OrderDeliveryController` 增加触发接口。
- 更新 `AuditService` 中文化。

验收：

- WMS 成功返回后生成正确 allocation。
- 数量超额时失败且不写入 allocation。
- 失败进入 `qpo-error.log`。

### 阶段 4：锁库零件查询接口

目标：提供结果查询能力。

后端：

- 新增 `LockedItemDTO`、`LockedItemPageDTO`、`LockedItemQueryReq`。
- 新增 `LockedItemService`。
- 修改 `OrderDeliveryController` 增加 allocation 查询接口。
- 接入数据范围。

验收：

- 锁库零件列表分页可用。
- 按客户、图号、物料号、分配类型、在产订单号筛选可用。
- 无数据范围返回空列表。

### 阶段 5：前端页面与交互

目标：用户可提交 WMS 并查看结果。

前端：

- 修改 `deliveredItemApi.js`。
- 修改 `DeliveredItemManage.vue`。
- 新增 `LockedItemManage.vue`。
- 修改 `router/index.js`。
- 修改 `App.vue`。

验收：

- 已提零件页面可提交 WMS。
- 锁库零件页面可查询分配结果。
- 无权限时菜单和按钮不可见。
- 直接访问无权限路由被拦截。

### 阶段 6：文档与回归

文档更新放到 `05-权限日志运维与回归收口开发文档.md` 统一处理。

本阶段验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

提交建议：

```powershell
git add docs/QPO-MES-WMS对接/04-WMS锁库分配与锁库零件模块开发文档.md `
  quote-backend/src/main/resources/db/migration/V47__delivery_ticket_wms_allocation_permissions.sql `
  quote-backend/src/main/java/com/machining/quote/dto/WmsAllocationReq.java `
  quote-backend/src/main/java/com/machining/quote/dto/WmsAllocationResp.java `
  quote-backend/src/main/java/com/machining/quote/dto/LockedItemDTO.java `
  quote-backend/src/main/java/com/machining/quote/dto/LockedItemPageDTO.java `
  quote-backend/src/main/java/com/machining/quote/service/WmsAllocationClient.java `
  quote-backend/src/main/java/com/machining/quote/service/WmsAllocationService.java `
  quote-backend/src/main/java/com/machining/quote/service/LockedItemService.java `
  quote-backend/src/main/java/com/machining/quote/service/SystemTaskService.java `
  quote-backend/src/main/java/com/machining/quote/controller/OrderDeliveryController.java `
  quote-backend/src/main/java/com/machining/quote/service/AuditService.java `
  quote-backend/src/main/java/com/machining/quote/service/PermissionService.java `
  docker-compose.yml `
  erp.env `
  quote-backend/src/test/java/com/machining/quote/WmsAllocationTest.java `
  quote-backend/src/test/java/com/machining/quote/WmsAllocationClientTest.java `
  quote-backend/src/test/java/com/machining/quote/LockedItemQueryTest.java `
  quote-frontend/src/services/deliveredItemApi.js `
  quote-frontend/src/components/DeliveredItemManage.vue `
  quote-frontend/src/components/LockedItemManage.vue `
  quote-frontend/src/router/index.js `
  quote-frontend/src/App.vue `
  quote-frontend/src/deliveredItemWmsAllocation.test.js `
  quote-frontend/src/lockedItemManage.test.js

git commit -m "feat: add wms allocation for delivered items"
```

## 14. Stage 20 锁库结果池收口说明

阶段 20 重构后，WMS 成功后结果进入锁库零件模块，锁库零件模块承担完成结果池展示职责。

- `order_delivery_ticket_allocation` 是锁库结果池，保存 WMS 拆分后的库存锁定、在产锁定和待生产明细。
- 原始 ticket 不删除，`order_delivery_ticket` 中的提单事实、MES/WMS 状态、物料号、来源订单和工艺快照继续保留。
- 完成行从已提零件默认列表隐藏，但仍可从锁库 allocation 通过 `ticketId` 反查原始 `order_delivery_ticket`。
- 锁库零件详情或操作入口应保留“查看提单行”或等价的 ticketId 跳转能力，确保 MES/WMS 状态、物料号、来源订单和工艺快照可追溯。
- 查询仍由后端分页完成，锁库结果页不全量拉取已提零件再本地拼接。

## 15. Stage 31 待产零件与锁库结果池拆分收口说明

阶段 31 后，`order_delivery_ticket_allocation` 仍是 WMS 拆分结果事实表，但页面口径拆成两套模块：锁库零件模块只展示 `IN_STOCK_LOCK` 和 `IN_PRODUCTION_LOCK`；`PRODUCTION_REQUIRED` 行进入待产零件模块 `/production-items`，用于 MES 生产下发工作池。

- `ticket_qty` 是原始提单数量快照，来源于 `order_delivery_ticket.qty`。
- `qty` 表示 allocation 拆分后的当前数量；对 `PRODUCTION_REQUIRED` 行来说，`qty` 就是待生产数量。
- `ticket_qty` 表示未锁库存前的原始提单数量，用于 MES 生产下发时让 MES 同时看到本次待生产数量和原始提单数量。
- `ticket_qty` 写入所有 allocation 行，不只写待生产行，便于后续审计和数量核对。
- 原始 ticket 不删除；锁库零件、待产零件、MES 生产下发结果和原始已提零件通过 `ticket_id` 保持完整溯源。
- 数量关系保持：已入库库存锁定数量 + 在产库存锁定数量 + 待生产数量 = `ticket_qty`。
- 锁库零件页面不展示待生产行，不提供 MES 生产下发按钮或下发权限。
- 待产零件页面调用 `POST /api/order-delivery/production-items/mes-production`，权限为 `market:production-item:mes-production-submit`，接口契约见 `docs/QPO-MES-WMS对接/09-提供给MES的待生产下发接口对接说明.md`。
## 16. 完成定义

- 已提零件可提交 WMS 锁库任务。
- WMS 动态多个在产订单可拆成多条 `IN_PRODUCTION_LOCK`。
- allocation 合计数量等于 ticket 原始需求数量。
- 锁库零件页面可查询分配结果。
- 权限树能独立授权 WMS 分配和锁库零件查看。
- 操作日志和文件日志可排查任务成功/失败。
- 全量后端测试、前端测试和前端 build 通过。
