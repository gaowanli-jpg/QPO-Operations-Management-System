# 44-QPO 与 MES 真实批量联调及契约重构开发文档

> 文档状态：已完成真实环境探测，待按阶段实施。
>
> 适用范围：QPO 已提零件批量获取 MES 物料号，以及待产零件批量下发 MES 生产需求。
>
> 事实来源：2026-08-12 读取 MES 测试环境 OpenAPI、MES 运行日志、数据中心接口资料和当前 QPO 代码，并结合 QPO 与 MES 开发者确认的字段及回调责任边界。本文中的真实接口字段优先于第 03、09、18、22 号历史文档中的占位契约。

## 1. 背景与最终结论

QPO 已经实现了“已提零件获取 MES 物料号”和“待产零件下发 MES 生产”页面、权限、异步任务和状态字段，但当前实现仍混合了早期占位契约：

- 前端支持多选，后端实际逐行调用 MES，不是真正的一次批量外呼。
- 物料准备请求已经接近 MES 的 `bizId + items[]` 契约，但每次只发送一行，返回只读取第一行。
- 生产下发仍发送 QPO 内部 allocation 平铺对象，与 MES 的 `bizId + items[]` 契约完全不同。
- 生产响应 DTO 仍按 `success/requestId/externalId` 解析，无法解析数据中心真实的 `code/msg/data.items[]`。
- 数据中心本地 endpoint 路径为空，`erp.env` 中仍是早期 MES 直连默认路径，尚不能从 QPO 发起真实调用。
- MES 使用 `bizId` 作为严格幂等键；成功请求重复提交会返回“相同 bizId 已成功调用，请勿重复提交”。
- 物料准备可能立即返回已有物料号，也可能只创建 `PENDING_CREATE` 任务，不能把后一种情况错误标记为失败。

本次采用以下最终方案：

1. QPO 只调用数据中心，不直接调用 MES；链路固定为 `QPO -> 数据中心 -> MES`。
2. 两条链路均改为真实批量请求，一次外呼使用一个稳定唯一 `bizId`，请求包含一个或多个 `items`。
3. 新增 MES 批次和批次明细持久化模型，明确表达“一个 bizId 对多条 QPO 行”。
4. 外部 HTTP 调用不占用数据库长事务；先短事务预约批次和行状态，再在事务外调用，最后短事务逐行回写。
5. 物料准备支持 `EXISTING_PRODUCT` 立即成功和 `PENDING_CREATE` 待处理两种真实结果；新物料创建完成后，MES 将结果提交到数据中心回调入口，再由数据中心转发到 QPO 批量回调接口写回物料号。
6. 生产下发只发送 `PRODUCTION_REQUIRED` 待产行，数量固定为 `demandQty = makeQty = allocation.qty`、`buyQty = stockQty = 0`。
7. 外部调用超时或返回不可判定时进入 `RESULT_UNKNOWN`，不得用新 bizId 直接重试，避免在 MES 重复创建任务。
8. 复用现有权限，不新增 `common:*` 或同义权限。
9. 真实联调数据使用 `QPO-MES-E2E-` 统一测试标识前缀，完整业务编号仍由日期、序号或 ULID 保持唯一，并保留在 QPO 与 MES 测试环境供后续核对。

## 2. 真实环境探测结论

### 2.1 环境与链路

测试环境已确认：

- 数据中心业务入口：`http://10.2.25.100:18080`。
- 数据中心 token：`POST /oauth/token`，OAuth 2.0 Client Credentials。
- MES 后端健康检查正常，实际监听测试服务器 `48080`。
- MES OpenAPI 文档可由 MES 自身 `/v3/api-docs` 读取。
- QPO 使用数据中心提供的 `/api/v1/...` 路径，不直接使用 MES 内部 `/openapi/...` 路径。
- 正常调用方向分为两条：业务创建出站为 `QPO -> 数据中心 -> MES`；新物料完成通知入站为 `MES -> 数据中心回调入口 -> QPO /api/integrations/mes/material-callback`。两条链路都经过数据中心，但回调不经过 QPO 的出站 client。

数据中心实际接口：

```text
POST /api/v1/mes/openapi/material-preparation/create
POST /api/v1/mes/openapi/demand/create
```

MES 还提供只读接口：

```text
GET  /openapi/material/page
POST /openapi/production-base-info/query
GET  /openapi/production-order/list
```

但当前 QPO 数据中心客户端凭据对这些只读接口返回业务码 `403`“没有该操作权限”。因此它们只能作为后续对账能力，不能作为本阶段已具备前提。

### 2.2 物料准备实测语义

MES 真实日志已观察到两类成功结果：

```json
{
  "bizId": "003",
  "items": [
    {
      "taskId": 2,
      "taskNo": "MP202608050001",
      "drawingNo": "tu0001",
      "drawingVersion": "V2",
      "status": "EXISTING_PRODUCT",
      "productId": 20,
      "productCode": "SY100399000001",
      "productName": "业务演示成品"
    }
  ]
}
```

以及：

```json
{
  "bizId": "001",
  "items": [
    {
      "taskId": 1,
      "taskNo": "MP202607310001",
      "drawingNo": "图纸001",
      "drawingVersion": "v1",
      "status": "PENDING_CREATE",
      "productId": null,
      "productCode": null,
      "productName": null
    }
  ]
}
```

已确认规则：

- `bizId` 是幂等键，不是普通追踪字段。
- 同一成功 `bizId` 再次提交会被 MES 业务拒绝。
- `EXISTING_PRODUCT + productCode` 表示可立即确认物料号。
- `PENDING_CREATE + productCode=null` 表示 MES 已受理；MES 创建物料完成后必须调用数据中心提供的物料结果回调入口，由数据中心转发到 QPO 返回正式物料号。
- 回调责任边界已经确认：QPO 定义最终业务请求/响应 JSON、幂等、原子性和错误语义；数据中心向 MES 暴露回调入口，负责两段鉴权衔接和结果透传；MES 保存原 `bizId/taskNo` 并按契约向数据中心执行至少一次投递。数据中心不得把 QPO 的业务拒绝或服务失败统一包装为成功。
- 只读查询接口可以作为运维核对补充，但不是新物料正常回写链路的前置条件。

### 2.3 真实 OpenAPI 必填字段

物料准备请求必填：

- 头：`bizId`、`items`，且 `items` 至少一行。
- 行：`drawingNo`、`drawingVersion`、`drawingUrls`，且 `drawingUrls` 至少一个 URL。

生产需求请求必填：

- 头：`bizId`、`items`，且 `items` 至少一行。
- 行：`saleOrderNo`、`saleOrderItemNo`、`productCode`、`demandQty`、`makeQty`、`buyQty`、`stockQty`、`requiredDate`。
- 四个数量均不得小于 0。

OpenAPI 中 `code` 的真实类型为整数，成功码为 `0`；外部文本资料把它展示成字符串只是文档生成示例，QPO 按整数兼容字符串解析。

### 2.4 当前 QPO 数据事实

联调前本地业务库为空：

```text
order_rfq = 0
sales_order_header = 0
order_delivery_ticket = 0
order_delivery_ticket_allocation = 0
```

因此本次可以通过正常 QPO 业务入口创建独立测试订单，不需要修改或覆盖历史订单。系统数据、权限数据、客户缓存和材料工艺主数据不得清空。

## 3. 目标与非目标

### 3.1 开发目标

- QPO 可通过数据中心一次批量创建 MES 物料准备任务。
- QPO 可正确接收多行物料准备结果，并逐行写回提单、RFQ 零件或销售订单零件。
- QPO 可区分已有物料、待创建、明确失败和结果未知。
- QPO 可通过数据中心一次批量下发 MES 待生产需求。
- MES 生产需求中的订单号、行号、物料号、数量、交期、BOM 和工艺路线来自明确的 QPO 快照。
- 同一批次并发提交、重复点击和超时重试不会重复创建 MES 业务任务。
- 用户能在现有已提零件和待产零件页面看清批次结果与逐行结果。
- 保留 U9、WMS、待产、锁库和后续状态链路的现有业务行为。
- 完成一组使用 `QPO-MES-E2E-` 统一测试标识前缀且完整编号唯一的真实数据端到端验收。

### 3.2 明确非目标

- 不开发 MES 管理后台功能。
- 不修改 MES 源码和数据库业务数据，除非联调发现 MES 缺陷且用户另行授权。
- 不让 QPO 直接连接 MES 数据库作为正式集成方式。
- 不在 QPO 自动物料编码或伪造 MES 物料号。
- 不把 `PENDING_CREATE` 当成物料号同步成功。
- 不向 MES 生产需求下发 `IN_STOCK_LOCK` 或 `IN_PRODUCTION_LOCK` 行。
- 不新增销售订单、客户、工艺或材料的自由文本替代字段。
- 不新增定时自动下发生产任务。
- 不新增用户权限或可分配 `common:*`。

## 4. 业务边界与批量分组

### 4.1 为什么必须持久化真实批次

MES 请求只有一个 `bizId`，但包含多个 `items`。当前 QPO 的 `order_delivery_ticket.mes_request_id` 和 `order_delivery_ticket_allocation.mes_production_request_id` 都是每行唯一字段，无法单独表达：

- 一个外部请求包含哪些 QPO 行。
- 请求发出后超时，哪些行处于不可直接重试状态。
- MES 返回多行时每行对应哪个 QPO 主记录。
- 一批中部分立即成功、部分待创建时的独立状态。

因此新增批次和明细，而不是把同一个 `bizId` 粗暴写入多个带唯一索引的旧字段。

### 4.2 物料准备分组

物料准备头字段是整批共享字段，按以下三元组分组：

```text
customerCode + customerName + internalProjectNo
```

同组三元组下可以一次发送多条 MES `items`。不同客户或内部项目号必须拆成不同 `bizId`，不能选择第一行头字段覆盖其他行。

QPO 现有“图号 + 版本”活动主请求去重继续生效：

