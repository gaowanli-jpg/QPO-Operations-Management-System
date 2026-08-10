# QPO MES 物料号同步开发文档

> 更新说明：本文档记录的是 MES 物料号同步的第一版“同步响应”口径。后续已确认需要同时支持“即时返回物料号”和“受理后回调物料号”两种 MES 结果，详细设计以 `docs/QPO报价状态流转/18-MES物料号异步回调开发文档.md` 为准。实施阶段应保留本文档中已提零件、权限、日志和基础 MES 客户端能力，并按第 18 号文档扩展 `mes_request_id`、`CALLBACK_PENDING` 和回调接口。

> 阶段 5 收口说明：正式联调口径同时支持 **即时返回物料号** 和 **受理后回调物料号**。QPO 每次请求生成 `mesRequestId`，MES 响应和后续回调必须原样带回；回调接口为 `POST /api/integrations/mes/material-callback`。待回调响应下 `system_task` 按成功完成，提单行进入 `CALLBACK_PENDING`。本次不新增用户权限，继续使用 `market:delivered-item:mes-sync` 触发获取 / 重试；MES 回调接口使用机器 token，不挂用户权限。

> 阶段 6 最终收口说明：已确认物料号复用前移到 RFQ 重复件核价和销售订单导入自动快照补全，提单行继承确认结果。只有未确认行进入 MES；QPO 对同一 `图号 + 版本` 同一时间只保留一个活动主请求，后续行成为 follower，不再重复请求 MES。没有活动主请求时创建新的主请求，不查询其他历史成功提单行。MES 仍只回调主 `mesRequestId`，QPO 收到主请求即时成功或回调成功后负责 QPO fan-out 写回所有 follower。

> RFQ 主表物料号收口说明：MES 物料号成功后，QPO 必须同时写回提单行和来源零件主表。`source_type = RFQ` 时写回 `item_quote_version.material_no`、`material_no_status = 已确认`、来源 ticket 信息；`source_type = SALES_ORDER` 时写回 `sales_order_item.material_no`、`material_no_status = 已确认`、来源 ticket 信息。后续 U9 判断不再从提单行临时反查物料号，而是以 RFQ / 销售订单各自主表中的已确认物料号作为正式物料号基础；提单行保留为 MES 获取与追溯来源。

> 销售订单导入即提单阶段 7 收口：销售订单来源提单行由销售订单导入自动生成，不再要求市场用户手动销售匹配或手动议价提单。销售订单导入后直接进入已提零件需求池，已提零件向 MES 获取物料号成功后继续回写 `order_delivery_ticket` 和 `sales_order_item`。

> 阶段 22 收口说明：MES 对接现在包含两条独立业务 path：物料号获取继续使用 `ERP_MES_MATERIAL_PATH`，锁库零件待生产下发使用 `ERP_MES_PRODUCTION_PATH`。两者复用 `ERP_MES_BASE_URL`、`ERP_MES_AUTH_TOKEN` 和 `ERP_MES_TIMEOUT_MS`，但请求契约、权限点、操作日志和文件日志事件名彼此独立；待生产下发接口详见 `docs/QPO-MES-WMS对接/09-提供给MES的待生产下发接口对接说明.md`。

> 系统配置阶段 6 收口说明：QPO 通过数据中心获取 MES 物料号，出站配置由 **系统配置 > 出站接口/数据中心配置** 或 `ERP_DATA_CENTER_*` fallback 提供；数据中心调用 QPO 的入站配置由 **系统配置 > 入站接口 > MES 物料号回调** 管理，调用方为数据中心，业务来源为 MES。回调 URL 和机器 token 在页面生成、轮换、启停，真实 token 单独交付数据中心。`ERP_MES_CALLBACK_TOKEN` 与 `ERP_MES_CALLBACK_TOKEN_ENABLED` 仅作为页面配置不存在时的老环境 deprecated fallback，不作为给数据中心的主要交付变量。

> 数据中心统一对接阶段 8 收口：MES 物料号获取底层调用使用 `MES_MATERIAL_RESOLVE` endpoint，链路为 `QPO -> 数据中心 -> MES`。同图号版本去重仍由 QPO 负责，数据中心只需要转发主 `mesRequestId` 的 MES 结果；QPO 收到主请求即时成功或回调成功后负责 fan-out 写回 follower。

> 2026-08-08 两级工艺主数据阶段 15 收口：MES 物料号同步不重新计算工艺路线，也不记录完整 `processRoute` JSON；已提零件只携带提单时固化的标准工艺快照用于后续 WMS、待产、锁库和生产下发追溯。MES 生产下发的结构化工艺路线详见 `09-提供给MES的待生产下发接口对接说明.md`。

## 1. 背景

