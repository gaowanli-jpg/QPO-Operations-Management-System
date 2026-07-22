# QPO MES 物料号同步开发文档

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
  -> 异步调用 MES
  -> MES 返回 materialNo
  -> ticket.material_no = materialNo
  -> ticket.mes_sync_status = SUCCESS
  -> ticket.mes_synced_at = now
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
ERP_MES_TIMEOUT_MS=10000
```

说明：

- 继续使用 `ERP_` 环境变量前缀，保持当前 QPO 部署兼容风格。
- token 可以为空；为空时不发送 Authorization header。
- 本地没有 MES 时，测试使用 mock，不依赖真实外部系统。

### 6.2 `docker-compose.yml`

backend 环境变量映射：

```yaml
ERP_MES_BASE_URL: ${ERP_MES_BASE_URL:-}
ERP_MES_AUTH_TOKEN: ${ERP_MES_AUTH_TOKEN:-}
ERP_MES_MATERIAL_PATH: ${ERP_MES_MATERIAL_PATH:-/api/mes/materials/resolve}
ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-10000}
```

Spring 配置读取建议：

```java
@Value("${erp.mes.base-url:}")
private String mesBaseUrl;

@Value("${erp.mes.auth-token:}")
private String mesAuthToken;

@Value("${erp.mes.material-path:/api/mes/materials/resolve}")
private String materialPath;

@Value("${erp.mes.timeout-ms:10000}")
private Integer timeoutMs;
```

如果当前项目没有统一把环境变量映射为 `erp.mes.*`，在 `docker-compose.yml` 中用 Java 系统属性或 Spring 环境变量命名规则补齐：

```yaml
ERP_MES_BASE_URL: ${ERP_MES_BASE_URL:-}
ERP_MES_AUTH_TOKEN: ${ERP_MES_AUTH_TOKEN:-}
ERP_MES_MATERIAL_PATH: ${ERP_MES_MATERIAL_PATH:-/api/mes/materials/resolve}
ERP_MES_TIMEOUT_MS: ${ERP_MES_TIMEOUT_MS:-10000}
```

Spring Boot 会把 `ERP_MES_BASE_URL` 绑定到 `erp.mes.base-url`。

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
  "materialNo": "MAT-000123",
  "message": "created",
  "externalTraceId": "MES-TRACE-001"
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
- `success=false`，判定失败。
- HTTP 2xx 但 `materialNo` 为空，判定失败。
- HTTP 非 2xx，判定失败。
- 超时，判定失败。
- `materialNo` 去除首尾空格后保存，长度超过 100 拒绝保存并标记失败。

## 8. 后端设计

### 8.1 权限

新增权限：

| 权限编码 | 类型 | 父级 | 中文 |
| --- | --- | --- | --- |
| `market:delivered-item:mes-sync` | `BUTTON` / `API` | `market:delivered-item:view` | 获取MES物料号 |

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
- 创建 `system_task`。
- 记录操作日志 `DELIVERY_MES_MATERIAL_SYNC_SUBMIT`。
- 异步执行 `MesMaterialSyncService.syncMaterialNo(ticketId, taskId)`。

### 8.4 MesMaterialSyncService

职责：

- 锁定 `order_delivery_ticket`。
- 组装 MES 请求数据。
- 更新 ticket 状态。
- 调用 `MesMaterialClient`。
- 保存成功/失败结果。
- 记录操作日志和文件日志。

状态流：

```text
PENDING / FAILED -> PROCESSING -> SUCCESS
PENDING / FAILED -> PROCESSING -> FAILED
SUCCESS -> 拒绝重复提交
PROCESSING -> 拒绝重复提交
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
| `DELIVERY_MES_MATERIAL_SYNC_FAILED` | `DELIVERY` | `order_delivery_ticket` | MES 获取失败。 |

操作日志摘要：

```json
{
  "ticketId": 123,
  "taskId": 456,
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
MES material sync rejected ticketId={} reason={}
MES material sync failed ticketId={} taskId={} rfqId={} partNo={} durationMs={}
```