- 同一图号版本只保留一个活动主请求。
- follower 不重复进入 MES `items`。
- 批次返回主请求成功或待处理后，QPO 按现有 waiter 关系 fan-out。

### 4.3 生产需求分组

生产需求头字段同样是整批共享字段。为了保证行号归属明确，按以下四元组分组：

```text
customerCode + customerName + internalProjectNo + saleOrderNo
```

生产下发不以 `allocation.source_type` 作为唯一准入条件，而是要求每条 allocation 最终能够唯一解析到正式 `sales_order_item + sales_order_header`。解析顺序固定如下：

1. 首选按 `sales_order_item.source_ticket_id = allocation.ticket_id` 反查销售订单行。RFQ 正式提单派生销售订单后，ticket 仍保留 `source_type = RFQ`，但派生的销售订单行通过 `source_ticket_id` 与该 ticket 建立唯一关系，因此必须走此路径。
2. 第一步无结果时，仅对直接导入销售订单产生的提单，允许按 `allocation.source_type = SALES_ORDER` 且 `allocation.source_item_id = sales_order_item.id` 回退解析；同时校验 `source_order_id`、ticket 来源和销售订单行归属一致。
3. 从解析出的 `sales_order_item.sales_order_id` 加载唯一 `sales_order_header`，取得正式销售订单号、客户快照和行号。
4. 两条路径解析到不同销售订单行、任一路径出现多条、关联缺失、头行归属不一致时，逐行阻断并提示“缺少或无法唯一确定正式销售订单来源”，不影响其他合法分组。

MES 强制要求 `saleOrderNo` 和 `saleOrderItemNo`。任何情况下都不允许使用 `rfq_no`、RFQ 主键、ticket ID、allocation ID 或其他近似字段伪造销售订单号或行号。

### 4.4 批次上限和顺序

- 单个 QPO 操作最多接受 100 行，超过时后端拒绝并提示分批选择。
- 分组后每个 MES 请求最多 100 个 `items`。
- 明细按 QPO 主键升序稳定组装，指纹和重试不受前端选择顺序影响。
- 返回行不依赖数组位置，使用业务复合键匹配。

## 5. 真实 MES 契约

### 5.1 通用响应包装

两条接口都使用：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {}
}
```

QPO 规则：

- HTTP 2xx 且 `code = 0` 才是 MES 业务成功。
- `code` 同时兼容 JSON 数字 `0` 和字符串 `"0"`，序列化时不主动发送 `code`。
- HTTP 2xx 但 `code != 0` 属于明确业务失败。
- HTTP 非 2xx、连接失败、读取超时、响应为空或 JSON 不可解析属于结果未知或明确失败，按第 8 章状态机处理。
- 错误响应只保留脱敏且截断的 `msg`，不保存完整响应体。

### 5.2 物料准备请求

数据中心路径：

```text
POST /api/v1/mes/openapi/material-preparation/create
```

目标 JSON：

```json
{
  "bizId": "QPO-MAT-20260812-01J5ABCDEF",
  "customerCode": "GSS1",
  "customerName": "测试客户",
  "internalProjectNo": "QPO-MES-E2E-20260812",
  "items": [
    {
      "drawingNo": "QPO-MES-E2E-P001",
      "drawingVersion": "A",
      "drawingUrls": [
        "http://qpo-test.example/api/files/..."
      ],
      "remark": "QPO MES E2E material preparation",
      "customerRequirements": "材料名称：精密安装板\n表面处理：本色阳极氧化"
    }
  ]
}
```

字段矩阵：

| MES 字段 | 必填 | QPO 来源 | 为空处理 |
| --- | --- | --- | --- |
| `bizId` | 是 | QPO 批次服务生成并持久化 | 不允许为空 |
| `customerCode` | 否 | RFQ `customer_code` 或销售订单 `customer_code` 快照 | 可不发送 |
| `customerName` | 否 | RFQ/销售订单客户名称快照 | 可不发送 |
| `internalProjectNo` | 否（QPO 业务必填） | 提单快照 `internal_project_no` | QPO 阻断 |
| `items[].drawingNo` | 是 | `order_delivery_ticket.part_no` | QPO 阻断 |
| `items[].drawingVersion` | 是 | `order_delivery_ticket.version` | 允许空字符串，因为 MES 注解只要求字段存在；但匹配键保留空版本口径 |
| `items[].drawingUrls` | 是 | 当前绑定图纸经 `BusinessFileService.externalReadableUrl` 转换后的绝对 URL | QPO 阻断 |
| `items[].standard` | 否 | 当前 QPO 没有已确认同义字段 | 不发送，禁止用材料名称/描述代替 |
| `items[].remark` | 否 | 提单备注快照 | 空时不发送 |
| `items[].customerRequirements` | 否 | 提单 `material_name` 和 `surface_treatment` 快照 | 按下述固定格式拼接；两项都空时不发送 |

`customerRequirements` 组装规则：

```text
材料名称：{materialName}
表面处理：{surfaceTreatment}
```

- 只输出非空项，保持“材料名称”在前、“表面处理”在后，使用 `\n` 分隔。
- 对每项先 trim；将字段内部的 `\r\n/\r` 统一为 `\n`，保留有业务意义的内部换行。
- 不添加空标签、不使用 `null`、`-` 或其他占位值。
- `remark` 始终使用提单备注快照独立发送，不能并入 `customerRequirements`，也不能反向使用客户需求覆盖备注。
- 使用 `order_delivery_ticket` 快照作为唯一出站来源，不在外呼时重新读取 RFQ 或销售订单的可变字段。
- 如 MES 对该字段存在长度上限，应在阶段 1 真实契约测试中锁定；QPO 不静默截断，超限时阻断该行并给出明确错误。

禁止发送 QPO 内部字段：`ticketId`、`sourceType`、`sourceOrderId`、`sourceItemId`、`rfqId`、`itemId`、材料价格、完整工艺快照和文件内容。

### 5.3 物料准备响应与逐行匹配

真实响应结构：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "bizId": "QPO-MAT-20260812-01J5ABCDEF",
    "items": [
      {
        "taskId": 123,
        "taskNo": "MP202608120001",
        "drawingNo": "QPO-MES-E2E-P001",
        "drawingVersion": "A",
        "status": "PENDING_CREATE",
        "productId": null,
        "productCode": null,
        "productName": null
      }
    ]
  }
}
```

匹配键：

```text
normalize(drawingNo) + normalizeVersion(drawingVersion)
```

规则：

- `data.bizId` 必须等于请求 `bizId`。
- 请求组内不允许存在重复图号版本；重复行应在 MES 去重阶段变成 follower。
- 返回行必须与请求行一一对应；缺行、多行或未知行使该批次进入 `RESULT_UNKNOWN`，不得猜测数组位置。
- `productCode` 非空时，状态按成功处理；当前已知状态为 `EXISTING_PRODUCT`。
- `productCode` 为空且状态为 `PENDING_CREATE/PENDING/ACCEPTED` 时，按 `CALLBACK_PENDING` 处理。
- `taskNo` 优先保存为外部任务号；为空时保存 `taskId` 字符串。

### 5.4 经数据中心转发的物料结果回调契约

`PENDING_CREATE` 的正式收口方式固定为 `MES -> 数据中心回调入口 -> QPO`。QPO 是最终业务接口提供方和业务契约定义方；数据中心是 MES 可访问的回调入口提供方和安全转发方；MES 负责保存原 `bizId/taskNo` 并向数据中心投递。只读查询仅作为 `RESULT_UNKNOWN` 的运维核对补充，不能替代正常回调。

两段接口边界：

```text
MES -> 数据中心：POST <数据中心配置的 MES 物料结果回调入口>
数据中心 -> QPO：POST {QPO_PUBLIC_BASE_URL}/api/integrations/mes/material-callback
```

数据中心对 MES 暴露的具体路径由数据中心部署配置确定，未从现有资料确认前不得在 QPO 代码或本文中虚构固定路径。QPO 下游接收路径固定为：

```http
POST /api/integrations/mes/material-callback
Authorization: Bearer <QPO生成并仅交付数据中心的机器Token>
Content-Type: application/json
```

鉴权和转发规则：

- `MES -> 数据中心` 使用数据中心管理的机器鉴权，不假设与 QPO Bearer Token 相同。
- `数据中心 -> QPO` 使用 QPO 生成的独立 Bearer Token；该明文只安全交付数据中心，不交付 MES。
- 数据中心将本文定义的业务 JSON 转发到 QPO，不增加、删除或改写 `bizId/items` 业务字段。
- 数据中心只有在收到 QPO 响应后才能向 MES 确认结果，并应透传 QPO HTTP 状态和响应体；如平台必须使用统一外层包装，也必须保持等价的 HTTP 状态、业务码和可重试语义。
- 数据中心自身的鉴权失败、限流、网络错误或服务失败也必须返回可区分的非 2xx 结果，不能伪装成 QPO 已接收。

回调采用“增量批量”语义：同一次可返回原批次中一个或多个已经处理完成的物料，不要求等待整个原始批次全部完成；后续完成的物料继续使用同一 `bizId` 发起新的回调。两段调用使用相同业务体，单次最多 100 个 `items`，同一回调内不允许重复图号版本。

请求契约：

```json
{
  "bizId": "QPO-MAT-20260812-01J5ABCDEF",
  "items": [
    {
      "taskNo": "MP202608120001",
      "drawingNo": "QPO-MES-E2E-P001",
      "drawingVersion": "A",
      "status": "SUCCESS",
      "productCode": "SY100399000099",
      "productName": "精密安装板",
      "message": "物料已创建"
    },
    {
      "taskNo": "MP202608120002",
      "drawingNo": "QPO-MES-E2E-P002",
      "drawingVersion": "A",
      "status": "FAILED",
      "productCode": null,
      "productName": null,
      "message": "图纸资料不完整"
    }
  ]
}
```