`02-基础数据模型与已提零件模块开发文档.md` 完成后，QPO 已经具备：

- `order_delivery_ticket.material_no` 字段。
- `mes_sync_status`、`mes_sync_message`、`mes_synced_at` 字段。
- “已提零件”页面和分页查询接口。

本开发文档在该基础上实现 MES 物料号同步：用户在“已提零件”页面主动触发，后端创建可追踪任务，调用 MES，根据返回结果写回 `order_delivery_ticket.material_no`。

## 2. 目标

### 2.1 本阶段目标

- 新增 MES 客户端配置和调用封装。
- 新增单条已提零件获取 MES 物料号接口。
- 使用 `system_task` 记录异步任务。
- 成功后写回 `material_no`、`mes_sync_status=SUCCESS`、`mes_synced_at`。
- 失败后写回 `mes_sync_status=FAILED`、`mes_sync_message`。
- 前端“已提零件”页面增加“获取物料号 / 重试”按钮。
- 新增并接入 `market:delivered-item:mes-sync` 权限。
- 操作日志和后端文件日志覆盖提交、开始、成功、失败。

### 2.2 非目标

- 不实现 WMS 锁库分配。
- 不生成 `order_delivery_ticket_allocation`。
- 不实现定时自动同步。
- 不实现批量合并成一个大任务。
- 不允许成功状态下无条件覆盖已有物料号。
- 不在日志中记录 MES token、图纸文件内容或完整大 JSON。

## 3. 前置条件

必须先完成 `02-基础数据模型与已提零件模块开发文档.md`：

- `order_delivery_ticket` 已有 MES 字段。
- “已提零件”页面已经存在。
- `market:delivered-item:view` 已接入。
- 已提零件查询接口已经接入数据范围。

## 4. 目标流程

```text
用户进入 已提零件
  -> 选择一条 material_no 为空或 MES 状态为 FAILED 的提单行
  -> 点击 获取物料号 / 重试
  -> 后端校验权限和数据范围
  -> 创建 system_task: MES_MATERIAL_SYNC
  -> ticket.mes_sync_status = PROCESSING
  -> 先按 图号 + 版本 判断是否存在活跃主请求
  -> 如果提单行已继承前置匹配确认物料号：保持 SUCCESS，不进入 MES 待处理池
  -> 如果已有活跃主请求：不调用 MES，当前行作为 FOLLOWER 等待主请求
  -> 如果没有活跃主请求：当前行成为 MASTER，异步调用 MES
  -> MES 情况 A：即时返回 materialNo
      -> ticket.material_no = materialNo
      -> ticket.mes_sync_status = SUCCESS
      -> ticket.mes_synced_at = now
      -> system_task.status = SUCCESS
  -> MES 情况 B：返回 callbackRequired=true / CALLBACK_PENDING
      -> ticket.mes_sync_status = CALLBACK_PENDING
      -> 保存 mesRequestId / MES 任务号
      -> system_task.status = SUCCESS
      -> MES 后续回调 QPO 写回 materialNo
  -> 页面轮询任务状态或刷新列表
```

失败流程：

```text
MES 调用异常 / 返回无物料号 / 请求字段不完整
  -> system_task.status = FAILED
  -> ticket.mes_sync_status = FAILED
  -> ticket.mes_sync_message = 失败摘要
  -> qpo-error.log 记录 taskId + ticketId + 异常堆栈
  -> 操作日志记录失败摘要
```

## 5. 文件清单

### 5.1 后端新增或修改

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 新增 | `quote-backend/src/main/resources/db/migration/V46__delivery_ticket_mes_sync_permission.sql` | 新增 MES 同步权限。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/service/PermissionService.java` | fallback catalog 和默认角色映射。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/dto/MesMaterialResolveReq.java` | QPO 调 MES 请求 DTO。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/dto/MesMaterialResolveResp.java` | MES 返回 DTO。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/service/MesMaterialClient.java` | MES HTTP 客户端。 |
| 新增 | `quote-backend/src/main/java/com/machining/quote/service/MesMaterialSyncService.java` | 物料号同步业务服务。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/service/SystemTaskService.java` | 增加 `submitMesMaterialSyncTask(ticketId)`。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/controller/OrderDeliveryController.java` | 增加触发接口。 |
| 修改 | `quote-backend/src/main/java/com/machining/quote/service/AuditService.java` | 增加 MES 动作中文化。 |
| 修改 | `docker-compose.yml` | 注入 MES 配置。 |
| 修改 | `erp.env` | 增加 MES 配置项。 |

