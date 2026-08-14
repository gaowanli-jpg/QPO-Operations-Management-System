# QPO 提供给数据中心的 MES 物料号回调接口对接说明

本文档是 QPO 提供给数据中心联调方的入站接口文档。业务来源仍是 MES：当 MES 在物料号无法即时返回、后续生成正式物料号后，由数据中心携带 QPO 入站 token 调用 QPO，把物料号结果写回 QPO。

## 1. 接口结论

- 交付对象：数据中心。
- 调用方向：数据中心 -> QPO。
- 业务来源：MES。
- 调用时机：MES 在 QPO 获取物料号请求中返回 `callbackRequired=true`，或返回 `status=CALLBACK_PENDING / ACCEPTED / PENDING` 后，后续生成正式物料号时由数据中心转发调用。
- 接口路径：`POST /api/integrations/mes/material-callback`。
- 请求格式：`application/json`。
- 认证方式：`Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}`。
- 鉴权边界：该接口不走普通用户登录态，已从前端登录拦截中排除；后端最终安全边界是入站接口机器 token 校验。
- 配置来源：优先读取系统配置页 **入站接口 > MES 物料号回调** 的 DB 配置；页面配置不存在时，才兼容 `ERP_PUBLIC_BASE_URL`、`ERP_MES_CALLBACK_TOKEN`、`ERP_MES_CALLBACK_TOKEN_ENABLED` 等老环境 fallback。
- 主定位键：`mesRequestId`，值必须等于 QPO 出站请求中的批量 `bizId`，当前格式为 `QPO-MAT-{yyyyMMdd}-{ulid}`。
- 内部定位：QPO 根据 `mesRequestId` 查询 `mes_integration_batch` 批次，再按 `items[].drawingNo + items[].drawingVersion` 匹配批次内行；`ticketId`、行级流水号和 QPO 内部 ID 不要求也不接受数据中心回传。
- 去重口径：数据中心只需要转发主 `mesRequestId` 的 MES 结果；同图号版本的 follower 等待行由 QPO 内部 fan-out 写回，数据中心和 MES 都不需要逐行回调。

## 2. 前置约定

QPO 通过数据中心请求 MES 获取物料号时，会在请求体中传入：

```json
{
  "bizId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "customerCode": "CUST-001",
  "customerName": "客户A",
  "internalProjectNo": "IP-20260721-001",
  "items": [
    {
      "drawingNo": "P-1001",
      "drawingVersion": "A",
      "drawingUrls": [
        "http://192.168.70.21:8088/uploads/drawings/xxx.pdf"
      ],
      "remark": "备注"
    }
  ]
}
```

数据中心转发 MES 回调时，只需要取出 QPO 请求中的 `bizId` 值，并在回调 QPO 时放入 `mesRequestId` 字段。回调接口使用 QPO 自己的批量 envelope 契约，不使用 MES 响应的 `code/msg/data` 包裹。即使只有 1 个零件，也必须放在 `items` 数组中回调：

- `mesRequestId` 是 QPO 每次批量请求生成的唯一 ID，也是回调主定位键，值来自 QPO 出站请求里的 `bizId`。
- `items[].drawingNo + items[].drawingVersion` 是批次内行定位键，必须原样返回 QPO 出站请求中的图号和版本；没有版本时传空字符串。
- `items[].productCode` 是 MES 最终确认的物料号；QPO 会写回为内部 `materialNo`。
- QPO 不再向 MES 传 `ticketId`、`sourceType`、`rfqId`、`orderNo`、`rfqNo`、`sourceOrderId`、`sourceItemId`；数据中心回调 QPO 时也不需要回传这些字段。
- 如果 QPO 同图号版本去重后把其他零件作为 follower 关联到主请求，数据中心和 MES 都不需要知道 follower 明细，也不需要分别回调 follower。

## 3. HTTP 契约

```http
POST {QPO_PUBLIC_BASE_URL}/api/integrations/mes/material-callback
Content-Type: application/json
Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}
```

说明：