字段矩阵：

| 字段 | 必填 | 规则 |
| --- | --- | --- |
| `bizId` | 是 | 必须原样使用物料准备请求的批次 `bizId`，最大 80 字符 |
| `items` | 是 | 1 至 100 行；允许只回调原批次中本次已完成的子集 |
| `items[].taskNo` | 是 | 必须原样使用 MES 创建响应中的任务号，最大 120 字符 |
| `items[].drawingNo` | 是 | 必须与原请求对应行一致，trim 后非空，最大 128 字符 |
| `items[].drawingVersion` | 是 | 必须与原请求对应行一致；允许空字符串，最大 64 字符 |
| `items[].status` | 是 | 只接受大写 `SUCCESS` 或 `FAILED` |
| `items[].productCode` | 条件必填 | `SUCCESS` 时必填且最大 100 字符；`FAILED` 时必须为空 |
| `items[].productName` | 否 | MES 物料名称，最大 255 字符；仅保存为批次结果快照 |
| `items[].message` | 条件必填 | `FAILED` 时必填，`SUCCESS` 时可空；QPO 最多保存 500 字符安全摘要 |

定位与原子写入规则：

1. 按 `bizId` 加载唯一 `MATERIAL_PREPARATION` 批次。
2. 按 `bizId + normalize(drawingNo) + normalizeVersion(drawingVersion)` 定位唯一批次明细，不按数组位置匹配。QPO 已保存 `taskNo` 时必须与回调一致；因 create 响应超时而未保存 `taskNo` 时，允许首次合法回调补写该任务号并收口 `RESULT_UNKNOWN`。
3. 锁定本次涉及的批次明细，并确认来源 ticket 当前 `mes_request_id` 仍等于该明细 `line_request_id`；旧批次回调不得覆盖新请求。
4. 先完成整次请求的字段、重复键、关联、状态和物料号冲突校验，再在一个短事务内写入。任意一行非法时整次不写入。
5. `SUCCESS` 写回批次明细和提单，并调用 `SourceItemMaterialNoSyncService` 回写来源 RFQ/销售订单零件，再按现有 waiter 关系 fan-out 同图号版本 follower。
6. `FAILED` 将主请求及其 follower 标记为明确失败并保存脱敏原因，允许用户后续创建新批次重试。
7. 回调可将同一批次的 `SUBMITTING/CALLBACK_PENDING/RESULT_UNKNOWN` 行收口为 `SUCCESS/FAILED`；`RESERVED` 或已被新批次替代的行拒绝写入。
8. 每次写入后重新聚合批次状态：仍有待回调行为 `PARTIAL_PENDING`；全部成功为 `SUCCESS`；无待回调行且存在明确失败为 `FAILED`；仍存在无法判定行为 `RESULT_UNKNOWN`。

幂等规则：

- QPO 按至少一次投递设计，不要求 MES 生成额外 callback event ID。
- 已成功行再次收到相同 `productCode` 的 `SUCCESS`，或已失败行再次收到 `FAILED`，按重复回调成功接收，不重复写来源表、不重复 fan-out。
- 已成功行收到不同 `productCode`、已保存 taskNo 与回调不一致、图号/版本与原请求不一致，或旧批次试图覆盖当前行时，整次返回 HTTP 409，现有数据不变。
- 同一回调中可以同时包含首次应用行和幂等重复行，分别计入 `appliedCount` 与 `duplicateCount`。

成功响应固定为 HTTP 200：

```json
{
  "code": 0,
  "msg": "accepted",
  "data": {
    "bizId": "QPO-MAT-20260812-01J5ABCDEF",
    "receivedCount": 2,
    "appliedCount": 1,
    "duplicateCount": 1
  }
}
```

失败响应保持相同包装，且原子失败时 `appliedCount = 0`：

```json
{
  "code": 409,
  "msg": "material callback batch rejected",
  "data": {
    "bizId": "QPO-MAT-20260812-01J5ABCDEF",
    "receivedCount": 1,
    "appliedCount": 0,
    "duplicateCount": 0,
    "errors": [
      {
        "taskNo": "MP202608120001",
        "drawingNo": "QPO-MES-E2E-P001",
        "drawingVersion": "A",
        "errorCode": "PRODUCT_CODE_CONFLICT",
        "message": "物料号与已确认结果冲突"
      }
    ]
  }
}
```

HTTP 语义：

| HTTP | 含义 | MES 动作 |
| --- | --- | --- |
| `200` | QPO 已接收，包含首次应用或幂等重复 | 数据中心透传后停止重试 |
| `400` | JSON、字段、状态或批内重复键非法 | 不自动重试，记录业务告警 |
| `401` | Bearer Token 缺失或无效 | 不自动重试，修复配置并告警 |
| `404` | `bizId` 或批次明细不存在 | 不自动重试，人工核对环境和原请求 |
| `409` | 物料号冲突、任务关联冲突或旧批次回调 | 不自动重试，人工核对 |
| `413` | 超过 100 行或请求体限制 | 拆分后以同一 `bizId` 重新发送未接收子集 |
| `429` | QPO 暂时限流 | 遵循 `Retry-After`，原请求重试 |
| `500/502/503/504` | QPO 暂时不可用或内部失败 | 保持原请求体和 `bizId` 重试 |

MES 对 `MES -> 数据中心` 的网络断开、读取超时、HTTP 429 和 5xx 必须执行原请求重试：建议间隔为 1、5、15、30 分钟，随后每 60 分钟一次，至少保留 24 小时；超过重试窗口后告警并支持人工重发原请求。4xx 除 429 外不得盲目重试。数据中心负责把 QPO 响应还原为上述 MES 可执行语义；如果 `数据中心 -> QPO` 网络失败，则向 MES 返回 5xx，使 MES 保持原请求体和原 `bizId` 重试。QPO 不在响应中返回 token、内部主键、异常堆栈或数据库信息。

生产环境两段回调都必须使用 HTTPS；测试环境可在受控内网使用 HTTP。QPO Bearer Token 由 QPO 系统配置生成，数据库只保存 hash，明文仅在生成或轮换时展示一次，并且只交付数据中心安全配置。现有单行 `mesRequestId/productCode` 请求只作为 QPO 内部过渡适配器保留，不再作为数据中心下游正式契约，并在批量回调真实验收完成后移除。

### 5.5 生产需求请求

数据中心路径：

```text
POST /api/v1/mes/openapi/demand/create
```

目标 JSON：

```json
{
  "bizId": "QPO-DEM-20260812-01J5ABCDEF",
  "customerCode": "GSS1",
  "customerName": "测试客户",
  "internalProjectNo": "QPO-MES-E2E-20260812",
  "items": [
    {
      "saleOrderNo": "QPO-MES-E2E-SO-20260812-001",
      "saleOrderItemNo": "1",
      "productCode": "SY100399000001",
      "demandQty": 5,
      "makeQty": 5,
      "buyQty": 0,
      "stockQty": 0,
      "requiredDate": "2026-08-20T23:59:59",
      "remark": "QPO MES E2E production demand",
      "customerRequirements": "材料名称：精密安装板\n表面处理：本色阳极氧化",
      "bom": [],
      "processRoute": [
        {
          "programNum": "10",
          "processName": "CNC加工",
          "processTypeCode": "CNC",
          "workHour": 1.5,
          "processDescription": "精加工"
        }
      ]
    }
  ]
}
```

### 5.6 生产需求字段矩阵

| MES 字段 | 必填 | QPO 来源 | 规则 |
| --- | --- | --- | --- |
| `bizId` | 是 | QPO 批次服务生成并持久化 | 稳定唯一，不得重用历史成功值 |
| `customerCode` | 否 | `sales_order_header.customer_code` 快照 | 空时不发送 |
| `customerName` | 否 | `sales_order_header.customer_name` 快照 | 空时不发送 |
| `internalProjectNo` | 否 | allocation 快照 | 同一批次必须一致 |
| `items[].saleOrderNo` | 是 | 按第 4.3 节解析出的 `sales_order_header.document_no`，兼容回退 `sales_order_no` | 禁止用 RFQ 编号或数据库 ID 伪造 |
| `items[].saleOrderItemNo` | 是 | QPO 系统生成的 `sales_order_item.line_no` | 转字符串；订单内从 1 连续递增；禁止用客户 Excel 原始“序号”或 allocation ID 代替 |
| `items[].productCode` | 是 | allocation 已确认 `material_no` | 必须非空且物料状态已确认 |
| `items[].demandQty` | 是 | `allocation.qty` | 正整数 |
| `items[].makeQty` | 是 | `allocation.qty` | 因本页只包含 `PRODUCTION_REQUIRED` |
| `items[].buyQty` | 是 | 常量 `0` | 不把待产行重复当采购量 |
| `items[].stockQty` | 是 | 常量 `0` | 已锁库存行不进入本接口 |
| `items[].requiredDate` | 是 | `allocation.delivery_date` | 按 Asia/Shanghai 理解业务日期，序列化为当天 `23:59:59` 的 ISO 本地日期时间；为空阻断，不伪造当前日期 |
| `items[].remark` | 否 | allocation 备注快照 | 空时不发送 |
| `items[].customerRequirements` | 否 | allocation `material_name` 和 `surface_treatment` 快照 | 完全复用第 5.2 节固定拼接规则；两项都空时不发送 |
| `items[].bom` | 否 | 已确认子件物料号及父子用量 | 详见 5.7 |
| `items[].processRoute` | 否 | allocation 标准工艺路线快照 | 详见 5.8 |

数量恒等式：

```text
demandQty = makeQty + buyQty + stockQty
allocation_type = PRODUCTION_REQUIRED
demandQty = makeQty = allocation.qty > 0
buyQty = 0
stockQty = 0
```

MES 该字段的 Java 类型是 `LocalDateTime`，因此 QPO 必须发送 `yyyy-MM-dd'T'HH:mm:ss`，不附带 `Z` 或 `+08:00`。Asia/Shanghai 只用于解释 QPO 业务日期对应的本地日界，不改变出站 JSON 的无偏移格式。