### 5.2 前端新增或修改

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 修改 | `quote-frontend/src/services/deliveredItemApi.js` | 增加 `syncMesMaterial(ticketId)`。 |
| 修改 | `quote-frontend/src/components/DeliveredItemManage.vue` | 增加按钮、状态刷新、任务提示。 |
| 视情况修改 | `quote-frontend/src/services/taskApi.js` | 如已有任务 API 可复用则不新增。 |

### 5.3 测试和文档

| 类型 | 文件 | 说明 |
| --- | --- | --- |
| 新增 | `quote-backend/src/test/java/com/machining/quote/MesMaterialSyncTest.java` | MES 同步服务测试。 |
| 新增 | `quote-backend/src/test/java/com/machining/quote/MesMaterialClientTest.java` | MES 客户端契约测试。 |
| 修改 | `quote-backend/src/test/java/com/machining/quote/AuditActionCoverageTests.java` | 增加 MES 操作日志动作。 |
| 修改 | `quote-backend/src/test/java/com/machining/quote/PermissionModuleTreeTest.java` | 冻结 MES 权限。 |
| 修改 | `quote-frontend/src/deliveredItemApi.test.js` | 增加 sync API 测试。 |
| 新增 | `quote-frontend/src/deliveredItemMesSync.test.js` | 页面按钮和权限测试。 |
| 修改 | `docs/QPO权限用户体系/02-权限点清单与现有角色映射.md` | 增加 MES 同步权限。 |
| 修改 | `docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md` | 增加管理员授权说明。 |
| 修改 | `docs/QPO日志模块/03-日志运维说明.md` | 增加 MES 日志排查说明。 |
| 修改 | `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md` | 增加 MES 主流程事件命名。 |

## 6. 配置设计

### 6.1 `erp.env`

新增：

```env
ERP_MES_BASE_URL=http://mes.example.local
ERP_MES_AUTH_TOKEN=
ERP_MES_MATERIAL_PATH=/api/mes/materials/resolve
ERP_MES_PRODUCTION_PATH=/api/mes/production/submit
ERP_MES_TIMEOUT_MS=10000
ERP_MES_CALLBACK_TOKEN=
ERP_MES_CALLBACK_TOKEN_ENABLED=true
```

说明：

- 继续使用 `ERP_` 环境变量前缀，保持当前 QPO 部署兼容风格。
- token 可以为空；为空时不发送 Authorization header。
- 本地没有 MES 时，测试使用 mock，不依赖真实外部系统。
- 物料号获取继续使用 `ERP_MES_MATERIAL_PATH`；待生产下发使用 `ERP_MES_PRODUCTION_PATH`。
- QPO 获取 MES 物料号的出站配置优先读取系统配置页的数据中心配置；`ERP_MES_*` 直连变量仅作为应急兼容 fallback。
- 数据中心调用 QPO 的入站配置优先读取系统配置页 **入站接口 > MES 物料号回调**；`ERP_MES_CALLBACK_TOKEN` / `ERP_MES_CALLBACK_TOKEN_ENABLED` 仅作为页面配置不存在时的 deprecated fallback。

### 6.2 `docker-compose.yml`

backend 环境变量映射：

```yaml
ERP_MES_BASE_URL: ${ERP_MES_BASE_URL:-}
ERP_MES_AUTH_TOKEN: ${ERP_MES_AUTH_TOKEN:-}
ERP_MES_MATERIAL_PATH: ${ERP_MES_MATERIAL_PATH:-/api/mes/materials/resolve}
ERP_MES_PRODUCTION_PATH: ${ERP_MES_PRODUCTION_PATH:-/api/mes/production/submit}
ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-10000}
ERP_MES_CALLBACK_TOKEN: ${ERP_MES_CALLBACK_TOKEN:-}
ERP_MES_CALLBACK_TOKEN_ENABLED: ${ERP_MES_CALLBACK_TOKEN_ENABLED:-true}
```

Spring 配置读取建议：

```java
@Value("${erp.mes.base-url:}")
private String mesBaseUrl;

@Value("${erp.mes.auth-token:}")
private String mesAuthToken;

@Value("${erp.mes.material-path:/api/mes/materials/resolve}")
private String materialPath;

@Value("${erp.mes.production-path:/api/mes/production/submit}")
private String productionPath;

@Value("${erp.mes.timeout-ms:10000}")
private Integer timeoutMs;
```

如果当前项目没有统一把环境变量映射为 `erp.mes.*`，在 `docker-compose.yml` 中用 Java 系统属性或 Spring 环境变量命名规则补齐：

```yaml
ERP_MES_BASE_URL: ${ERP_MES_BASE_URL:-}
ERP_MES_AUTH_TOKEN: ${ERP_MES_AUTH_TOKEN:-}
ERP_MES_MATERIAL_PATH: ${ERP_MES_MATERIAL_PATH:-/api/mes/materials/resolve}
ERP_MES_PRODUCTION_PATH: ${ERP_MES_PRODUCTION_PATH:-/api/mes/production/submit}
ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-10000}
```