- `QPO_PUBLIC_BASE_URL` 是 QPO 提供给数据中心可访问的内网地址，例如 `http://192.168.70.21:8088`；正式 URL 需要等部署网络、端口、域名或反向代理确定后再给数据中心。
- `QPO_INBOUND_CALLBACK_TOKEN` 是 QPO 侧生成的入站回调机器 token，占位符不是实际 token。真实 token 由 QPO 管理员在 **系统配置 > 入站接口 > MES 物料号回调** 生成或轮换，明文只展示一次，并通过受控渠道单独交付数据中心。
- 当 QPO 入站接口配置开启 token 校验时，数据中心必须携带 `Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}`。
- `ERP_MES_CALLBACK_TOKEN` 仅作为 QPO 侧老环境 deprecated fallback；页面入站接口配置存在时，以页面配置为准。数据中心联调方不需要也不应依赖该环境变量名。
- 只要请求已经到达 QPO 回调接口，HTTP 状态码统一返回 `200`，表示网络和接口可达；业务处理结果请读取响应体里的 `success`、`code`、`msg`。
- 如果页面配置存在但未生成 token，且 token 校验开启，即使老环境变量里配置了 token，也会按当前 DB 配置校验，并在响应体返回 `code=401`；因此生产联调应以页面展示的 token 状态为准。
- token 错误、缺失或格式不是 `Bearer xxx` 时，QPO 仍返回 HTTP `200`，但响应体返回 `success=false`、`code=401`。
- 不要在日志、截图、工单或联调群中明文发送真实 token。

### 3.1 联调交付信息

把本接口交给数据中心前，QPO 侧需要补齐下面两项运行时信息：

| 项目 | 当前口径 | 交付给数据中心的形式 |
| --- | --- | --- |
| 回调 URL | 部署后确定，格式为 `{QPO_PUBLIC_BASE_URL}/api/integrations/mes/material-callback` | 例如 `http://qpo-host:8088/api/integrations/mes/material-callback` |
| 回调 token | 由 QPO 管理员在 **系统配置 > 入站接口 > MES 物料号回调** 生成或轮换，明文只展示一次 | 只提供 token 值本身，数据中心调用 QPO 时放入 `Authorization: Bearer {token}` |

数据中心拿到 **回调 URL + 回调 token + 本文档请求/响应字段说明** 后，即可调用本接口。联调时建议先用一条 QPO 已进入 `CALLBACK_PENDING` 的 `mesRequestId` 做成功回调验证，再验证 token 错误、重复回调、冲突回调等异常场景。

## 4. 成功回调

### 4.1 请求示例

```json
{
  "mesRequestId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "items": [
    {
      "taskNo": "MP202608140010",
      "drawingNo": "P-1001",
      "drawingVersion": "A",
      "status": "SUCCESS",
      "productCode": "MAT-000123",
      "productName": "业务演示成品",
      "message": "MES material resolved"
    }
  ]
}
```

### 4.2 请求字段

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `mesRequestId` | string | 是 | QPO 批量请求 ID，必须等于 QPO 调 MES 时传入的 `bizId`。 |
| `items` | array | 是 | 回调行数组；单条回调也必须传 1 行。 |
| `items[].taskNo` | string | 是 | MES 任务号、流水号或工单号，QPO 会保存用于排查。 |
| `items[].drawingNo` | string | 是 | 图号，必须等于 QPO 出站请求中对应行的 `drawingNo`。 |
| `items[].drawingVersion` | string | 可为空字符串 | 版本，必须等于 QPO 出站请求中对应行的 `drawingVersion`；无版本传空字符串。 |
| `items[].status` | string | 是 | 成功回调固定传 `SUCCESS`。 |
| `items[].productCode` | string | 成功时必填 | MES 最终确认的物料号；QPO 会去除首尾空格，长度不能超过 100。 |
| `items[].productName` | string | 否 | MES 产品名称，QPO 当前记录在批次行上用于排查。 |
| `items[].message` / `items[].msg` | string | 否 | 回调摘要，不要放完整 MES 内部任务 JSON。 |

### 4.3 成功响应

```json
{
  "success": true,
  "code": 0,
  "msg": "accepted",
  "mesRequestId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "data": {
    "bizId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
    "receivedCount": 1,
    "appliedCount": 1,
    "duplicateCount": 0,
    "sourceSyncTargetCount": 1,
    "sourceSyncUpdatedCount": 1,
    "sourceSyncIdempotentCount": 0
  }
}
```

响应字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `success` | boolean | QPO 是否成功处理该回调。 |
| `msg` | string | QPO 处理摘要。 |
| `mesRequestId` | string | 本次回调对应的 QPO 请求 ID。 |
| `data.bizId` | string | 本次回调对应的 QPO 批量请求 ID。 |
| `data.receivedCount` | number | QPO 收到的回调行数。 |
| `data.appliedCount` | number | 本次实际写回的行数；重复幂等行不计入。 |
| `data.duplicateCount` | number | 本次识别为重复幂等的行数。 |
| `data.sourceSyncTargetCount` | number | 需要同步回写来源零件主表的数量。 |
| `data.sourceSyncUpdatedCount` | number | 已实际同步回写来源零件主表的数量。 |
| `data.sourceSyncIdempotentCount` | number | 来源零件主表已是同一物料号、无需重复写入的数量。 |