### 5.7 BOM 映射

MES BOM 只接收：

```json
{
  "itemCode": "CHILD-MATERIAL-001",
  "quantity": 2
}
```

规则：

- MES 生产下发只把 QPO 已确定的原材料 BOM 快照转成 `bom`；缺少 `engineeringMaterialCode` 或毛重时发送空数组或不发送 `bom`。
- `itemCode` 使用 QPO 快照中的 `engineeringMaterialCode`，`quantity` 使用毛重 kg，且只有两个字段都有效时才发送该 BOM 行。
- 普通件、组合件父件、销售订单未命中历史快照的零件都允许 `bom: []`，不因 BOM 资料缺失阻断生产下发。
- 组合件父子关系由 `assemblyRelations` 独立表达；关系缺失、未补全或关系表无记录时返回空数组，不在生产下发接口临时补建关系。
- 后端硬校验只保留 MES 必需的销售订单号、销售订单行号、物料号、数量和交期；BOM、工艺路线、组合件关系有值则传、无值则空。

### 5.8 工艺路线映射

QPO 标准工艺快照映射：

| MES 字段 | QPO 来源 |
| --- | --- |
| `programNum` | `sortNo` 转字符串；为空时按有效工序顺序生成 `10/20/30...` |
| `processName` | `detailName`，回退 `processName/name/categoryName` |
| `processTypeCode` | `processCode` |
| `workHour` | `unitWorkHours`，回退 `inputVal` 中可安全解析的纯数字 |
| `processDescription` | 工艺行 `description/remark`，没有则不发送 |

规则：

- 跳过 `routeType=FEE` 或 `sourceType=extra` 的费用行。
- 不发送 `categoryName`、`detailName`、`unitWorkHours`、`sortNo` 等 QPO 原字段名。
- 不发送小时价格、行金额、材料价格或完整 `processRoute` JSON。
- 工艺类型编码为空不阻断第一版联调，因为 MES OpenAPI 未声明该字段必填；但页面结果显示警告。

### 5.9 生产需求响应

真实响应结构：

```json
{
  "code": 0,
  "msg": "成功",
  "data": {
    "bizId": "QPO-DEM-20260812-01J5ABCDEF",
    "items": [
      {
        "id": 1001,
        "planNo": "PLAN202608120001",
        "productCode": "SY100399000001",
        "status": 0
      }
    ]
  }
}
```

逐行匹配键：

```text
saleOrderNo + saleOrderItemNo + productCode
```

MES 当前响应只返回 `productCode`，不足以在同一批次存在相同物料号的多行时唯一定位。因此 QPO 在真正批量提交前必须完成一次实测：

- 若 MES 返回结构实际包含订单号和行号，则按完整复合键匹配。
- 若仍只返回 `productCode`，同一请求内禁止重复 `productCode`；相同物料号的多行拆成不同 `bizId`。
- 不允许按数组位置盲目回写。

成功后：

- `mes_production_status = SUCCESS`。
- `mes_production_external_id = planNo`，若为空则使用 `id` 字符串。
- 保存 MES 数字状态的安全摘要。

## 6. 数据库设计

### 6.1 Flyway 迁移

使用 `./mig.ps1 mes_real_batch_contract` 生成当前可用迁移号，不在文档中硬编码版本。迁移新增四张表：

```text
mes_integration_batch
mes_material_batch_item
mes_production_batch_item
mes_integration_reconcile_run
```

### 6.2 `mes_integration_batch`

建议字段：

```sql
CREATE TABLE mes_integration_batch (
    id BIGSERIAL PRIMARY KEY,
    batch_type VARCHAR(40) NOT NULL,
    biz_id VARCHAR(80) NOT NULL,
    system_task_id BIGINT,
    group_key_hash VARCHAR(64) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    item_count INTEGER NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    submitted_at TIMESTAMP,
    responded_at TIMESTAMP,
    completed_at TIMESTAMP,
    response_code VARCHAR(40),
    response_message VARCHAR(500),
    created_by BIGINT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_batch_type CHECK (batch_type IN ('MATERIAL_PREPARATION', 'PRODUCTION_DEMAND')),
    CONSTRAINT chk_mes_batch_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'PARTIAL_PENDING', 'SUCCESS',
        'FAILED', 'RESULT_UNKNOWN', 'CANCELLED'
    )),
    CONSTRAINT chk_mes_batch_item_count CHECK (item_count > 0)
);

CREATE UNIQUE INDEX uk_mes_integration_batch_biz_id
    ON mes_integration_batch(biz_id);

CREATE INDEX idx_mes_integration_batch_status
    ON mes_integration_batch(batch_type, status, id DESC);
```

禁止保存：完整请求 JSON、完整响应 JSON、access token、Authorization、Cookie、client secret、图纸文件内容。

### 6.3 `mes_material_batch_item`

建议字段：

```sql
CREATE TABLE mes_material_batch_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES mes_integration_batch(id),
    ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    line_request_id VARCHAR(80) NOT NULL,
    mes_request_row_id BIGINT REFERENCES order_delivery_ticket_mes_request(id),
    drawing_no VARCHAR(128) NOT NULL,
    drawing_version VARCHAR(64) NOT NULL DEFAULT '',
    item_key VARCHAR(220) NOT NULL,
    status VARCHAR(30) NOT NULL,
    mes_task_id VARCHAR(120),
    mes_task_no VARCHAR(120),
    product_id VARCHAR(120),
    product_code VARCHAR(100),
    product_name VARCHAR(255),
    response_status VARCHAR(60),
    message VARCHAR(500),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_material_batch_item_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'CALLBACK_PENDING', 'SUCCESS',
        'FAILED', 'RESULT_UNKNOWN', 'SKIPPED'
    )),
    CONSTRAINT uk_mes_material_batch_ticket UNIQUE (batch_id, ticket_id),
    CONSTRAINT uk_mes_material_batch_key UNIQUE (batch_id, item_key),
    CONSTRAINT uk_mes_material_line_request_id UNIQUE (line_request_id)
);

CREATE UNIQUE INDEX uk_mes_material_active_ticket
    ON mes_material_batch_item(ticket_id)
    WHERE status IN ('RESERVED', 'SUBMITTING', 'CALLBACK_PENDING', 'RESULT_UNKNOWN');
```

### 6.4 `mes_production_batch_item`

建议字段：

```sql
CREATE TABLE mes_production_batch_item (
    id BIGSERIAL PRIMARY KEY,
    batch_id BIGINT NOT NULL REFERENCES mes_integration_batch(id),
    allocation_id INTEGER NOT NULL REFERENCES order_delivery_ticket_allocation(id),
    line_request_id VARCHAR(80) NOT NULL,
    sale_order_no VARCHAR(100) NOT NULL,
    sale_order_item_no VARCHAR(100) NOT NULL,
    product_code VARCHAR(100) NOT NULL,
    item_key VARCHAR(320) NOT NULL,
    demand_qty NUMERIC(18, 6) NOT NULL,
    status VARCHAR(30) NOT NULL,
    mes_record_id VARCHAR(120),
    mes_plan_no VARCHAR(120),
    response_status VARCHAR(60),
    message VARCHAR(500),
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_mes_production_batch_item_status CHECK (status IN (
        'RESERVED', 'SUBMITTING', 'SUCCESS', 'FAILED', 'RESULT_UNKNOWN', 'SKIPPED'
    )),
    CONSTRAINT chk_mes_production_batch_qty CHECK (demand_qty > 0),
    CONSTRAINT uk_mes_production_batch_allocation UNIQUE (batch_id, allocation_id),
    CONSTRAINT uk_mes_production_batch_key UNIQUE (batch_id, item_key),
    CONSTRAINT uk_mes_production_line_request_id UNIQUE (line_request_id)
);

CREATE UNIQUE INDEX uk_mes_production_active_allocation
    ON mes_production_batch_item(allocation_id)
    WHERE status IN ('RESERVED', 'SUBMITTING', 'RESULT_UNKNOWN');
```

以上部分唯一索引是跨批次并发保护的最终数据库边界；批次内 `(batch_id, ticket_id/allocation_id)` 唯一约束不能替代它。`SUCCESS/FAILED/SKIPPED` 等终态不占用活动唯一索引，只有旧批次明确终结后才允许创建新批次。

### 6.5 对账记录

`mes_integration_reconcile_run` 只记录对账摘要：

- `batch_id`
- `reconcile_type`
- `status`
- `matched_count`
- `pending_count`
- `failed_count`
- `message`
- `started_at/completed_at`

不保存完整查询响应。

### 6.6 旧字段兼容

- `order_delivery_ticket.mes_request_id` 继续表示该行当前活动物料请求标识。批量实施后写独立内部行请求 ID `QPO-MATL-<ULID>`，不得把同一个批次 `bizId` 写进多行唯一索引。
- `order_delivery_ticket_mes_request` 继续保留每行历史和同图号版本去重职责，并增加可空 `batch_id/batch_item_id` 外键。
- `order_delivery_ticket_allocation.mes_production_request_id` 继续表示该行当前生产下发行标识，写独立内部行请求 ID `QPO-DEML-<ULID>`。
- 旧单条 API 仍可调用，但内部统一转换成只有一个 item 的批次。

批次 `bizId` 只发送给 MES，并保存在 `mes_integration_batch.biz_id`；内部行请求 ID 只用于 QPO 旧字段兼容、逐行唯一约束和回调定位。两者不得拼接，也不得互相替代，从而保证现有 `VARCHAR(80)` 长度和唯一索引始终安全。

## 7. 后端架构

### 7.1 DTO

收窄或新增：

```text
MesCommonResult<T>
MesMaterialPreparationBatchReq/Resp
MesMaterialPreparationItemReq/Resp
MesProductionDemandBatchReq/Resp
MesProductionDemandItemReq/Resp
MesProductionBomItemDTO
MesProductionProcessStepDTO
MesBatchResultDTO
MesBatchResultRowDTO
```