Spring Boot 会把 `ERP_MES_BASE_URL` 绑定到 `erp.mes.base-url`。
同理，`ERP_MES_PRODUCTION_PATH` 会绑定到 `erp.mes.production-path`，供锁库零件待生产下发客户端使用。

## 7. MES 接口契约

### 7.1 QPO 请求 MES

HTTP：

```text
POST {ERP_MES_BASE_URL}{ERP_MES_MATERIAL_PATH}
Content-Type: application/json
Authorization: Bearer {ERP_MES_AUTH_TOKEN}
```

当 `ERP_MES_AUTH_TOKEN` 为空时不发送 Authorization header。

请求体：

```json
{
  "mesRequestId": "MES-123-8f8c2",
  "ticketId": 123,
  "rfqId": 88,
  "rfqNo": "RFQ-20260721-001",
  "customerName": "ACME",
  "partNo": "P-1001",
  "version": "A",
  "drawingUrl": "http://qpo.example.com/uploads/xxx.pdf",
  "internalProjectNo": "IP-20260721-001"
}
```

字段规则：

| 字段 | 必填 | 来源 |
| --- | --- | --- |
| `mesRequestId` | 是 | QPO 每次请求生成的唯一 ID，MES 响应和回调必须原样返回。 |
| `ticketId` | 是 | `order_delivery_ticket.id` |
| `rfqId` | 是 | `order_delivery_ticket.rfq_id` |
| `rfqNo` | 是 | `order_rfq.rfq_no` |
| `customerName` | 是 | `order_rfq.customer_name` |
| `partNo` | 是 | `order_delivery_ticket.part_no` |
| `version` | 否 | `order_delivery_ticket.version`，空版本传空字符串。 |
| `drawingUrl` | 是 | `item_quote_version.drawing_url` 转绝对 URL。 |
| `internalProjectNo` | 是 | `order_delivery_ticket.internal_project_no` |

### 7.2 MES 返回 QPO

成功：

```json
{
  "success": true,
  "status": "SUCCESS",
  "materialNo": "MAT-000123",
  "message": "created",
  "externalTraceId": "MES-TRACE-001",
  "mesRequestId": "MES-123-8f8c2",
  "callbackRequired": false
}
```

已受理待回调：

```json
{
  "success": true,
  "status": "CALLBACK_PENDING",
  "callbackRequired": true,
  "message": "MES已受理，物料号生成后回调QPO",
  "externalTraceId": "MES-TASK-001",
  "mesRequestId": "MES-123-8f8c2"
}
```

失败：

```json
{
  "success": false,
  "message": "drawing file is missing",
  "externalTraceId": "MES-TRACE-002"
}
```

QPO 处理规则：

- `success=true` 且 `materialNo` 非空，判定成功。
- `success=true` 且 `callbackRequired=true`，或 `status=CALLBACK_PENDING / ACCEPTED / PENDING`，判定为待回调，写入 `CALLBACK_PENDING`，请求流水状态也写入 `CALLBACK_PENDING`。
- `success=false`，判定失败。
- HTTP 2xx、`materialNo` 为空且没有明确待回调标识时，判定失败。
- HTTP 非 2xx，判定失败。
- 超时，判定失败。
- `materialNo` 去除首尾空格后保存，长度超过 100 拒绝保存并标记失败。
- MES 返回待回调时，`system_task` 不长时间挂起，按成功完成；批量任务中该行计入 `callbackPendingCount`，不计入失败。
- 同图号版本去重后，批量任务返回 `dedupFollowerCount`、`submittedToMesCount`：分别表示关联活动主请求、未重复发送 MES 的 follower 数量，以及实际提交 MES 的主请求数量。

### 7.3 数据中心回调 QPO（业务来源 MES）

当 MES 先返回待回调后，后续通过以下接口写回物料号：

```text
POST /api/integrations/mes/material-callback
Content-Type: application/json
Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}
```

请求体：

```json
{
  "mesRequestId": "MES-123-8f8c2",
  "ticketId": 123,
  "status": "SUCCESS",
  "materialNo": "MAT-000123",
  "externalTraceId": "MES-TASK-001",
  "message": "MES completed"
}
```

QPO 使用 `mesRequestId` 定位请求流水，并用 `ticketId` 做交叉校验。成功回调写回 `order_delivery_ticket.material_no` 和 `mes_sync_status=SUCCESS`；RFQ 来源提单行同步回写 `item_quote_version.material_no` 与物料号确认字段，销售订单来源提单行同步回写 `sales_order_item.material_no` 与物料号确认字段。重复同一物料号回调幂等返回成功，不同物料号冲突回调拒绝覆盖。回调 token 由 QPO 管理员在 **系统配置 > 入站接口 > MES 物料号回调** 生成或轮换，明文只展示一次并通过受控渠道交付数据中心。

