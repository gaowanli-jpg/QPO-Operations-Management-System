# QPO MES 物料号异步回调开发文档

最后更新：2026-08-01

> 2026-08-10 收口：已确认物料号由 RFQ 重复件核价或销售订单导入自动快照补全在前置阶段复用，提单行直接继承。只有来源主表尚未确认物料号的提单行才进入 MES；MES 去重只查询同一 `图号 + 版本` 的活动主请求，后续行作为 follower 等待，QPO fan-out 写回结果，不扫描其他历史成功提单行。

> 2026-08-08 两级工艺主数据阶段 15 收口：MES 物料号同步链路不重新读取工艺字典，不重算工艺路线；它只负责物料号状态和来源主表回写。提单行已有的标准工艺快照继续传递给 WMS、待产、锁库和 MES 生产下发链路。

## 1. 背景与结论

当前 **已提零件** 模块已经支持用户单条或批量点击 **获取物料号**，后端创建 `MES_MATERIAL_SYNC` / `MES_MATERIAL_BATCH_SYNC` 异步任务，并调用 MES 获取正式物料号。现有实现默认 MES 必须在同一次 HTTP response 中返回 `materialNo`；如果 MES 返回成功但没有物料号，QPO 会按失败处理。

MES 侧实际会存在两种处理结果：

1. **即时返回物料号**：MES 已存在或可以立即生成物料号，在 HTTP response 中直接返回 `materialNo`。
2. **受理后回调物料号**：MES 当前没有物料号，需要形成 MES 内部任务并由工作人员或系统后续补齐；MES 在 HTTP response 中返回“已受理 / 后续回调”，QPO 将已提零件标记为 **待MES回调**。MES 后续通过 QPO 回调接口返回正式物料号，QPO 再写回已提零件和销售订单零件。

核心结论：

- QPO 生成的 `mes_request_id` 是本次获取物料号请求的 **主唯一标识**。
- `order_delivery_ticket.id` / `ticketId` 是 QPO 已提零件行 ID，用于业务定位和辅助校验。
- MES 返回的 `mes_external_task_id` / `externalTraceId` 是 MES 侧任务号或流水号，用于排查和对账，不作为 QPO 主键。
- MES 即时响应和 MES 后续回调都必须原样带回 `mes_request_id`。
- QPO 系统任务不应长时间挂起等待 MES 人工处理；当 MES 已受理但无物料号时，QPO 任务应正常结束，提单行进入 `CALLBACK_PENDING`。
- 回调成功后，`order_delivery_ticket.material_no` 写入正式物料号；如果提单行来源为销售订单，还必须同步回写 `sales_order_item.material_no` 和物料号确认状态，供后续 U9 同步使用。

最终链路：

```text
已提零件点击 获取物料号
  -> QPO 生成 mes_request_id
  -> QPO 调 MES
  -> MES 情况 A：立即返回 materialNo
      -> QPO 写 order_delivery_ticket.material_no
      -> 销售订单来源同步回写 sales_order_item.material_no
      -> 状态 SUCCESS
  -> MES 情况 B：返回已受理，后续回调
      -> QPO 记录 mes_request_id / mes_external_task_id
      -> 状态 CALLBACK_PENDING
      -> QPO system_task 完成
      -> MES 后续回调 QPO
      -> QPO 按 mes_request_id 定位请求并校验 ticketId
      -> 写回 materialNo
      -> 状态 SUCCESS
```

## 2. 目标

- 扩展 MES 物料号同步链路，支持 **即时响应** 和 **后续回调** 两种结果。
- 使用 QPO 生成的 `mes_request_id` 作为请求级主唯一标识。
- 新增 `CALLBACK_PENDING` / `待MES回调` 状态。
- 新增 MES 回调接口，用于 MES 后续写回物料号。
- 回调写回必须幂等，重复回调不能重复污染数据。
- 旧回调、错回调、物料号冲突必须被拒绝或安全忽略。
- 销售订单来源提单行回调成功后，同步回写 `sales_order_item.material_no`、`material_no_status = 已确认`、来源提单行和回写时间。
- 已提零件页面支持展示、筛选和解释 `待MES回调` 状态。
- 日志和操作审计覆盖提交、受理待回调、即时成功、回调成功、失败和冲突。
- 更新提供给 MES 的接口说明，明确 MES 需要回传 `mesRequestId`。

## 3. 非目标

- 不在已提零件模块直接同步 U9。
- 不改变 U9 同步口径；U9 仍只读取 `sales_order_header` / `sales_order_item` 中已确认的物料号。
- 不让 QPO 后台任务等待 MES 人工处理数小时或数天。
- 不新增左侧菜单。
- 不新增用户可分配的公共 `common:*` 权限。
- 不在日志中记录 MES token、Authorization header、完整请求体、完整回调体或图纸文件内容。
- 不允许回调覆盖已确认但不一致的物料号。