## 5. 失败回调

如果 MES 后续确认无法生成物料号，可以回调失败状态，让 QPO 从 `CALLBACK_PENDING` 结束为 `FAILED`，用户后续可重新发起获取物料号。

### 5.1 请求示例

```json
{
  "mesRequestId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "items": [
    {
      "taskNo": "MP202608140010",
      "drawingNo": "P-1001",
      "drawingVersion": "A",
      "status": "FAILED",
      "message": "图纸无法识别，缺少材料信息"
    }
  ]
}
```

### 5.2 QPO 处理结果

- QPO 将对应 `order_delivery_ticket.mes_sync_status` 更新为 `FAILED`。
- QPO 将失败摘要写入状态信息和请求流水。
- 如果该主请求存在同图号版本 follower 等待行，QPO 会把等待行同步更新为失败。
- HTTP 层返回 `200`，响应体中 `success=true`、`msg=accepted`，表示 QPO 已接收并应用该失败行；业务行状态会更新为 `FAILED`。

失败响应示例：

```json
{
  "success": true,
  "code": 0,
  "msg": "accepted",
  "mesRequestId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "data": {
    "bizId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
    "receivedCount": 1,
    "appliedCount": 1,
    "duplicateCount": 0,
    "sourceSyncTargetCount": 0,
    "sourceSyncUpdatedCount": 0,
    "sourceSyncIdempotentCount": 0
  }
}
```

## 6. QPO 写回规则

QPO 收到回调后按以下顺序处理：

1. 校验回调 token。
2. 校验 `mesRequestId` 不为空，且 `items` 必须存在并至少 1 行。
3. 按 `mesRequestId` 查询 `mes_integration_batch` 批次。
4. 按每个 `items[].drawingNo + items[].drawingVersion` 匹配 `mes_material_batch_item` 批次行；无法匹配时响应体返回 `code=409`。
5. 对匹配到的行加载其行级请求流水 `order_delivery_ticket_mes_request` 和提单行。
6. 校验当前 `order_delivery_ticket.mes_request_id` 仍等于批次行的 `lineRequestId`，旧回调不能覆盖新请求。
7. 成功行校验 `items[].productCode` 非空且长度不超过 100，并写回 `order_delivery_ticket.material_no`、`mes_sync_status=SUCCESS`、`mes_synced_at`、`mes_external_task_id`、`mes_callback_received_at` 和回调摘要。
8. 失败行写回 `mes_sync_status=FAILED`、失败摘要、外部任务号和完成时间。
9. 更新批次行、行级请求流水和批次汇总状态；销售订单来源提单行还会同步回写 `sales_order_item.material_no` 与物料号确认字段。
10. 如果当前行是同图号版本去重主请求，QPO fan-out 写回所有 follower 等待行。

## 7. 同图号版本去重回调规则

QPO 当前获取物料号模块已经支持按 **图号 + 版本** 去重：

- 同一批量中相同 `图号 + 版本` 只会有一行成为 MASTER 并真实请求 MES。
- 跨批次有相同 `图号 + 版本` 活跃主请求时，后续行成为 FOLLOWER，不再请求 MES。
- 跨批次已有唯一成功物料号时，后续行直接复用，不请求 MES。
- 跨批次存在多个不同成功物料号时，QPO 不自动复用，按正常请求重新调用 MES。

因此 MES 回调时只需要处理 MASTER：

```text
MES 回调 MASTER mesRequestId
  -> QPO 写回 MASTER 提单行
  -> QPO 查找 order_delivery_ticket_mes_waiter
  -> QPO 写回所有 FOLLOWER 提单行
  -> 如果 FOLLOWER 来源是销售订单，同步回写 sales_order_item 物料号确认字段
```

MES 不需要：

- 回调 follower，或感知任何 QPO 内部 `ticketId`。
- 感知 QPO 内部的 `mesDedupKey`。
- 感知 QPO 内部的 `mesMasterTicketId` 或 `mesMasterRequestId`。

## 8. 幂等和冲突规则

| 场景 | QPO 处理 |
| --- | --- |
| 相同 `mesRequestId + drawingNo + drawingVersion` + 相同 `items[].productCode` 重复回调 | 幂等返回成功，不重复污染数据。 |
| 相同 `mesRequestId + drawingNo + drawingVersion` + 不同 `items[].productCode` | HTTP `200`，响应体 `code=409`，拒绝覆盖已确认物料号。 |
| 旧 `mesRequestId` 回调到当前已有新请求的 ticket | 请求流水标记为 `STALE`，HTTP `200`，响应体 `code=409`，旧回调不能覆盖新请求。 |
| 销售订单零件已有不同且已确认物料号 | HTTP `200`，响应体 `code=409`，不覆盖销售订单主零件表。 |
| 找不到 `mesRequestId` 对应批次 | HTTP `200`，响应体 `code=404`。 |
| 缺少 `mesRequestId`、缺少 `items`、`items` 为空，或成功行缺少 `items[].productCode` | HTTP `200`，响应体 `code=400`。 |
| token 缺失或错误 | HTTP `200`，响应体 `code=401`。 |