生产请求 DTO 必须只包含真实 MES 字段，删除旧平铺契约中的 QPO 内部字段。旧 DTO 若被测试或兼容调用引用，可保留类名但改变内部结构；不允许序列化禁止字段。

### 7.2 组装器和验证器

新增：

```text
MesMaterialBatchAssembler
MesProductionDemandAssembler
MesMaterialBatchValidator
MesProductionDemandValidator
MesBatchFingerprintService
```

职责：

- 从数据库实体生成精确 JSON DTO。
- 在任何状态预约和 HTTP 调用前完成全组验证。
- 对请求 DTO 做 canonical JSON SHA-256 指纹。
- 生成稳定 item key 和分组 key。
- 为每条物料/生产批次明细分别生成 `QPO-MATL-<ULID>` / `QPO-DEML-<ULID>` 内部行请求 ID；批次 `bizId` 不参与旧行字段拼接。
- 不在 client 或 service 中临时拼 Map。

### 7.3 客户端

`MesMaterialClient` 和 `MesProductionDispatchClient` 继续调用 `DataCenterIntegrationClient`，但改用真实 DTO。

客户端只负责：

- 调用固定 endpoint code。
- 解析通用响应包装。
- 将 HTTP/网络/解析异常转换为不含敏感内容的异常类别。

客户端不负责：

- 查询数据库。
- 生成 bizId。
- 修改业务状态。
- 记录完整请求或响应。

### 7.4 状态服务与事务边界

新增 `MesBatchStateService`，所有数据库预约和回写均由短事务完成：

```text
validate outside transaction
  -> reserve batch and item rows in short transaction
  -> mark source rows PROCESSING in short transaction
  -> call Data Center outside transaction
  -> apply response in short transaction
```

数据库行锁不得跨数据中心/MES 网络调用。

### 7.5 物料批量编排

`DeliveryIntegrationBatchService.syncMesMaterialBatch` 改为：

1. 读取所有 ticket，执行数据范围校验。
2. 跳过已经成功、正在处理或等待回调的行。
3. 执行同图号版本活动主请求去重，follower 不进入外部 items。
4. 校验主请求行并按三元组分组。
5. 为每组创建一个批次并预约所有明细。
6. 每组只调用一次数据中心。
7. 严格匹配所有返回行。
8. 每行分别写 `SUCCESS/CALLBACK_PENDING/FAILED/RESULT_UNKNOWN`。
9. 成功行继续调用现有 `SourceItemMaterialNoSyncService`，双写来源 RFQ 或销售订单零件。
10. 主请求成功或待处理后继续 fan-out follower。

一个分组的前置字段错误只阻断该组；其他合法组继续提交。MES 对一个批次返回整体业务失败时，该批次全部明细失败，但不回滚其他已经成功的批次。

### 7.6 生产需求批量编排

`MesProductionDispatchService.dispatchBatch` 改为：

1. 读取 allocation 并校验数据范围。
2. 跳过非 `PRODUCTION_REQUIRED`、已成功和正在处理行。
3. 首选按 `sales_order_item.source_ticket_id = allocation.ticket_id` 解析 RFQ 提单派生的销售订单行。
4. 首选无结果时，仅对直接导入销售订单提单按 `SALES_ORDER + source_item_id` 回退解析，并交叉校验订单归属。
5. 从 `sales_order_item.sales_order_id` 加载销售订单头；缺失、多义或两条路径冲突时逐行阻断。
6. 校验正式订单号、行号、物料号、正整数数量和交期。
7. 组装 BOM 与工艺路线警告/错误。
8. 按四元组及相同物料防重规则分组。
9. 为每组预约批次和明细。
10. 每组一次调用数据中心。
11. 严格映射返回行并写回 allocation。

### 7.7 结果核对

新增内部服务：

```text
MesMaterialReconciliationService
MesProductionReconciliationService
```

在数据中心未开放只读端点前：

- API 对 `RESULT_UNKNOWN` 返回“当前数据中心未开放核对能力”，不自动创建新请求。
- 运维可在 MES 管理后台按 `bizId/taskNo/planNo` 核对。

数据中心开放端点后：

- 物料按图号版本或 taskNo 核对并回写。
- 生产按 `productCode + saleOrderNo` 查询工单，只在能唯一确定时修复状态。
- 明确不存在后，原批次标记 `FAILED`，用户才能以新 bizId 重试。

## 8. 幂等、并发和状态机

### 8.1 bizId 规则

```text
物料准备：QPO-MAT-yyyyMMdd-<ULID>
生产需求：QPO-DEM-yyyyMMdd-<ULID>
真实验收：前缀增加 E2E 标识，但仍保证总长度 <= 80
```

- bizId 在预约事务中生成并唯一入库。
- 同一次 HTTP 重发必须复用原 bizId 和完全相同的请求指纹。
- 新的业务重试才生成新 bizId，而且必须先证明旧批次明确失败或不存在。
- 历史成功 bizId 永不复用。

### 8.2 内部行请求 ID 规则

```text
物料准备行：QPO-MATL-<ULID>
生产需求行：QPO-DEML-<ULID>
```

- 每条批次明细生成一个全局唯一内部行请求 ID，总长度不得超过 80。
- 内部行请求 ID 写入批次明细 `line_request_id` 及对应旧业务行请求字段，不发送给 MES。
- 一个批次的所有明细共享同一 `bizId`，但每条明细拥有不同 `line_request_id`。
- 历史行请求 ID 不复用；业务重试创建新批次时同步创建新的行请求 ID。

### 8.3 批次状态

| 状态 | 含义 | 是否允许直接重试 |
| --- | --- | --- |
| `RESERVED` | 已预约，尚未发出 | 仅由恢复任务判断 |
| `SUBMITTING` | 正在调用数据中心/MES | 否 |
| `PARTIAL_PENDING` | 至少一行待 MES 创建物料 | 否 |
| `SUCCESS` | 所有应提交行完成 | 否 |
| `FAILED` | 明确未创建或明确业务失败 | 是，生成新 bizId |
| `RESULT_UNKNOWN` | 超时、断连或响应无法唯一匹配 | 否，必须先核对 |
| `CANCELLED` | 预约后明确未外呼且被恢复流程取消 | 是 |

### 8.4 行状态

物料行：

```text
RESERVED -> SUBMITTING -> SUCCESS
                       -> CALLBACK_PENDING
                       -> FAILED
                       -> RESULT_UNKNOWN
```

生产行：

```text
RESERVED -> SUBMITTING -> SUCCESS
                       -> FAILED
                       -> RESULT_UNKNOWN
```

### 8.5 并发保护

- 预约时使用条件更新或行锁，只有 `PENDING/FAILED` 且没有活动批次的行可进入新批次。
- 同一 ticket/allocation 同时只能存在一个活动批次明细。
- `mes_material_batch_item(ticket_id)` 和 `mes_production_batch_item(allocation_id)` 的部分唯一索引是跨批次并发最终边界。
- 前端 loading 和重复点击保护只是体验层，后端唯一索引与条件更新是最终边界。
- `CALLBACK_PENDING` 和 `RESULT_UNKNOWN` 行不能通过构造 API 重提。

### 8.6 恢复策略

- `RESERVED` 超过保护期且没有 `submitted_at`：可标记 `CANCELLED` 并恢复来源行为 `PENDING/FAILED`。
- `SUBMITTING` 超过保护期：标记 `RESULT_UNKNOWN`，不能假定失败。
- 服务重启时只扫描活动批次摘要，不自动重发外部请求。

## 9. API 与前端

### 9.1 现有 API 保持

```text
POST /api/order-delivery/tickets/mes-material/batch
POST /api/order-delivery/production-items/mes-production
```

MES 物料号获取统一使用批量入口，单个零件也通过 `ticketIds` 长度为 1 的批量请求提交。异步 `system_task` 仍用于页面轮询。

### 9.2 新增批次查询/核对 API

```text
GET  /api/order-delivery/mes-batches/{batchId}
POST /api/order-delivery/mes-batches/{batchId}/reconcile
```

权限按批次类型复用：

- 物料批次：`market:delivered-item:mes-sync`。
- 生产批次：`market:production-item:mes-production-submit`。

查询和核对必须重新校验批次内所有订单的数据范围。无权限或无数据范围不能通过构造 batchId 读取或核对。

### 9.3 已提零件页面

保留现有批量按钮，结果区增加：

- MES 批次数。
- 实际发送行数。
- 已有物料成功数。
- 待 MES 创建数。
- follower 数。
- 明确失败数。
- 结果待核对数。
- 每行 `bizId` 摘要、MES taskNo、状态和安全消息。

`CALLBACK_PENDING` 文案明确为“MES 已受理，等待物料创建结果”，不显示为失败。

### 9.4 待产零件页面

确认弹窗在提交前展示：

- 已选行数。
- 将实际发送的合法行数。
- 将拆分的 MES 批次数。
- 待生产总数量。
- 被阻断或跳过的原因摘要。

结果区展示 `planNo` 和逐行状态。`RESULT_UNKNOWN` 只显示“核对结果”，不显示普通重试。

### 9.5 响应式和交互

- 复用现有 Element Plus 和 QPO 页面样式。
- 所有提交、核对按钮接入 `usePendingAction`。
- 批次结果明细在小屏采用纵向列表或横向可滚动的局部表格，不产生页面级横向溢出。
- 长 bizId/taskNo/错误摘要可断行或省略，并提供 tooltip。
- 弹窗关闭只清理当前展示状态，不清除后台任务和数据库批次。

## 10. 数据中心与部署配置

### 10.1 endpoint 配置

在 `system_data_center_endpoint_config` 配置：