## 4. 唯一标识设计

### 4.1 标识含义

| 标识 | 生成方 | 是否主唯一标识 | 含义 | 用途 |
| --- | --- | --- | --- | --- |
| `mes_request_id` | QPO | 是 | QPO 发起的一次获取物料号请求 | 响应和回调的主定位键、幂等键、防旧回调覆盖 |
| `ticketId` | QPO | 否 | QPO 已提零件行 ID，即 `order_delivery_ticket.id` | 业务对象定位、回调辅助校验、页面排查 |
| `mes_external_task_id` / `externalTraceId` | MES | 否 | MES 侧任务号或流水号 | MES 排查、双方对账、运维追踪 |
| `system_task.id` | QPO | 否 | QPO 后台任务 ID | QPO 内部任务追踪，不要求 MES 回传 |

### 4.2 推荐规则

- QPO 调 MES 前生成 `mes_request_id`，推荐格式：`MES-{yyyyMMddHHmmss}-{ticketId}-{random}` 或 UUID。
- 同一次 QPO 请求的 `mes_request_id` 必须全局唯一。
- MES 即时响应和后续回调都必须原样带回 `mes_request_id`。
- MES 回调建议同时带回 `ticketId`，QPO 用它做交叉校验。
- MES 生成的任务号保存为 `mes_external_task_id`，但 QPO 不依赖它做主定位。
- 如果用户对失败行重试，QPO 生成新的 `mes_request_id`；旧请求后续回调不能覆盖新请求或已确认物料号。

### 4.3 为什么不用 `ticketId` 作为唯一标识

`ticketId` 表示已提零件行，不表示某一次外部请求。同一个已提零件可能出现以下情况：

- 用户对失败任务重试。
- MES 接口超时，但 MES 侧实际已受理。
- MES 先返回待回调，用户后续又触发重试。
- 老请求的回调晚于新请求返回。

如果只用 `ticketId`，旧回调可能覆盖新结果。使用 `mes_request_id` 可以区分“哪一次请求产生的结果”，更安全。

## 5. 数据模型设计

### 5.1 `order_delivery_ticket` 扩展字段

新增迁移，例如：

```text
Vxx__delivery_ticket_mes_callback.sql
```

实际编号以当前下一 Flyway 迁移编号为准。

建议给 `order_delivery_ticket` 增加以下字段，用于当前状态和页面展示：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `mes_request_id` | `VARCHAR(80)` | 当前最新 MES 物料号请求 ID，由 QPO 生成。 |
| `mes_external_task_id` | `VARCHAR(120)` | MES 返回的外部任务号 / 流水号。 |
| `mes_callback_received_at` | `TIMESTAMP` | 最近一次有效回调接收时间。 |
| `mes_callback_message` | `TEXT` | 最近一次回调摘要，避免保存完整回调体。 |

索引建议：

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_order_delivery_ticket_mes_request_id
    ON order_delivery_ticket(mes_request_id)
    WHERE mes_request_id IS NOT NULL AND TRIM(mes_request_id) <> '';

CREATE INDEX IF NOT EXISTS idx_order_delivery_ticket_mes_external_task
    ON order_delivery_ticket(mes_external_task_id, id);