## 8. 后端设计

### 8.1 权限

权限：

阶段 5/阶段 6 不新增用户权限，继续使用既有权限：

| 权限编码 | 类型 | 父级 | 中文 |
| --- | --- | --- | --- |
| `market:delivered-item:mes-sync` | `BUTTON` / `API` | `market:delivered-item:view` | 获取MES物料号 |

MES 物料号回调接口不使用用户登录态，不进入权限树；数据中心调用该接口时通过入站接口配置中的机器 token 做认证。页面配置不存在时才兼容 `ERP_MES_CALLBACK_TOKEN` deprecated fallback。

默认角色：

| 角色 | 默认授权 |
| --- | --- |
| `SUPER_ADMIN` | 是 |
| `MARKET_MANAGER` | 是 |
| `MARKET_USER` | 按业务需要默认是 |
| `READONLY` | 否 |
| `ENGINEER_USER` | 否 |

### 8.2 Controller

`OrderDeliveryController` 新增：

```java
@PostMapping("/tickets/{ticketId}/mes-material")
@RequirePermission("market:delivered-item:mes-sync")
public Map<String, Object> syncMesMaterial(@PathVariable Integer ticketId) {
    SystemTask task = systemTaskService.submitMesMaterialSyncTask(ticketId);
    return Map.of("success", true, "message", "MES material sync submitted", "task", task);
}
```

接口路径：

```text
POST /api/order-delivery/tickets/{ticketId}/mes-material
```

### 8.3 SystemTaskService

新增任务类型：

```text
MES_MATERIAL_SYNC
```

新增方法：

```java
public SystemTask submitMesMaterialSyncTask(Integer ticketId)
```

行为：

- `ticketId` 为空时报业务错误。
- 查询并锁定或校验提单行存在。
- 通过 `DataScopeService.assertCanAccessOrder(ticket.getRfqId(), orderRfqMapper)` 校验数据范围。
- 已有 `material_no` 且 `mes_sync_status=SUCCESS` 时拒绝重复提交。
- `mes_sync_status=PROCESSING` 时拒绝重复提交。
- `mes_sync_status=CALLBACK_PENDING` 时拒绝重复提交，提示等待 MES 回调。
- 创建 `system_task`。
- 记录操作日志 `DELIVERY_MES_MATERIAL_SYNC_SUBMIT`。
- 异步执行 `MesMaterialSyncService.syncMaterialNo(ticketId, taskId)`。

### 8.4 MesMaterialSyncService

职责：

- 锁定 `order_delivery_ticket`。
- 调用 `MesMaterialDedupService`，先按 `图号 + 版本` 判断是否可复用、是否已有活跃主请求、是否需要创建主请求。
- 组装 MES 请求数据。
- 更新 ticket 状态。
- 调用 `MesMaterialClient`。
- 保存成功/失败结果。
- 记录操作日志和文件日志。

状态流：

```text
PENDING / FAILED -> PROCESSING -> SUCCESS
PENDING / FAILED -> PROCESSING -> FAILED
PENDING / FAILED -> PROCESSING -> CALLBACK_PENDING -> SUCCESS
SUCCESS -> 拒绝重复提交
PROCESSING -> 拒绝重复提交
CALLBACK_PENDING -> 拒绝重复提交
```

同图号版本去重状态：

```text
PENDING / FAILED -> REUSED -> SUCCESS
PENDING / FAILED -> FOLLOWER -> PROCESSING / CALLBACK_PENDING -> SUCCESS
PENDING / FAILED -> MASTER -> PROCESSING -> SUCCESS / CALLBACK_PENDING / FAILED
```

成功写入：

```text
material_no = MES 返回 materialNo
mes_sync_status = SUCCESS
mes_sync_message = MES message 或 "MES material resolved"
mes_synced_at = now
```

失败写入：

```text
mes_sync_status = FAILED
mes_sync_message = 失败摘要，最长建议 500 字符
mes_synced_at 不更新
```

待回调写入：

```text
mes_sync_status = CALLBACK_PENDING
mes_sync_message = MES message 或 "MES accepted, waiting for callback"
mes_request_id = QPO 本次请求 ID
mes_external_task_id = MES 返回的 externalTraceId
```

去重字段写入：

```text
mes_dedup_key = 图号 + 版本
mes_request_role = MASTER / FOLLOWER / REUSED
mes_master_ticket_id = FOLLOWER 关联的主提单行 ID
mes_master_request_id = FOLLOWER 关联的主 mesRequestId
```

主请求成功分发：