| endpoint code | path | method | timeout |
| --- | --- | --- | --- |
| `MES_MATERIAL_RESOLVE` | `/api/v1/mes/openapi/material-preparation/create` | POST | 至少 60000ms |
| `MES_PRODUCTION_DISPATCH` | `/api/v1/mes/openapi/demand/create` | POST | 至少 60000ms |

如增加核对：

```text
MES_MATERIAL_QUERY
MES_PRODUCTION_BASE_INFO_QUERY
MES_PRODUCTION_ORDER_QUERY
```

必须先由数据中心给当前 client 授权后再启用；不得把 403 当作空结果。

### 10.2 fallback 配置

更新 fallback：

```env
ERP_DATA_CENTER_MES_MATERIAL_PATH=/api/v1/mes/openapi/material-preparation/create
ERP_DATA_CENTER_MES_PRODUCTION_DISPATCH_PATH=/api/v1/mes/openapi/demand/create
```

`ERP_MES_BASE_URL/ERP_MES_*_PATH` 只保留已废弃的直连应急兼容，不作为正式测试或部署口径。

### 10.3 图纸可访问性

物料准备前必须验证 `drawingUrls` 对 MES 服务器可达：

- URL 必须是绝对 HTTP/HTTPS 地址。
- 不能是 `localhost`、`127.0.0.1`、Docker 内部服务名或仅开发机可达地址。
- 如果文件接口需要用户 JWT，MES 无法访问；联调应使用受控公开 URL、短时签名 URL或机器级访问方式。
- 日志只记录 host、文件 ID 和 URL 数量，不记录完整签名 query。

### 10.4 部署顺序

1. 数据中心确认两个 create endpoint 对当前 client 可用。
2. QPO 配置数据中心连接和 endpoint 路径。
3. 数据中心/MES 确认 QPO 图纸 URL 可访问。
4. QPO 配置可被数据中心访问的 `publicBaseUrl` 和 `/api/integrations/mes/material-callback`，启用 Bearer Token 校验并生成一次明文 token。
5. 通过安全渠道把 QPO 下游 URL、QPO Bearer Token 和第 5.4 节契约交付数据中心配置；QPO Token 不交付 MES，也不写入仓库、群聊日志或普通部署文档。
6. 数据中心配置并向 MES 发布物料结果回调入口，配置 `MES -> 数据中心` 独立鉴权、`数据中心 -> QPO` 转发和 QPO 结果语义透传。
7. 执行 Flyway 迁移并部署 QPO backend/frontend。
8. 先分别验证 MES 到数据中心的鉴权拒绝、数据中心到 QPO 的 401、成功、幂等重复、冲突和 5xx 透传场景，再创建真实 E2E 数据。
9. 最后验证数据中心 token、两个出站 endpoint 和完整 `PENDING_CREATE -> MES -> 数据中心 -> QPO -> SUCCESS` 链路。

## 11. 权限、数据范围与日志

### 11.1 权限

不新增权限：

- 已提零件获取物料号继续使用 `market:delivered-item:mes-sync`。
- 待产零件下发生产继续使用 `market:production-item:mes-production-submit`。
- 查看页面分别继续使用现有 view 权限。
- 系统配置 endpoint 继续使用系统配置模块现有权限。
- MES 回调使用机器到机器鉴权，不挂用户权限；MES 到数据中心和数据中心到 QPO 使用各自独立凭据。

后端 `@RequirePermission` 是最终边界，前端隐藏按钮不能代替后端校验。
QPO 回调属于数据中心到 QPO 的机器入站接口，不使用用户 JWT 或用户数据权限；安全边界是 HTTPS、QPO 独立 Bearer Token、批次/明细关联校验、未完成零件的当前请求校验和回调幂等。已成功取得物料号的零件收到有效历史回调时幂等成功应答且不更新数据。管理 QPO 下游回调 URL 和 token 继续复用 `system:config:inbound:update`、`system:config:inbound:token-rotate`，不新增用户权限。MES 到数据中心的鉴权由数据中心独立管理，不能复用或向 MES 暴露 QPO Token。

### 11.2 数据范围

- 提交前校验每个 ticket/allocation。
- 创建 system_task 前完成数据范围校验，不能先创建任务再发现越权。
- 批次查询和核对按批次全部明细重新校验。
- 无数据范围返回拒绝或空列表，不能泄露客户、订单号、物料号或 MES taskNo。

### 11.3 操作日志

复用并扩展安全摘要：

```text
DELIVERY_MES_MATERIAL_SYNC_SUBMIT
DELIVERY_MES_MATERIAL_SYNC_SUCCESS
DELIVERY_MES_MATERIAL_SYNC_FAILED
DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS
DELIVERY_MES_MATERIAL_CALLBACK_FAILED
PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT
PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS
PRODUCTION_ITEM_MES_PRODUCTION_FAILED
MES_INTEGRATION_BATCH_RECONCILE
```

摘要允许：

- batchId、bizId 摘要。
- taskId。
- ticket/allocation 数量和最多 20 个内部 ID。
- success/pending/failed/unknown/skipped 数量。
- 回调 received/applied/duplicate/rejected 数量和脱敏错误码。
- 耗时和脱敏错误分类。

禁止：

- 完整请求和响应。
- 完整图纸 URL 签名参数。
- token、Authorization、Cookie、client secret、凭据。
- 数据中心记录的完整回调请求体、QPO 完整响应体或两段鉴权信息。
- 图纸内容、BOM/工艺完整 JSON。

### 11.4 文件日志

推荐事件：

```text
MES material batch reserved batchId={} bizId={} taskId={} itemCount={}
MES material batch completed batchId={} successCount={} pendingCount={} failedCount={} unknownCount={} durationMs={}
MES material callback completed batchId={} bizId={} receivedCount={} appliedCount={} duplicateCount={} durationMs={}
MES material callback rejected bizId={} receivedCount={} errorCode={} durationMs={}
MES production batch reserved batchId={} bizId={} taskId={} itemCount={} demandQty={}
MES production batch completed batchId={} successCount={} failedCount={} unknownCount={} durationMs={}
MES batch reconcile completed batchId={} matchedCount={} pendingCount={} failedCount={} durationMs={}
```

失败日志必须带异常对象进入 `qpo-error.log`，但异常消息先经过敏感信息清洗。

## 12. 自动化测试设计

### 12.1 迁移测试

- 空库迁移通过。
- 当前基线数据库升级通过。
- `biz_id` 唯一。
- 同一批次不能重复绑定同一 ticket/allocation。
- 状态约束拒绝非法值。
- 活动批次唯一约束阻止并发重复预约。

### 12.2 精确 JSON 契约测试

物料准备断言：

- 只包含第 5.2 节字段。
- `items` 真正包含多行。
- `customerRequirements` 只由提单材料名称和表面处理按固定标签、顺序和换行组装；两项都空时不发送。
- `remark` 独立发送，不能混入 `customerRequirements`。
- 不发送 QPO IDs、价格或完整文件内容。
- 图纸 URL 是 MES 可访问绝对地址。

生产需求断言：

- 只包含第 5.5 节字段。
- 四个数量满足恒等式。
- `requiredDate` 精确序列化为 `yyyy-MM-dd'T'HH:mm:ss`，不带 `Z`、`+08:00` 或其他时区偏移。
- `customerRequirements` 复用物料准备的描述和表面处理组装规则，`remark` 仍独立发送。
- BOM 的 itemCode 是子件物料号。
- 工艺字段名完全符合 MES。
- 不发送旧 `allocationId/ticketId/sourceType/partNo/assemblyRelations` 等字段。

### 12.3 响应解析测试

- `code` 为数字 0 和字符串 `"0"` 都能成功解析。
- `EXISTING_PRODUCT` 立即写回物料号。
- `PENDING_CREATE` 进入等待结果。
- `data.bizId` 不匹配时进入结果未知。
- 返回缺行、多行、未知行不按数组位置误写。
- 生产返回 planNo 正确写回。

物料回调断言：

- MES 只调用数据中心回调入口，数据中心以原业务体调用固定 QPO 下游接口。
- `MES -> 数据中心` 和 `数据中心 -> QPO` 使用独立机器凭据，MES 无法获得 QPO Bearer Token。
- 数据中心保留 QPO 的 HTTP 状态、业务码和可重试语义，不能把 QPO 4xx/5xx 包装为成功。
- 一个 `bizId` 可增量回调原批次的任意已完成子集，单次不超过 100 行。
- 整批字段先校验后原子写入，任意非法行使 `appliedCount = 0`。
- `SUCCESS` 必须有 `productCode`，`FAILED` 必须有 `message` 且不能有 `productCode`。
- 相同成功结果和相同失败结果重复回调返回 HTTP 200，并计入 `duplicateCount`，不重复写来源表或 fan-out。
- 不同物料号、已保存 taskNo 冲突、旧批次回调和图号版本冲突返回 HTTP 409，现有数据不变。
- create 响应超时导致 taskNo 未保存时，可按 `bizId + 图号 + 版本` 接收首次合法回调并补写 taskNo。
- 数据中心调用 QPO 时 Bearer Token 缺失或错误返回 HTTP 401；错误响应不包含内部主键、异常堆栈或凭据。

### 12.4 编排和幂等测试

- 同组 3 行只调用一次 client。
- 不同客户/项目拆分多个批次。
- follower 不重复发送 MES。
- 并发提交只预约一次。
- 同一 ticket/allocation 跨不同 batch 的并发预约由部分唯一索引拒绝。
- RFQ 提单 allocation 通过 `sales_order_item.source_ticket_id = allocation.ticket_id` 解析派生销售订单。
- 直接导入销售订单提单在无 `source_ticket_id` 关联时，通过 `SALES_ORDER + source_item_id` 解析。
- 两条销售订单解析路径冲突、缺失或多义时阻断该行，且绝不使用 `rfq_no` 代填。
- 超时后不允许直接使用新 bizId 重试。
- 明确业务失败后可生成新 bizId 重试。
- 数据库事务不跨外部网络调用。
- 部分分组失败不回滚其他已成功组。