```

`mes_sync_status` 沿用现有字段，扩展状态值：

| 状态 | 中文 | 含义 |
| --- | --- | --- |
| `PENDING` | 未获取 | 尚未提交 MES。 |
| `PROCESSING` | 处理中 | QPO 任务正在调用 MES。 |
| `CALLBACK_PENDING` | 待MES回调 | MES 已受理但没有即时返回物料号。 |
| `SUCCESS` | 已获取 | QPO 已取得并保存正式物料号。 |
| `FAILED` | 获取失败 | MES 调用失败、响应失败或回调失败。 |

### 5.2 MES 请求流水表

为了支持重复请求、旧回调识别和审计追踪，建议新增请求流水表：

```sql
CREATE TABLE IF NOT EXISTS order_delivery_ticket_mes_request (
    id BIGSERIAL PRIMARY KEY,
    ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    mes_request_id VARCHAR(80) NOT NULL,
    system_task_id BIGINT,
    request_status VARCHAR(30) NOT NULL DEFAULT 'PROCESSING',
    mes_external_task_id VARCHAR(120),
    material_no VARCHAR(100),
    request_message TEXT,
    response_message TEXT,
    callback_message TEXT,
    requested_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    responded_at TIMESTAMP,
    callback_received_at TIMESTAMP,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_mes_request_request_id
    ON order_delivery_ticket_mes_request(mes_request_id);

CREATE INDEX IF NOT EXISTS idx_delivery_mes_request_ticket
    ON order_delivery_ticket_mes_request(ticket_id, id DESC);

CREATE INDEX IF NOT EXISTS idx_delivery_mes_request_external_task
    ON order_delivery_ticket_mes_request(mes_external_task_id, id);
```

请求流水表状态建议：

| 状态 | 含义 |
| --- | --- |
| `PROCESSING` | QPO 已提交 MES，请求处理中。 |
| `CALLBACK_PENDING` | MES 已受理，等待后续回调。 |
| `SUCCESS` | 已通过即时响应或回调取得物料号。 |
| `FAILED` | 本次请求失败。 |
| `STALE` | 本次请求已不是该 ticket 的最新请求，后续回调不得覆盖当前结果。 |
| `IGNORED` | 重复回调或无效回调已安全忽略。 |

### 5.3 同图号版本去重引用

第 19 号文档 `19-MES物料号同图号版本去重开发文档.md` 在本回调模型之上补充同图号版本去重能力。去重键 `dedupKey` 固定为 **图号 + 版本**，不区分客户、订单、图纸文件或内部项目号。

阶段 1 新增 `order_delivery_ticket_mes_waiter` 用于记录 follower 等待关系。MES 只回调主请求，QPO fan-out 写回等待行：

```text
同图号版本 ticket
  -> 一个 MASTER 真实调用 MES
  -> 其他 FOLLOWER 记录到 order_delivery_ticket_mes_waiter
  -> MES 只回调主请求 mesRequestId
  -> QPO fan-out 写回等待行
```

原 `mes_request_id` 仍只表示当前 ticket 自己真实发起的 MES 请求；follower 行不得共用主请求的 `mes_request_id`，只能通过 `mes_master_request_id` 和等待表追溯主请求。

当前去重边界只包含活动主请求和 follower：没有活动主请求时创建新的主请求，并由数据库唯一约束兜底并发；存在活动主请求时建立 follower。历史成功提单行不参与本阶段查询，历史确认物料号由前置匹配和提单继承负责。

### 5.4 销售订单物料号回写字段

销售订单来源提单行已经通过 `source_type = 'SALES_ORDER'`、`source_order_id`、`source_item_id` 指回独立销售订单主表 / 零件表。

MES 获取成功后必须继续维护：

| 表 | 字段 | 规则 |
| --- | --- | --- |
| `sales_order_item` | `material_no` | 写入 MES 返回的正式物料号。 |
| `sales_order_item` | `material_no_status` | 写入 `已确认`。 |
| `sales_order_item` | `material_no_source` | 写入 `MES_TICKET`。 |
| `sales_order_item` | `material_no_source_ticket_id` | 写入 `order_delivery_ticket.id`。 |
| `sales_order_item` | `material_no_synced_at` | 写入当前时间。 |
| `sales_order_item` | `material_no_message` | 写入低噪声摘要。 |

RFQ 来源提单行只更新 `order_delivery_ticket`，不写 `sales_order_item`。

## 6. MES 接口契约

### 6.1 QPO 请求 MES

QPO 调 MES 请求必须新增 `mesRequestId`。

```http
POST {ERP_MES_BASE_URL}{ERP_MES_MATERIAL_PATH}
Content-Type: application/json
Authorization: Bearer {ERP_MES_AUTH_TOKEN}
```

请求示例：

```json
{
  "mesRequestId": "MES-20260801153000-123-a8f8c2",
  "ticketId": 123,
  "sourceType": "SALES_ORDER",
  "sourceOrderId": 56,
  "sourceItemId": 789,
  "orderNo": "SO-20260801-001",
  "customerName": "客户A",
  "partNo": "P-1001",
  "version": "A",
  "drawingUrl": "http://192.168.70.21:8088/uploads/drawings/xxx.pdf",
  "internalProjectNo": "IP-20260801-001"
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `mesRequestId` | 是 | QPO 生成的请求唯一标识，MES 响应和回调必须原样返回。 |
| `ticketId` | 是 | QPO 已提零件行 ID。 |
| `sourceType` | 是 | `RFQ` 或 `SALES_ORDER`。 |
| `sourceOrderId` | 否 | 来源订单 ID；销售订单来源时为 `sales_order_header.id`。 |
| `sourceItemId` | 否 | 来源零件 ID；销售订单来源时为 `sales_order_item.id`。 |
| `orderNo` | 否 | RFQ 编号或销售订单号，用于 MES 展示和排查。 |
| `customerName` | 否 | 客户名称。 |
| `partNo` | 是 | 图号。 |
| `version` | 否 | 版本，无版本时传空字符串。 |
| `drawingUrl` | 是 | MES 可访问的图纸绝对地址。 |
| `internalProjectNo` | 是 | 内部项目号。 |

兼容说明：

- 第一版可保留旧字段 `rfqId`、`rfqNo`，但新字段 `sourceType`、`sourceOrderId`、`sourceItemId` 应作为后续统一字段。
- MES 侧必须保存或原样透传 `mesRequestId`，用于后续回调。

### 6.2 MES 即时成功响应

```json
{
  "success": true,
  "status": "SUCCESS",
  "materialNo": "MAT-000123",
  "message": "物料号创建成功",
  "mesRequestId": "MES-20260801153000-123-a8f8c2",
  "externalTraceId": "MES-TASK-001"
}
```

QPO 判定成功条件：

- HTTP 2xx。
- `success = true`。
- `materialNo` 非空。
- `mesRequestId` 等于 QPO 发出的请求 ID。
- `materialNo` 去除首尾空格后长度不超过 100。

### 6.3 MES 已受理待回调响应

```json
{
  "success": true,
  "status": "CALLBACK_PENDING",
  "callbackRequired": true,
  "materialNo": null,
  "message": "MES已受理，物料号生成后回调QPO",
  "mesRequestId": "MES-20260801153000-123-a8f8c2",
  "externalTraceId": "MES-TASK-002"
}
```

QPO 判定待回调条件：

- HTTP 2xx。
- `success = true`。
- `materialNo` 为空。
- `callbackRequired = true`，或 `status` 为 `ACCEPTED` / `PENDING` / `CALLBACK_PENDING`。
- `mesRequestId` 等于 QPO 发出的请求 ID。

处理结果：

- `order_delivery_ticket.mes_sync_status = CALLBACK_PENDING`。
- 保存 `mes_request_id`、`mes_external_task_id`、`mes_sync_message`。
- 请求流水表状态为 `CALLBACK_PENDING`。
- QPO `system_task` 按成功完成，结果摘要为“MES已受理，待回调”。

### 6.4 MES 失败响应

```json
{
  "success": false,
  "status": "FAILED",
  "message": "图纸无法识别，缺少材料信息",
  "mesRequestId": "MES-20260801153000-123-a8f8c2",
  "externalTraceId": "MES-TASK-003"
}
```

QPO 判定失败条件：

- HTTP 非 2xx。
- 连接超时或读取超时。
- 响应体为空。
- `success != true`。
- `success = true` 但既没有 `materialNo`，也没有明确 `callbackRequired = true` / 待回调状态。
- `mesRequestId` 缺失或不等于 QPO 发出的请求 ID。
- `materialNo` 长度超过 100。

失败后：

- `order_delivery_ticket.mes_sync_status = FAILED`。
- 请求流水表状态为 `FAILED`。
- 用户可重试，重试会生成新的 `mes_request_id`。

### 6.5 MES 回调 QPO

新增 QPO 接收回调接口：

```http
POST /api/integrations/mes/material-callback
Content-Type: application/json
Authorization: Bearer {ERP_MES_CALLBACK_TOKEN}
```

回调请求示例：

```json
{
  "mesRequestId": "MES-20260801153000-123-a8f8c2",
  "ticketId": 123,
  "externalTraceId": "MES-TASK-002",
  "status": "SUCCESS",
  "materialNo": "MAT-000456",
  "message": "物料号已维护完成"
}
```

字段说明：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `mesRequestId` | 是 | QPO 请求 ID，主定位键。 |
| `ticketId` | 是 | QPO 已提零件行 ID，用于辅助校验。 |
| `externalTraceId` | 否 | MES 侧任务号 / 流水号。 |
| `status` | 是 | `SUCCESS` 或 `FAILED`。 |
| `materialNo` | 成功时必填 | 正式物料号。 |
| `message` | 否 | 回调摘要。 |

回调响应示例：

```json
{
  "success": true,
  "message": "QPO已接收物料号回调",
  "ticketId": 123,
  "mesRequestId": "MES-20260801153000-123-a8f8c2"
}
```

## 7. 后端设计

### 7.1 DTO 调整

修改：

```text
quote-backend/src/main/java/com/machining/quote/dto/mes/MesMaterialResolveReq.java
quote-backend/src/main/java/com/machining/quote/dto/mes/MesMaterialResolveResp.java
```

请求 DTO 新增：

```java
private String mesRequestId;
private String sourceType;
private Long sourceOrderId;
private Long sourceItemId;
private String orderNo;
```

响应 DTO 新增：

```java
private String mesRequestId;
private String status;
private Boolean callbackRequired;
private String externalTraceId;
```

新增回调 DTO：

```text
quote-backend/src/main/java/com/machining/quote/dto/mes/MesMaterialCallbackReq.java
quote-backend/src/main/java/com/machining/quote/dto/mes/MesMaterialCallbackResp.java
```

### 7.2 Mapper / Entity 调整

修改：

```text
quote-backend/src/main/java/com/machining/quote/entity/OrderDeliveryTicket.java
quote-backend/src/main/java/com/machining/quote/mapper/OrderDeliveryTicketMapper.java
```

新增：

```text
quote-backend/src/main/java/com/machining/quote/entity/OrderDeliveryTicketMesRequest.java
quote-backend/src/main/java/com/machining/quote/mapper/OrderDeliveryTicketMesRequestMapper.java
```

核心能力：

- 按 `mes_request_id` 查请求流水。
- 按 `ticket_id` 查最新请求。
- 插入请求流水。
- 更新请求状态、响应摘要、回调摘要、物料号。
- 条件更新 ticket 状态，防止状态被并发覆盖。

### 7.3 `MesMaterialSyncService` 调整

现有 `syncMaterialNo(ticketId, taskId)` 改为支持三种结果：

```text
即时成功       -> SUCCESS
MES已受理待回调 -> CALLBACK_PENDING
失败           -> FAILED
```

处理步骤：

1. 校验提单行存在、数据权限、图号、图纸、内部项目号等必填。
2. 如果 `mes_sync_status = SUCCESS` 且 `material_no` 非空，拒绝重复获取。
3. 如果 `mes_sync_status = PROCESSING` 或 `CALLBACK_PENDING`，拒绝重复获取。
4. 生成 `mes_request_id`。
5. 插入 `order_delivery_ticket_mes_request`，状态 `PROCESSING`。
6. 更新 `order_delivery_ticket.mes_sync_status = PROCESSING`，写入当前 `mes_request_id`。
7. 调用 MES。
8. 解析响应：
   - `materialNo` 非空：调用统一成功写回方法。
   - 明确待回调：写 `CALLBACK_PENDING`，system_task 成功完成。
   - 失败：写 `FAILED`。
9. 文件日志只记录摘要，不记录完整请求体和 token。

### 7.4 成功写回方法抽取

新增或抽取统一方法：

```java
confirmMaterialNo(ticketId, mesRequestId, materialNo, externalTraceId, message, source)
```

该方法同时服务于：

- MES 即时响应成功。
- MES 后续回调成功。

写回规则：

- 锁定或条件更新 `order_delivery_ticket`。
- 校验 `mes_request_id` 与请求流水匹配。
- 如果 ticket 已 `SUCCESS` 且物料号相同，按幂等成功返回。
- 如果 ticket 已 `SUCCESS` 且物料号不同，拒绝覆盖并记录冲突。
- 写入 `order_delivery_ticket.material_no`。
- 写入 `mes_sync_status = SUCCESS`。
- 写入 `mes_synced_at = now`。
- 写入 `mes_callback_received_at`，如果来源是回调。
- 更新请求流水 `SUCCESS`。
- 如果 `source_type = SALES_ORDER` 且 `source_item_id` 非空，同步回写 `sales_order_item` 物料号确认字段。

### 7.5 回调 Controller

新增：

```text
quote-backend/src/main/java/com/machining/quote/controller/MesIntegrationCallbackController.java
```

接口：

```text
POST /api/integrations/mes/material-callback
```

说明：

- 该接口是 MES 系统调用 QPO 的机器接口，不挂用户菜单权限。
- 必须做机器认证，例如 `Authorization: Bearer {ERP_MES_CALLBACK_TOKEN}`。
- 也可以叠加 IP 白名单，但第一版不强制。
- 校验失败返回 401 / 403。
- 业务冲突返回 409。
- 幂等重复成功返回 200。

### 7.6 配置

新增配置：

```env
ERP_MES_CALLBACK_TOKEN=
ERP_MES_CALLBACK_TOKEN_ENABLED=true
```

如果系统对接配置页面已承载 MES 配置，后续可以扩展入库配置：

| 配置 | 说明 |
| --- | --- |
| `callbackToken` | MES 调 QPO 回调接口使用的 token。 |
| `callbackTokenEnabled` | 是否启用回调 token 校验。 |

第一版可以先使用 `erp.env`，但不得在日志和前端展示 token 明文。

### 7.7 批量任务处理

`DeliveryIntegrationBatchService.syncMesMaterialBatch(...)` 调整：

- 已 `SUCCESS` 且有物料号：跳过。
- `PROCESSING`：跳过。
- `CALLBACK_PENDING`：跳过，提示“等待MES回调”。
- `FAILED`：允许重试。
- `PENDING`：允许提交。
- 单行返回 `CALLBACK_PENDING` 时，批量任务该行计入“已受理待回调”，不计入失败。

批量汇总建议新增：

```json
{
  "successCount": 3,
  "callbackPendingCount": 2,
  "failedCount": 1,
  "skippedCount": 4
}
```

## 8. 前端设计

修改：

```text
quote-frontend/src/components/DeliveredItemManage.vue
quote-frontend/src/services/deliveredItemApi.js
```

### 8.1 状态筛选

MES 状态下拉新增：

| 值 | 文案 |
| --- | --- |
| `PENDING` | 未获取 |
| `PROCESSING` | 处理中 |
| `CALLBACK_PENDING` | 待MES回调 |
| `SUCCESS` | 已获取 |
| `FAILED` | 获取失败 |

### 8.2 表格展示

- `CALLBACK_PENDING` 使用 warning / info 风格标签，文案 `待MES回调`。
- 物料号为空但状态为 `CALLBACK_PENDING` 时，不显示“缺物料号”错误感文案，而显示“待回调”。
- 详情抽屉展示：
  - `mesRequestId`
  - MES 外部任务号
  - 回调接收时间
  - MES 状态消息

### 8.3 操作按钮

单行按钮规则：

| 状态 | 按钮 |
| --- | --- |
| `PENDING` | 获取物料号 |
| `FAILED` | 重试 |
| `PROCESSING` | 处理中，禁用 |
| `CALLBACK_PENDING` | 待回调，禁用 |
| `SUCCESS` | 不显示获取按钮 |

批量按钮说明文案：

```text
已成功、处理中、待MES回调的行会自动跳过。
```

## 9. 权限与安全

### 9.1 用户权限

不新增用户可分配权限。继续使用：

| 权限 | 作用 |
| --- | --- |
| `market:delivered-item:view` | 查看已提零件。 |
| `market:delivered-item:mes-sync` | 点击获取物料号 / 重试。 |

### 9.2 回调接口安全

MES 回调接口不使用用户登录态，不使用前端权限。

安全要求：

- 必须校验机器 token。
- token 不得进入操作日志、文件日志、前端响应和测试快照。
- 回调体只保存必要摘要，不保存完整 JSON。
- `materialNo` 必须做非空、长度和字符清洗。
- `mesRequestId` 必须存在并匹配请求流水。
- `ticketId` 必须与请求流水中的 `ticket_id` 一致。
- 物料号冲突不得覆盖已确认结果。

## 10. 日志与审计

### 10.1 操作日志动作

新增或补齐动作：

| 动作编码 | 模块 | targetType | 说明 |
| --- | --- | --- | --- |
| `DELIVERY_MES_MATERIAL_SYNC_SUBMIT` | `DELIVERY` | `order_delivery_ticket` | 用户提交 MES 物料号获取。 |
| `DELIVERY_MES_MATERIAL_SYNC_ACCEPTED` | `DELIVERY` | `order_delivery_ticket` | MES 已受理，等待回调。 |
| `DELIVERY_MES_MATERIAL_SYNC_SUCCESS` | `DELIVERY` | `order_delivery_ticket` | MES 即时返回物料号成功。 |
| `DELIVERY_MES_MATERIAL_SYNC_FAILED` | `DELIVERY` | `order_delivery_ticket` | MES 即时请求失败。 |
| `DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS` | `DELIVERY` | `order_delivery_ticket` | MES 回调写回物料号成功。 |
| `DELIVERY_MES_MATERIAL_CALLBACK_FAILED` | `DELIVERY` | `order_delivery_ticket` | MES 回调失败或业务拒绝。 |
| `DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT` | `DELIVERY` | `order_delivery_ticket` | MES 回调物料号与已确认值冲突。 |

摘要允许记录：

- `ticketId`
- `taskId`
- `mesRequestId`
- `externalTraceId`
- `sourceType`
- `sourceOrderId`
- `sourceItemId`
- `partNo`
- `version`
- `internalProjectNo`
- `materialNo`
- `status`
- 失败摘要

禁止记录：

- MES token。
- Authorization header。
- Cookie。
- 图纸文件内容。
- 完整请求 JSON。
- 完整回调 JSON。

### 10.2 文件日志

建议事件：

```text
MES material sync submitted ticketId={} taskId={} mesRequestId={} sourceType={} partNo={}
MES material sync accepted ticketId={} taskId={} mesRequestId={} externalTraceId={}
MES material sync completed ticketId={} taskId={} mesRequestId={} materialNo={} durationMs={}
MES material callback received ticketId={} mesRequestId={} externalTraceId={}
MES material callback completed ticketId={} mesRequestId={} materialNo={}
MES material callback rejected ticketId={} mesRequestId={} reason={}
MES material callback conflict ticketId={} mesRequestId={} existingMaterialNo={} incomingMaterialNo={}
```

异常堆栈只进入后端错误日志，必须带 `requestId`、`ticketId`、`mesRequestId`，不得带完整外部 payload。

## 11. 幂等与并发规则

### 11.1 即时响应幂等

- 同一 `mes_request_id` 的成功响应重复处理，若物料号一致，返回成功。
- 若物料号不一致，标记冲突，不覆盖。

### 11.2 回调幂等

- 回调成功后再次收到相同 `mesRequestId + materialNo`，返回 200，提示已处理。
- 回调成功后收到相同 `mesRequestId` 但不同 `materialNo`，返回 409，不覆盖。
- 回调带来的 `ticketId` 与请求流水不一致，返回 409。
- 找不到 `mesRequestId`，返回 404 或 409，记录低噪声拒绝日志。

### 11.3 旧请求回调

如果用户重试生成了新的 `mes_request_id`：

- 旧 `mes_request_id` 的回调不能覆盖当前 ticket。
- 如果 ticket 已经 `SUCCESS` 且物料号一致，可按幂等成功处理并标记旧请求 `IGNORED`。
- 如果 ticket 未成功但旧请求不是当前请求，第一版默认拒绝写入并标记 `STALE`，避免旧任务污染当前流程。

### 11.4 销售订单回写冲突

销售订单来源回写时：

- `sales_order_item.material_no` 为空或与回调物料号一致：允许写入 / 幂等。
- `sales_order_item.material_no` 已有不同值且 `material_no_status = 已确认`：拒绝覆盖，记录冲突。
- `sales_order_item.material_no` 已有不同值但 `material_no_status != 已确认`：第一版仍不建议静默覆盖，应返回冲突，由用户或管理员确认后重试。

## 12. 与 U9 同步的关系

U9 同步模块不直接查询 MES，也不实时查询 `order_delivery_ticket` 取物料号。

关系如下：

```text
已提零件 / MES 回调
  -> 写 order_delivery_ticket.material_no
  -> 销售订单来源同步写 sales_order_item.material_no + material_no_status=已确认
  -> U9同步读取 sales_order_header / sales_order_item
```

因此：

- `CALLBACK_PENDING` 状态下，销售订单 U9 同步预览应显示对应零件物料号未确认，不允许整单同步。
- MES 回调成功后，销售订单 U9 同步页面刷新即可看到物料号已确认。
- U9 同步不会消费 `mes_request_id`，只消费已确认物料号。

## 13. 开发阶段

### 阶段 1：数据模型与状态骨架

目标：系统可以保存 MES 请求唯一标识和待回调状态。

修改：

- 新增迁移 `Vxx__delivery_ticket_mes_callback.sql`。
- `order_delivery_ticket` 新增 `mes_request_id`、`mes_external_task_id`、`mes_callback_received_at`、`mes_callback_message`。
- 新增 `order_delivery_ticket_mes_request` 请求流水表。
- `OrderDeliveryTicket` 实体补齐字段。
- 新增请求流水实体和 Mapper。
- 前后端状态枚举增加 `CALLBACK_PENDING`。

验收：

- 数据库存在 `mes_request_id` 并有唯一索引。
- 请求流水表可按 `mes_request_id` 唯一定位。
- 已提零件查询能返回 `CALLBACK_PENDING`。

### 阶段 2：MES 即时响应与待回调响应适配

目标：QPO 调 MES 后能区分即时成功、待回调和失败。

修改：

- `MesMaterialResolveReq` 新增 `mesRequestId` 和来源字段。
- `MesMaterialResolveResp` 新增 `mesRequestId`、`status`、`callbackRequired`。
- `MesMaterialSyncService.syncMaterialNo(...)` 生成并保存 `mes_request_id`。
- 即时返回 `materialNo` 时走成功写回。
- MES 返回待回调时，ticket 状态写 `CALLBACK_PENDING`，system_task 成功完成。
- 批量任务汇总增加待回调数量。

验收：

- MES 即时返回物料号时，行为与现有成功路径一致。
- MES 返回 `callbackRequired=true` 且无物料号时，不再标记失败。
- `CALLBACK_PENDING` 不允许重复提交获取物料号。
- 批量任务中待回调行不计入失败。

### 阶段 3：MES 回调接口

目标：MES 后续回调能安全写回物料号。

修改：

- 新增 `MesIntegrationCallbackController`。
- 新增 `POST /api/integrations/mes/material-callback`。
- 新增 `MesMaterialCallbackReq` / `MesMaterialCallbackResp`。
- 新增回调 token 校验。
- 回调按 `mesRequestId` 定位请求流水，按 `ticketId` 交叉校验。
- 回调成功写入 `order_delivery_ticket.material_no`。
- 销售订单来源同步回写 `sales_order_item` 物料号确认字段。
- 重复回调幂等处理，冲突回调拒绝覆盖。

验收：

- MES 带 `mesRequestId` 和 `ticketId` 回调成功后，已提零件状态变为 `SUCCESS`。
- 销售订单来源提单行回调成功后，销售订单零件物料号状态为 `已确认`。
- 重复相同回调返回成功，不重复污染数据。
- 不同物料号冲突回调不覆盖已确认物料号。
- 无 token 或 token 错误的回调被拒绝。

### 阶段 4：已提零件前端展示

目标：用户能看懂哪些零件在等 MES 回调。

修改：

- `DeliveredItemManage.vue` MES 状态筛选新增 `待MES回调`。
- 表格状态标签支持 `CALLBACK_PENDING`。
- 单行按钮在 `CALLBACK_PENDING` 时显示禁用态 `待回调`。
- 批量确认文案说明会跳过待回调行。
- 详情抽屉展示 `mesRequestId`、MES 任务号、回调时间和状态摘要。
- 前端测试补充待回调状态展示和按钮禁用。

验收：

- 待回调行不会被用户重复提交。
- 用户能在列表和详情看到待回调状态。
- 原 `PENDING`、`PROCESSING`、`SUCCESS`、`FAILED` 展示不回退。

### 阶段 5：文档、测试与回归

目标：联调口径、运维口径和测试覆盖完整收口。

修改：

- 更新 `docs/QPO-MES-WMS对接/03-MES物料号同步开发文档.md`。
- 更新 `docs/QPO-MES-WMS对接/06-提供给MES的物料号接口对接说明.md`，移除“不需要 MES 回调”的旧口径。
- 更新 `docs/QPO日志模块/03-日志运维说明.md`。
- 更新 `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md`。
- 权限清单说明不新增用户权限，继续使用 `market:delivered-item:mes-sync`。
- 补后端测试和前端测试。

后端测试：

- 即时成功返回物料号。
- 待回调响应进入 `CALLBACK_PENDING`。
- 待回调响应下 system_task 成功完成。
- `CALLBACK_PENDING` 状态禁止重复提交。
- 回调成功写回 `order_delivery_ticket`。
- 销售订单来源回调成功写回 `sales_order_item`。
- 重复回调幂等。
- 冲突回调拒绝覆盖。
- 旧 `mesRequestId` 回调不覆盖新请求。
- 回调 token 缺失 / 错误被拒绝。
- 批量任务待回调计数正确。

前端测试：

- MES 状态筛选包含 `待MES回调`。
- `CALLBACK_PENDING` 行显示待回调标签。
- `CALLBACK_PENDING` 行获取物料号按钮禁用。
- 批量获取文案包含待回调跳过说明。
- 原成功 / 失败 / 处理中按钮逻辑不回退。

构建验证：

```powershell
mvnw.cmd test
npm test
npm run build
```

## 14. 对 MES 的联调要求

MES 需要支持：

1. 接收并保存 / 透传 `mesRequestId`。
2. 即时成功时返回 `success=true`、`status=SUCCESS`、`materialNo`、`mesRequestId`。
3. 需要人工或长时间处理时返回 `success=true`、`status=CALLBACK_PENDING`、`callbackRequired=true`、`mesRequestId`、`externalTraceId`。
4. 后续回调 QPO 时带回 `mesRequestId`、`ticketId`、`materialNo`。
5. 不要因为重复请求创建多个不同物料号。
6. 回调失败或物料号无法生成时，也应回调 `status=FAILED` 和失败摘要，便于 QPO 状态闭环。

建议双方联调用例：

| 场景 | MES 行为 | QPO 预期 |
| --- | --- | --- |
| 即时有物料号 | response 返回 `materialNo` | ticket `SUCCESS`，物料号写入。 |
| 需要后续人工维护 | response 返回 `CALLBACK_PENDING` | ticket `CALLBACK_PENDING`，任务成功完成。 |
| 回调成功 | callback 返回 `materialNo` | ticket `SUCCESS`，销售订单来源同步回写。 |
| 重复回调 | callback 重复同一物料号 | QPO 返回成功，数据不重复变更。 |
| 冲突回调 | callback 返回不同物料号 | QPO 拒绝覆盖，记录冲突。 |
| 无效 token | callback 未带合法 token | QPO 拒绝。 |
| 错误 ticketId | `mesRequestId` 与 `ticketId` 不匹配 | QPO 拒绝。 |

## 15. 验收总清单

- 已提零件模块可以继续单条 / 批量获取物料号。
- MES 即时返回物料号时，物料号立即写入已提零件。
- MES 返回待回调时，已提零件显示 `待MES回调`。
- 待回调状态下 QPO 后台任务不会长时间运行。
- MES 回调接口可按 `mesRequestId` 定位并写回物料号。
- 回调成功后，已提零件状态为 `SUCCESS`。
- 销售订单来源提单行回调成功后，`sales_order_item.material_no_status = 已确认`。
- U9 同步仍只读取销售订单主表 / 零件表，不实时查询提单行补物料号。
- 重复回调幂等，不同物料号冲突不覆盖。
- 日志不记录 token、密钥、Authorization header、完整请求体或完整回调体。
- 原 RFQ 提单、已提零件、WMS 锁库、销售订单 U9 同步流程不回退。

### 阶段 8 收口补充：RFQ 派生销售订单双写

RFQ 议价提单生成的 ticket 通过 `sales_order_item.source_ticket_id` 关联派生销售订单零件。MES 即时成功、异步回调、活动主请求完成和 follower fan-out 都使用同一双写服务，幂等写回 `item_quote_version` 与 `sales_order_item`；冲突物料号不覆盖任一主表。

销售订单直接导入来源仍只回写 `sales_order_item`。回调接口只返回和记录安全摘要，不记录 token、密钥、Authorization header、完整外部请求体或响应体；普通读取不记操作日志。