- MASTER 即时成功或回调成功时，QPO fan-out 写回所有 `order_delivery_ticket_mes_waiter` 等待行。
- 等待行写入同一个 `materialNo`，状态变为 `SUCCESS`。
- RFQ 来源等待行同步回写 `item_quote_version.material_no` 和 `material_no_status = 已确认`，销售订单来源等待行同步回写 `sales_order_item.material_no` 和 `material_no_status = 已确认`，因此后续 RFQ / 销售订单 U9 同步前都能从来源主表看到等待行物料号已确认。
- MES 仍只回调主 `mesRequestId`，不需要知道 QPO 内部有哪些关联等待行。

请求前校验：

- `partNo` 不能为空。
- `internalProjectNo` 不能为空。
- `drawingUrl` 不能为空。
- `rfqId` 必须能查到订单。
- `itemId` 必须能查到零件；历史缺 `itemId` 的提单行可以按 `rfqId + partNo + version` 查找零件，找不到则失败。

### 8.5 MesMaterialClient

建议实现：

```java
@Service
public class MesMaterialClient {
    public MesMaterialResolveResp resolveMaterial(MesMaterialResolveReq req) {
        // 使用 RestTemplate 或项目已有 HTTP 工具
    }
}
```

要求：

- baseUrl 为空时直接抛出业务异常：`MES base URL is not configured`。
- 请求超时使用 `ERP_MES_TIMEOUT_MS`。
- token 为空不发送 Authorization。
- token 非空使用 Bearer。
- 记录 DEBUG 级别请求摘要，不记录 token，不记录完整大 JSON。
- HTTP 错误转换为带状态码的异常摘要。

## 9. 前端设计

### 9.1 API

`deliveredItemApi.js` 增加：

```js
export const syncMesMaterial = (ticketId) => request(`/api/order-delivery/tickets/${ticketId}/mes-material`, {
  method: 'POST'
})
```

### 9.2 页面按钮

`DeliveredItemManage.vue` 操作列增加：

| 状态 | 按钮 |
| --- | --- |
| `materialNo` 为空且非 `PROCESSING` | 显示“获取物料号”。 |
| `mesSyncStatus=FAILED` | 显示“重试”。 |
| `mesSyncStatus=PROCESSING` | 显示“处理中”，按钮禁用。 |
| `mesSyncStatus=SUCCESS` | 不显示获取按钮。 |

按钮权限：

```vue
v-permission="'market:delivered-item:mes-sync'"
```

### 9.3 交互

- 点击前弹出二次确认。
- 提交成功后显示任务 ID。
- 提交成功后刷新当前列表。
- 如果已有任务列表能力，可提供“查看任务”入口；没有则只提示任务 ID。
- 失败提示统一使用 `ElMessage.error`。
- 不在前端判断数据权限，最终以后端 403/业务错误为准。

确认文案：

```text
确认向 MES 获取该零件的物料号？
```

成功提示：

```text
MES 物料号任务已提交，任务ID：{taskId}
```

## 10. 操作日志与文件日志

### 10.1 操作日志动作

新增动作：

| 动作 | 模块 | 目标 | 说明 |
| --- | --- | --- | --- |
| `DELIVERY_MES_MATERIAL_SYNC_SUBMIT` | `DELIVERY` | `order_delivery_ticket` | 用户提交任务。 |
| `DELIVERY_MES_MATERIAL_SYNC_SUCCESS` | `DELIVERY` | `order_delivery_ticket` | MES 返回物料号成功。 |
| `DELIVERY_MES_MATERIAL_SYNC_ACCEPTED` | `DELIVERY` | `order_delivery_ticket` | MES 已受理，等待回调。 |
| `DELIVERY_MES_MATERIAL_SYNC_FAILED` | `DELIVERY` | `order_delivery_ticket` | MES 获取失败。 |
| `DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS` | `DELIVERY` | `order_delivery_ticket` | MES 回调写回物料号成功。 |
| `DELIVERY_MES_MATERIAL_CALLBACK_FAILED` | `DELIVERY` | `order_delivery_ticket` | MES 回调失败或业务拒绝。 |
| `DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT` | `DELIVERY` | `order_delivery_ticket` | MES 回调物料号与已确认值冲突。 |

操作日志摘要：

```json
{
  "ticketId": 123,
  "taskId": 456,
  "mesRequestId": "MES-123-8f8c2",
  "rfqId": 88,
  "partNo": "P-1001",
  "version": "A",
  "internalProjectNo": "IP-20260721-001",
  "materialNo": "MAT-000123"
}
```

失败时摘要不记录完整异常堆栈，堆栈进入 `qpo-error.log`。

### 10.2 文件日志事件名