## 9. 错误响应示例

### 9.1 token 错误

HTTP `200`，响应体 `code=401`

```json
{
  "success": false,
  "code": 401,
  "msg": "QPO 入站回调 token 无效",
  "mesRequestId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "data": {
    "bizId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
    "receivedCount": 1,
    "appliedCount": 0,
    "duplicateCount": 0,
    "errors": ["QPO 入站回调 token 无效"]
  }
}
```

### 9.2 批次不存在

HTTP `200`，响应体 `code=404`

```json
{
  "success": false,
  "code": 404,
  "msg": "MES material batch bizId does not exist",
  "mesRequestId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "data": {
    "bizId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
    "receivedCount": 1,
    "appliedCount": 0,
    "duplicateCount": 0,
    "errors": ["MES material batch bizId does not exist"]
  }
}
```

### 9.3 物料号冲突或回调行不匹配

HTTP `200`，响应体 `code=409`

```json
{
  "success": false,
  "code": 409,
  "msg": "MES material callback row does not match current QPO batch item",
  "mesRequestId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
  "data": {
    "bizId": "QPO-MAT-20260814-01KZZ08THK316WKM13FFENVJD0",
    "receivedCount": 1,
    "appliedCount": 0,
    "duplicateCount": 0,
    "errors": ["MES material callback row does not match current QPO batch item"]
  }
}
```

## 10. 联调验收用例

| 场景 | 数据中心转发的 MES 结果 | QPO 预期 |
| --- | --- | --- |
| 待回调成功 | 回调 `items[].status=SUCCESS` + `items[].productCode` | 已提零件状态变为 `SUCCESS`，保存物料号。 |
| 待回调失败 | 回调 `items[].status=FAILED` + `items[].message`，不传 `productCode` | 已提零件状态变为 `FAILED`，用户可重试。 |
| 销售订单来源成功 | 回调销售订单来源提单行 | `sales_order_item.material_no` 和 `material_no_status=已确认` 被回写。 |
| 同图号版本主请求成功 | 只回调 MASTER 所在批次和行 | MASTER 和所有 FOLLOWER 等待行都写回物料号。 |
| 重复相同回调 | 重复发送相同 `items[].productCode` | QPO 幂等返回成功。 |
| 冲突回调 | 同一批次同一图号版本发送不同 `items[].productCode` | HTTP `200`，响应体 `code=409`，不覆盖数据。 |
| 旧请求回调 | ticket 已经有新的 `mesRequestId` | HTTP `200`，响应体 `code=409`，旧请求不覆盖新请求。 |
| token 错误 | 不带或带错 Bearer token | HTTP `200`，响应体 `code=401`。 |

## 11. 日志和安全要求

- QPO 文件日志会记录内部 `ticketId`、`mesRequestId`、`externalTraceId`、`materialNo`、`durationMs` 和失败摘要，用于 QPO 内部排查；日志不得把完整回调请求体作为外部契约扩散。
- QPO 操作日志动作包括 `DELIVERY_MES_MATERIAL_CALLBACK_SUCCESS`、`DELIVERY_MES_MATERIAL_CALLBACK_FAILED`、`DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT` 和去重 fan-out 相关动作。
- 日志不得记录 token、Authorization header、完整 MES 请求 JSON、完整入站回调 JSON 或 MES 内部完整任务 JSON。
- 回调 `message` 建议只放简短摘要，不要放敏感信息、图纸内容、完整 BOM 或内部审批详情。

## 12. 双方上线前确认

- QPO 提供数据中心可访问的公网或内网 base URL。
- QPO 和数据中心确认 `QPO_INBOUND_CALLBACK_TOKEN` 的发放、轮换和保密方式；真实 token 单独交付数据中心，不写入文档、日志、截图或工单。
- 数据中心确认只转发主 `mesRequestId` 的 MES 结果，不需要逐个回调同图号版本 follower。
- 数据中心确认失败场景会转发 `status=FAILED` 和明确 `message`，或由 QPO 用户重试触发下一次请求。
- 双方保留 `mesRequestId`、`externalTraceId` 作为联调排查要素；`ticketId` 仅作为 QPO 内部日志和请求流水排查字段。