要求：

- submit/start/completed 用 INFO。
- rejected 用 WARN。
- failed 用 ERROR，并传异常对象。
- 失败必须能在 `qpo-error.log` 查到 `taskId` 和堆栈。
- 日志不记录 MES token、图纸文件内容、完整请求体。

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
- `DELIVERY_MES_MATERIAL_SYNC_FAILED` 中文化。

### 11.2 前端测试

扩展 `deliveredItemApi.test.js`：

- `syncMesMaterial(123)` 调用 `POST /api/order-delivery/tickets/123/mes-material`。

新增 `deliveredItemMesSync.test.js`：

- 页面包含 `market:delivered-item:mes-sync` 权限。
- `PENDING` 且无物料号时显示“获取物料号”。
- `FAILED` 时显示“重试”。
- `PROCESSING` 时按钮禁用。
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
cd D:\Code\quote-backend
.\mvnw.cmd test -Dtest=MesMaterialSyncTest,MesMaterialClientTest,PermissionModuleTreeTest,AuditActionCoverageTests

cd D:\Code\quote-frontend
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

- 更新 `docs/QPO权限用户体系/02-权限点清单与现有角色映射.md`。
- 更新 `docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md`。
- 更新 `docs/QPO日志模块/03-日志运维说明.md`。
- 更新 `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md`。

验收：

- 管理员知道如何给角色授权 MES 物料号获取。
- 运维知道如何按 taskId / requestId 查 MES 同步失败。

### 阶段 6：全量回归与提交

验收命令：

```powershell
cd D:\Code\quote-backend
.\mvnw.cmd test

cd D:\Code\quote-frontend
npm test
npm run build
```

提交建议：

```powershell
git add docs/QPO-MES-WMS对接/03-MES物料号同步开发文档.md `
  quote-backend/src/main/resources/db/migration/V46__delivery_ticket_mes_sync_permission.sql `
  quote-backend/src/main/java/com/machining/quote/dto/MesMaterialResolveReq.java `
  quote-backend/src/main/java/com/machining/quote/dto/MesMaterialResolveResp.java `
  quote-backend/src/main/java/com/machining/quote/service/MesMaterialClient.java `
  quote-backend/src/main/java/com/machining/quote/service/MesMaterialSyncService.java `
  quote-backend/src/main/java/com/machining/quote/service/SystemTaskService.java `
  quote-backend/src/main/java/com/machining/quote/controller/OrderDeliveryController.java `
  quote-backend/src/main/java/com/machining/quote/service/AuditService.java `
  quote-backend/src/main/java/com/machining/quote/service/PermissionService.java `
  docker-compose.yml `
  erp.env `
  quote-backend/src/test/java/com/machining/quote/MesMaterialSyncTest.java `
  quote-backend/src/test/java/com/machining/quote/MesMaterialClientTest.java `
  quote-backend/src/test/java/com/machining/quote/AuditActionCoverageTests.java `
  quote-backend/src/test/java/com/machining/quote/PermissionModuleTreeTest.java `
  quote-frontend/src/services/deliveredItemApi.js `
  quote-frontend/src/components/DeliveredItemManage.vue `
  quote-frontend/src/deliveredItemApi.test.js `
  quote-frontend/src/deliveredItemMesSync.test.js `
  docs/QPO权限用户体系/02-权限点清单与现有角色映射.md `
  docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md `
  docs/QPO日志模块/03-日志运维说明.md `
  docs/QPO日志模块/04-主流程文件日志规范化开发文档.md

git commit -m "feat: add mes material sync for delivered items"
```

## 13. 完成定义

- 已提零件页面可以触发 MES 物料号任务。
- MES 返回成功时，物料号写回 `order_delivery_ticket.material_no`。
- MES 返回失败或异常时，状态和失败摘要可见。
- 操作日志可查提交、成功、失败。
- 文件日志可通过 requestId 和 taskId 排查。
- 权限树中可以独立授权“获取MES物料号”。
- 不影响议价提单、订单管理、历史订单、报价处理主流程。