```text
MES material sync submitted ticketId={} taskId={} rfqId={} partNo={}
MES material sync started ticketId={} taskId={} rfqId={} partNo={}
MES material sync completed ticketId={} taskId={} rfqId={} partNo={} materialNo={} durationMs={}
MES material sync accepted ticketId={} taskId={} mesRequestId={} externalTraceId={} durationMs={}
MES material callback received ticketId={} mesRequestId={} externalTraceId={}
MES material callback completed ticketId={} mesRequestId={} materialNo={} durationMs={}
MES material callback rejected ticketId={} mesRequestId={} reason={}
MES material callback conflict ticketId={} mesRequestId={} existingMaterialNo={} incomingMaterialNo={}
MES material sync rejected ticketId={} reason={}
MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}
```

要求：

- submit/start/completed 用 INFO。
- rejected 用 WARN。
- failed 用 ERROR，并传异常对象。
- 失败必须能在 `qpo-error.log` 查到 `taskId` 和堆栈。
- 日志不记录 MES token、回调 token、Authorization header、图纸文件内容、完整请求 JSON、完整响应 JSON 或完整回调 JSON。

## 11. 测试计划

### 11.1 后端测试

新增 `MesMaterialSyncTest`：

- 无权限访问接口返回 403。
- 无数据权限提交返回业务错误。
- `ticketId` 不存在返回业务错误。
- `material_no` 已存在且状态成功时拒绝重复提交。
- `PROCESSING` 状态拒绝重复提交。
- 缺图纸时任务失败，ticket 状态为 `FAILED`。
- MES 返回 `materialNo` 后，ticket 状态为 `SUCCESS` 且物料号写入。
- MES 返回空物料号时，ticket 状态为 `FAILED`。
- 操作日志包含 submit/success/failed 动作。

新增 `MesMaterialClientTest`：

- baseUrl 为空时报配置错误。
- token 为空时不发送 Authorization。
- token 非空时发送 Bearer。
- HTTP 500 转换为明确异常。
- 2xx 但 `success=false` 返回失败响应。

扩展 `PermissionModuleTreeTest`：

- `market:delivered-item:mes-sync` 唯一。
- `market:delivered-item:mes-sync` 父级是 `market:delivered-item:view`。
- 授权树中位于“已提零件”下。

扩展 `AuditActionCoverageTests`：

- `DELIVERY_MES_MATERIAL_SYNC_SUBMIT` 中文化。
- `DELIVERY_MES_MATERIAL_SYNC_SUCCESS` 中文化。
- `DELIVERY_MES_MATERIAL_SYNC_ACCEPTED` 中文化。
- `DELIVERY_MES_MATERIAL_SYNC_FAILED` 中文化。
- `DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS` / `FAILED` / `CONFLICT` 中文化。
- 回调成功写回 `order_delivery_ticket`。
- RFQ 来源回调成功写回 `item_quote_version`；销售订单来源回调成功写回 `sales_order_item`。
- 重复回调幂等、冲突回调拒绝覆盖、旧 `mesRequestId` 回调不覆盖新请求。
- 回调 token 缺失 / 错误被拒绝。
- 批量任务待回调计数正确。

### 11.2 前端测试

扩展 `deliveredItemApi.test.js`：

- `syncMesMaterial(123)` 调用 `POST /api/order-delivery/tickets/123/mes-material`。

新增 `deliveredItemMesSync.test.js`：

- 页面包含 `market:delivered-item:mes-sync` 权限。
- `PENDING` 且无物料号时显示“获取物料号”。
- `FAILED` 时显示“重试”。
- `PROCESSING` 时按钮禁用。
- `CALLBACK_PENDING` 行显示“待回调”且按钮禁用。
- 批量获取文案包含“待MES回调的行会自动跳过”。
- `SUCCESS` 且已有物料号时不显示获取按钮。
- 提交成功后显示任务 ID。

## 12. 分阶段实施

### 阶段 0：测试护栏与权限冻结

目标：先冻结 MES 权限、接口、状态流和按钮显示规则。

后端：

- 新增 `MesMaterialSyncTest`。
- 新增 `MesMaterialClientTest`。
- 扩展 `PermissionModuleTreeTest`。
- 扩展 `AuditActionCoverageTests`。

前端：

- 扩展 `deliveredItemApi.test.js`。
- 新增 `deliveredItemMesSync.test.js`。

验收：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test -Dtest=MesMaterialSyncTest,MesMaterialClientTest,PermissionModuleTreeTest,AuditActionCoverageTests