### 12.5 权限与数据范围测试

- 无功能权限不能提交。
- 无订单数据范围不能构造 ticket/allocation ID 提交。
- 无数据范围不能构造 batchId 查询或核对。
- 任一段回调机器凭据无效时均拒绝，且请求不会绕过数据中心直达 QPO。
- 不新增 `common:*`。

### 12.6 前端测试

- 物料批量结果区展示成功、待创建、follower、失败和待核对。
- 生产确认弹窗展示实际发送行数和数量。
- loading 与重复点击保护生效。
- `RESULT_UNKNOWN` 只显示核对，不显示普通重试。
- 小屏无页面级横向溢出或控件重叠。

### 12.7 敏感信息扫描

扫描代码、测试、文档和日志样例，确保不存在：

```text
access_token
Authorization: Bearer <真实值>
client_secret=<真实值>
Cookie=<真实值>
完整数据中心/MES 请求或响应
测试服务器凭据
```

真实凭据只来自未跟踪的 `erp.env` 和用户提供的外部资料，不写入仓库。

## 13. 分阶段开发任务

### 阶段 1：真实契约 DTO、组装器和验证器

目标：QPO 能生成两个 MES 接口的精确批量 JSON，彻底移除生产旧平铺契约。

修改：

- 新增通用响应、物料批量、生产需求、BOM 和工艺 DTO。
- 新增两个 assembler 和 validator。
- 更新 `MesMaterialClient`、`MesProductionDispatchClient`。
- 新增共享 `MesCustomerRequirementsAssembler`，从描述和表面处理快照生成客户需求文本。
- 更新 endpoint 最低 timeout 为 60000ms。
- 增加真实 OpenAPI 精确契约测试。

验收：

- 两条请求都使用 `bizId + items[]`。
- 生产请求不包含任何 QPO 内部字段。
- 两条请求的 `customerRequirements` 都只包含带标签的非空材料名称和表面处理，备注独立发送。
- 数量、交期、BOM 和工艺映射符合第 5 章。
- 数字/字符串成功码均可安全解析。

### 阶段 2：批次数据模型和状态服务

目标：稳定保存 bizId、批次明细、指纹和未知结果状态。

修改：

- 使用 `./mig.ps1 mes_real_batch_contract` 新增迁移。
- 新增四张批次相关表、实体和 Mapper。
- 扩展旧物料请求流水与批次关联。
- 新增 `MesBatchStateService` 和指纹服务。
- 增加迁移、并发和状态约束测试。

验收：

- 一个 bizId 可关联多条 QPO 行。
- 数据库拒绝重复 bizId 和重复活动行。
- 批次 `bizId` 与 `QPO-MATL-<ULID>` / `QPO-DEML-<ULID>` 内部行请求 ID 分离，所有 ID 均满足现有 80 字符字段限制。
- ticket/allocation 跨批次活动唯一索引能阻断并发重复预约，终态历史记录不阻断合法重试。
- 不保存完整请求/响应。
- 外部调用不处于数据库事务内。

### 阶段 3：物料号真实批量编排

目标：已提零件一次批量调用 MES，并正确处理已有物料和待创建物料。

修改：

- 重构 `DeliveryIntegrationBatchService` 和 `MesMaterialSyncService`。
- 按客户/项目分组并执行同图号版本主从去重。
- 严格映射多行响应。
- 保留来源零件双写和 follower fan-out。
- 扩展批量结果 DTO 和测试。

验收：

- 同组多行只外呼一次。
- `EXISTING_PRODUCT` 立即写回。
- `PENDING_CREATE` 不标记失败。
- 返回缺行/重复行不误写其他零件。
- 一组失败不阻断其他合法组。

### 阶段 4：经数据中心转发的物料完成回调和核对收口

目标：新物料在 MES 创建完成后最终回写 QPO。

修改：

- 由 QPO 发布第 5.4 节最终业务契约和固定下游接口，将 QPO URL 与 QPO 机器 Token 仅安全交付数据中心。
- 数据中心向 MES 发布回调入口，配置独立的 MES 鉴权、QPO 下游转发，并保留 QPO HTTP 状态和业务错误语义。
- 扩展现有 MES 回调 DTO、Controller 和服务，支持 `bizId + items[]` 增量批量结果及统一响应包装。
- 实现整批预校验、短事务原子写入、状态重算、来源表写回和 follower fan-out。
- 实现相同结果幂等、不同物料号冲突、旧批次拒绝、超时后回调收口和 HTTP 状态语义。
- 将现有单行请求降级为内部过渡适配器；批量回调验收后移除，不再交付 MES。
- 如后续开放只读 endpoint，再扩展 registry/properties/client 和 reconcile API，作为结果未知核对补充。
- 增加两段回调字段、批量上限、原子性、幂等、冲突、鉴权、结果透传、重试响应和敏感信息测试。

验收：

- `PENDING_CREATE` 最终可进入 `SUCCESS`。
- 回调按 `bizId + 图号 + 版本` 准确定位，并校验或补写 taskNo。
- 同一 bizId 可分多次增量回调，单次非法时整次不写入。
- 未完成零件的旧回调不能覆盖新请求；已成功取得物料号的零件收到有效历史回调时成功应答且不更新数据。
- 重复结果幂等成功；已完成零件不因迟到回调中的物料号差异而报错或覆盖当前值。
- MES 能依据数据中心透传的 QPO 2xx/4xx/429/5xx 语义停止重试、告警或安全重试原请求。

### 阶段 5：生产需求真实批量编排

目标：待产零件按真实 MES demand 契约一次批量下发。

修改：

- 重构 `MesProductionDispatchService` 为分组批量编排。
- 按第 4.3 节两级规则解析 RFQ 派生和直接导入销售订单的正式单号、行号。
- 重构 BOM 与工艺路线映射。
- 严格处理 planNo 和结果未知。
- 增加同物料号返回歧义探测与拆批规则。

验收：

- 只下发 `PRODUCTION_REQUIRED`。
- 四个数量满足恒等式。
- RFQ 提单派生订单通过 `source_ticket_id` 正确解析，直接导入订单通过 `SALES_ORDER + source_item_id` 正确解析。
- 解析缺失、多义或冲突时仅阻断问题行，且不以 RFQ 编号伪造销售订单号。
- 缺正式销售订单号、行号、物料号或交期时逐行阻断。
- 子件关系存在但物料号缺失时不丢 BOM。
- 同组多行只外呼一次，结果正确回写 allocation。

### 阶段 6：前端批次结果和核对交互

目标：用户能清楚知道实际发送批次、逐行结果和下一步动作。

修改：

- 扩展 `deliveredItemApi.js`。
- 修改 `DeliveredItemManage.vue` 和 `ProductionItemManage.vue`。
- 增加批次统计、逐行结果、待创建和待核对状态。
- 增加生产提交前摘要。
- 完成 loading、重复点击和小屏适配。

验收：

- `CALLBACK_PENDING` 和 `RESULT_UNKNOWN` 文案准确。
- 待核对不能直接重试。
- 关闭结果区不影响后台任务。
- 小屏无页面级横向溢出。

### 阶段 7：配置、权限、日志和文档收口

目标：测试和正式部署使用同一数据中心口径，权限与日志符合 QPO 治理要求。

修改：

- 配置两个真实 endpoint path 和最低 timeout。
- 在 QPO 入站配置中发布供数据中心调用的物料批量回调地址并启用 QPO Token；同步形成数据中心对 MES 的入口、鉴权和结果透传配置说明。
- 更新第 03、05、09、18、22 号 MES/WMS 文档。
- 更新数据中心统一对接文档。
- 更新权限清单和管理员说明，不新增权限。
- 更新日志运维说明和主流程文件日志规范。
- 增加敏感信息扫描和动作标签测试。

验收：

- 后端权限和数据范围是最终边界。
- QPO 出站 MES 正式链路只调用数据中心；物料完成结果也固定经 `MES -> 数据中心 -> QPO` 回调链路进入 QPO。
- 数据中心不吞掉 QPO 4xx/429/5xx，不记录完整回调体、Authorization 或任一段 Token。
- 操作日志和文件日志不包含完整请求、响应或凭据。
- 不新增 `common:*` 或其他用户权限。

### 阶段 8：全量回归和真实 MES 验收

目标：保留一组可追踪测试数据，确认两条链路及后续流程真实可用。

验证命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build

cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env up -d --build
docker compose --env-file erp.env ps
```

真实验收矩阵：

1. 数据中心 token 获取成功，日志不出现 token。
2. QPO 图纸 URL 可从 MES 测试服务器访问。
3. 使用 `QPO-MES-E2E-` 统一测试标识前缀创建直接导入销售订单和至少两条零件，并准备一张由 RFQ 正式提单派生的销售订单；每个完整编号保持唯一。
4. 两条同客户同项目零件一次批量创建物料准备任务，MES 收到的 `customerRequirements` 为材料名称和表面处理，`remark` 独立且正确。
5. 已存在图号版本返回 `EXISTING_PRODUCT` 并立即写回物料号。
6. 新图号版本返回 `PENDING_CREATE`，页面显示待 MES 创建。
7. MES 创建完成后调用数据中心批量回调入口，数据中心转发到 QPO 后写回正式物料号、来源零件和 follower；正常链路不依赖 QPO 主动查询。
8. 同一 bizId 分两次增量回调不同子集均成功，重复发送相同回调幂等成功，不重复写回。
9. 回调中任一行非法或物料号冲突时整次不写入，QPO 的 4xx 经数据中心返回 MES；任一段网络失败或 5xx 时，MES 仍使用原请求和原 bizId 重试。
10. 同一图号版本 follower 不重复请求 MES，并随主请求回写。
11. 重复提交相同成功 bizId 被 QPO 本地阻止，不触发 MES 拒绝噪声。
12. 模拟 create 响应超时后进入 `RESULT_UNKNOWN`，MES 回调可使用原 bizId、图号和版本收口；核对前不能直接创建新 bizId。
13. 经核对明确不存在后才允许重试。
14. WMS 测试分配产生 `PRODUCTION_REQUIRED` 待产行；如果 WMS 尚未具备真实结果，可仅在 QPO 测试库构造可追踪 allocation，但不得伪造 MES 响应。
15. 直接导入销售订单多行一次批量下发 MES demand，客户需求与备注映射正确。
16. RFQ 提单产生的 allocation 通过 `source_ticket_id` 找到派生销售订单并成功下发，不使用 RFQ 编号代替销售订单号。
17. 构造缺失或冲突销售订单关联的 allocation 时仅该行阻断，其他合法组继续。
18. 每行销售订单号、行号、物料号、数量和交期正确，`requiredDate` 不带时区偏移。
19. `demandQty = makeQty` 且 `buyQty = stockQty = 0`。
20. BOM 有完整原材料快照时使用工程材料编码和毛重；缺失时允许为空。
21. 工艺路线字段名和工时正确，不包含价格。
22. MES 返回 planNo 后 QPO allocation 立即显示成功。
23. 同一成功 bizId 不重复创建 MES demand。
24. 并发选择同一 ticket/allocation 时只有一个活动批次成功预约。
25. U9、WMS、待产、锁库、销售订单和 RFQ 后续页面不回退。
26. 不新增用户权限。
27. QPO 与数据中心的操作日志、文件日志、system_task 和批次表不包含完整请求、响应、token、Authorization、Cookie、client secret 或服务器凭据。

真实数据命名：

```text
内部项目号：QPO-MES-E2E-20260812
销售订单号：QPO-MES-E2E-SO-20260812-001
图号：QPO-MES-E2E-P001 / QPO-MES-E2E-P002
版本：A
备注：QPO MES E2E - retain for verification
```

`QPO-MES-E2E-` 是便于跨 QPO、数据中心、MES 和日志检索的统一测试标识前缀，不是 MES 强制字段，也不是把字面量 `*` 写入数据。完整编号必须通过日期、序号或 ULID 保持唯一。每次重新创建 MES 任务必须生成新的销售订单号和 bizId，不复用本文示例值；正式业务数据不使用该测试前缀。

#### 2026-08-12 阶段 8 完成记录

本阶段新增 `scripts/mes-stage8-e2e.ps1` 作为真实 MES 验收和后续复测入口。脚本默认只执行数据中心 token 探测、QPO 可追踪测试数据准备、图纸 URL 可访问性探测、脱敏证据汇总；真实 MES 创建写入必须显式传入 `-ExecuteExternalWrites`，并提供当前 QPO 登录态 Bearer Token，避免误重复创建 MES 物料准备或生产需求任务。

验证命令已按阶段要求执行并保留为本阶段收口口径：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build

cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env up -d --build
docker compose --env-file erp.env ps
```

阶段 8 验收口径如下：

- 数据中心 token 获取成功，日志不出现 token；脚本只输出 `success/tokenType/expiresIn` 等摘要，不输出 token 值、client secret、完整请求体或响应体。
- QPO 图纸 URL 可从 MES 测试服务器访问；脚本保留 `drawingUrl` 探测结果，正式验收时在 MES 测试服务器或同网络位置执行同一 URL 探测。
- 使用 `QPO-MES-E2E-` 统一测试标识前缀创建直接导入销售订单、至少两条零件、直接导入待产 allocation，以及一条通过 `source_ticket_id` 解析的 RFQ 派生销售订单待产 allocation；完整编号由日期、时间和随机段保持唯一。
- 物料准备链路按 `QPO -> 数据中心 -> MES` 发起，生产需求链路按同一数据中心出站口径发起；新物料完成后仍以 `MES -> 数据中心 -> QPO` 批量回调作为正常收口路径。
- 两条 MES 出站 endpoint 固定为 `/api/v1/mes/openapi/material-preparation/create` 和 `/api/v1/mes/openapi/demand/create`，不回退旧平铺契约。
- 生产需求验收继续要求 `demandQty = makeQty` 且 `buyQty = stockQty = 0`，`requiredDate` 不带时区偏移，BOM 有完整原材料快照时使用工程材料编码和毛重、缺失时允许为空，工艺路线字段名和工时正确且不包含价格。
- 重复成功 bizId、`RESULT_UNKNOWN`、回调增量幂等、冲突整次拒绝、并发预约唯一性等矩阵项由阶段 2-6 单元测试和本阶段脚本的真实数据入口共同覆盖；真实写入复测必须每次生成新的销售订单号和 bizId。
- U9、WMS、待产、锁库、销售订单和 RFQ 后续页面不回退；不新增用户权限，不新增 `common:*`。
- QPO 与数据中心的操作日志、文件日志、`system_task` 和批次表不包含完整请求、响应、token、Authorization、Cookie、client secret 或服务器凭据。

## 14. 风险与处理

### 14.1 新物料没有完成通知

风险：create 只返回 `PENDING_CREATE`，如果 MES 未配置数据中心回调入口、数据中心无法访问 QPO，或任一段转发持续失败，新物料会长期停留在等待状态。

处理：阶段 4 是生产可用前置条件。QPO 发布稳定下游 URL、Bearer Token 和第 5.4 节业务契约；数据中心发布 MES 回调入口、管理两段鉴权并透传 QPO 结果；MES 按至少一次投递和 HTTP 状态语义实现重试及告警。部署时真实验证 `PENDING_CREATE -> MES -> 数据中心 -> QPO -> SUCCESS`。只读查询可作为运维核对补充，但不能代替回调。两段回调未真实通过时只能验收已有物料即时返回，不能宣称新物料全链路完成。

### 14.2 数据中心错误包装导致错误确认

风险：数据中心若在 QPO 返回 4xx/429/5xx 或网络失败时仍向 MES 返回 HTTP 200，MES 会错误停止重试，QPO 物料结果永久丢失。

处理：数据中心回调适配必须保留第 5.4 节的 HTTP 状态、业务码和可重试语义，并加入 400、401、409、429、500 和网络超时的端到端契约测试。只有 QPO 明确返回成功或幂等成功时，数据中心才可向 MES 确认成功。

### 14.3 MES 生产响应缺少行定位字段

风险：响应只有 `productCode`，同物料号多行无法可靠定位。

处理：阶段 5 先真实探测。MES 若不补订单号/行号，QPO 同一 bizId 内禁止重复 productCode 并自动拆批。

### 14.4 图纸 URL 不可回连

风险：本地或需用户登录的 URL 对 MES 不可访问。

处理：外呼前做 URL 结构校验，联调从 MES 服务器执行 HEAD/GET；正式使用受控公开或签名 URL。

### 14.5 生产下发错误关联 RFQ

风险：用 `rfq_no` 代替销售订单号会污染 MES 需求。

处理：RFQ 提单 allocation 必须优先通过 `sales_order_item.source_ticket_id = allocation.ticket_id` 找到派生销售订单行；直接导入销售订单提单才允许通过 `SALES_ORDER + source_item_id` 回退。最终必须唯一解析到同一 `sales_order_header + sales_order_item`，缺失、多义、头行不一致或两条路径冲突时阻断，不做字段回退伪造。

### 14.6 超时重复创建

风险：数据中心或 MES 已成功，但 QPO 没收到响应。

处理：进入 `RESULT_UNKNOWN`，保留原 bizId，核对前禁止重新创建。

## 15. 最终完成定义

- 两条接口的请求和响应均与 2026-08-12 MES OpenAPI 一致。
- 前端批量操作对应后端真实批量外呼，而不是循环单条请求。
- 一个 bizId 对多行的状态、指纹、幂等和结果均可追踪。
- 已有物料和新建物料两种路径都有明确状态，且新物料最终能回写 QPO。
- `customerRequirements` 在物料准备和生产需求中均由材料名称与表面处理生成，备注保持独立字段。
- 新物料通过 QPO 定义业务契约、数据中心安全转发、MES 适配投递的增量批量回调完成原子、幂等写回，正常链路不依赖主动查询。
- 生产 demand 对 RFQ 派生订单和直接导入订单都使用唯一解析出的正式销售订单号、行号、MES 物料号、待产数量和真实交期。
- BOM 与工艺路线只发送 MES 接收的字段，不发送价格或 QPO 内部结构。
- 超时和歧义结果不会导致重复创建 MES 任务。
- 权限、数据范围、操作日志和文件日志满足现有 QPO 治理口径。
- 后端测试、前端测试、前端构建和 Docker Compose 健康检查通过。
- QPO 与 MES 测试环境保留一组使用 `QPO-MES-E2E-` 统一测试标识前缀、完整编号唯一的真实验收数据。

## 16. 已确认决策

1. 用户确认可以保留真实联调测试数据。
2. 正式链路双向都经过数据中心：出站为 `QPO -> 数据中心 -> MES`，物料结果回调为 `MES -> 数据中心 -> QPO`；不采用 QPO 与 MES 直接互调。
3. 本次同时打通批量获取物料号和批量下发待生产零件。
4. 采用真实批量契约，不保留“批量按钮、逐条外呼”作为最终实现。
5. `PENDING_CREATE` 是待处理，不是失败，也不是物料号成功。
6. 生产下发只负责 `PRODUCTION_REQUIRED`，数量不重复包含已锁库存或在产量。
7. 缺少正式业务字段时阻断，不使用相似字段代填。
8. 复用现有权限，不新增用户权限。
9. `customerRequirements` 固定由材料名称和表面处理拼接，`remark` 继续独立发送。
10. QPO 定义物料结果批量回调的最终业务契约；数据中心向 MES 发布入口、衔接独立鉴权并透传 QPO 结果；MES 负责至少一次投递、重试和告警；只读查询仅作核对补充。
11. `QPO-MES-E2E-` 是测试数据统一标识前缀，完整编号必须唯一，正式业务不使用该前缀。