cd D:\QPO-Operations-Management-System\quote-frontend
npm test -- deliveredItemApi.test.js deliveredItemMesSync.test.js
```

### 阶段 1：权限迁移与配置

目标：系统认识 MES 同步权限和 MES 配置。

后端：

- 新增 `V46__delivery_ticket_mes_sync_permission.sql`。
- 更新 `PermissionService` fallback catalog。
- 更新默认角色映射。
- 修改 `docker-compose.yml`。
- 修改 `erp.env`。

验收：

- 权限树中“已提零件”下出现“获取MES物料号”。
- 未授权用户不能调用接口。
- 配置项能被 Spring 读取。

### 阶段 2：MES Client

目标：封装外部系统调用，测试可替换。

后端：

- 新增 `MesMaterialResolveReq`。
- 新增 `MesMaterialResolveResp`。
- 新增 `MesMaterialClient`。
- 使用 `RestTemplate` 或项目现有 HTTP 客户端。

验收：

- 客户端单测覆盖成功、失败、超时配置、token header。
- 不依赖真实 MES 服务即可跑测试。

### 阶段 3：异步任务与业务服务

目标：完成后端主流程。

后端：

- 新增 `MesMaterialSyncService`。
- 修改 `SystemTaskService`，新增 `submitMesMaterialSyncTask(ticketId)`。
- 修改 `OrderDeliveryController`，新增触发接口。
- 更新 `AuditService` 中文化。
- 文件日志按事件名记录。

验收：

- 提交任务后 `system_task` 生成 `MES_MATERIAL_SYNC`。
- 成功后 `material_no` 写回。
- 失败后 ticket 状态为 `FAILED`。
- `qpo-error.log` 有 taskId 和异常堆栈。

### 阶段 4：前端按钮与状态刷新

目标：用户能在已提零件页面触发 MES 同步。

前端：

- 修改 `deliveredItemApi.js`。
- 修改 `DeliveredItemManage.vue`。
- 根据状态显示“获取物料号 / 重试 / 处理中”。
- 操作后刷新列表。

验收：

- 无权限时按钮不可见。
- PENDING 行可提交。
- FAILED 行可重试。
- PROCESSING 行不可重复提交。
- SUCCESS 行不显示提交按钮。

### 阶段 5：文档与日志收口

文档：

- 权限清单说明不新增用户权限，继续使用 `market:delivered-item:mes-sync`。
- 更新 `docs/QPO-MES-WMS对接/03-MES物料号同步开发文档.md`。
- 更新 `docs/QPO-MES-WMS对接/06-提供给MES的物料号接口对接说明.md`，旧同步响应口径已废弃。
- 更新 `docs/QPO日志模块/03-日志运维说明.md`。
- 更新 `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md`。

验收：

- 管理员知道阶段 5 不新增权限，仍授权已提零件下的“获取MES物料号”。
- 运维知道如何按 `taskId` / `requestId` / `mesRequestId` 查即时成功、待回调、回调成功、失败和冲突。
- MES 实施人员知道响应必须返回 `mesRequestId`，待回调后通过 QPO 回调接口写回物料号。

### 阶段 6：全量回归与提交

阶段 6 以第 19 号同图号版本去重文档为准，验收口径如下：

- `mvnw.cmd test` 通过。
- `npm test` 通过。
- `npm run build` 通过。
- 同批量相同 `图号 + 版本` 只打 MES 一次。
- 跨批次待回调时相同 `图号 + 版本` 不再打 MES。
- 跨批次没有活动主请求时，相同 `图号 + 版本` 创建新的主请求并调用 MES。
- 已确认物料号由 RFQ 重复件核价或销售订单导入自动快照补全在提单前复用，已提零件不扫描历史成功提单行。
- MES 回调一次可写回所有等待行。
- 销售订单 U9 同步前能看到等待行物料号已确认。

验收命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

阶段 6 提交只应包含文档、回归测试和必要的轻量前后端测试补充，不提交本地 `erp.env`。

## 13. 完成定义

- 已提零件页面可以触发 MES 物料号任务。
- MES 返回成功时，物料号写回 `order_delivery_ticket.material_no`。
- MES 返回待回调时，已提零件进入 `CALLBACK_PENDING`，后台任务成功完成。
- MES 回调成功时，物料号写回 `order_delivery_ticket.material_no`；RFQ 来源同步回写 `item_quote_version.material_no` 和确认状态，销售订单来源同步回写 `sales_order_item.material_no` 和确认状态。
- 同图号版本去重后，关联等待行不重复调用 MES；MES 仍只回调主 `mesRequestId`，QPO 负责分发写回等待行。
- MES 返回失败或异常时，状态和失败摘要可见。
- 操作日志可查提交、已受理待回调、即时成功、回调成功、失败和冲突。
- 文件日志可通过 requestId、taskId 和 mesRequestId 排查。
- 权限树中可以独立授权“获取MES物料号”。
- 不影响议价提单、订单管理、历史订单、报价处理主流程。
