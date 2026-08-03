# QPO 对接 MES 物料号接口说明

本文档提供给 MES 系统开发/实施人员，用于说明 QPO 如何调用 MES 获取已提零件的物料号。

> 更新说明：本文档原先只描述 MES 在 HTTP response 中同步返回物料号的第一版接口。根据最新联调要求，MES 可以即时返回物料号，也可以先返回“已受理，后续回调”，再通过 QPO 回调接口写回物料号。MES 回调 QPO 的完整接口契约已经拆分为独立文档：`docs/QPO-MES-WMS对接/08-MES物料号回调接口对接说明.md`。

> 阶段 5 收口说明：旧同步响应口径已废弃。MES 可以即时返回物料号，也可以返回已受理并后续回调；两种路径都必须原样返回 QPO 生成的 `mesRequestId`。

> 阶段 6 收口说明：QPO 已支持同图号版本去重。去重键固定为 **图号 + 版本**，不区分客户、订单、图纸文件或内部项目号。相同 `图号 + 版本` 已有活跃主请求时，后续行会成为 QPO 内部关联等待行，不再重复请求 MES；已有唯一成功物料号时，后续行直接复用。MES 仍只回调主 `mesRequestId`，QPO fan-out 写回所有关联等待行。

> 系统配置阶段 6 收口说明：本文档只描述 QPO 通过数据中心获取 MES 物料号的业务接口口径。MES 回调 QPO 的接口详情见 `08-MES物料号回调接口对接说明.md`；回调 URL 和 token 由 QPO 管理员从系统配置页面的入站接口提供给数据中心，真实 token 单独交付数据中心，不写入本文档。

## 1. 对接结论

- 调用方向：QPO -> MES。
- 调用方式：HTTP `POST`，请求体为 `application/json`。
- 返回方式：MES 可以在同一次 HTTP 请求的响应体中同步返回物料号，也可以返回已受理并后续回调。
- 回调要求：新口径需要 MES 响应和回调都原样带回 QPO 生成的 `mesRequestId`，后续通过 QPO 回调接口写回物料号；同图号版本去重场景下，MES 仍只回调主 `mesRequestId`。
- 文件访问：QPO 会传 `drawingUrl`，如果 MES 需要下载图纸，MES 服务器必须能在内网访问该 URL。

简单理解：

```text
用户在 QPO 点击“获取物料号”
  -> QPO 创建内部异步任务
  -> 任务里 QPO POST 调 MES
  -> MES 立即在 HTTP response 里返回 materialNo，或返回已受理待回调
  -> 如果立即返回 materialNo，QPO 保存 materialNo 和同步状态
  -> 如果待回调，QPO 标记为待MES回调，MES 后续按主 mesRequestId 回调 QPO
  -> 如果有相同图号版本等待行，QPO 自行 fan-out 写回等待行，MES 不需要逐行回调
```

## 2. MES 需要提供给 QPO 的信息

请 MES 提供以下信息，用于 QPO 配置和联调：

| 信息 | 示例 | 说明 |
| --- | --- | --- |
| MES 内网访问地址 | `http://mes-server:9000` | QPO 后端所在机器必须能访问。 |
| 物料号接口路径 | `/api/mes/materials/resolve` | QPO 默认路径，可按 MES 实际路径配置。 |
| 认证方式 | `Bearer Token` / 无认证 | 当前 QPO 支持可选 `Authorization: Bearer {token}`。 |
| 测试环境地址 | `http://mes-test:9000` | 用于联调。 |
| 生产环境地址 | `http://mes-prod:9000` | 上线前配置。 |
| 超时时间建议 | `10000 ms` | QPO 当前默认 10 秒。 |
| 并发能力 | 例如 `4` 个并发请求 | QPO 默认后台任务线程池最大 4 个任务并行。 |
| 错误码/错误信息规则 | JSON 示例 | 方便 QPO 页面展示失败原因。 |

QPO 侧配置项：

```env
ERP_MES_BASE_URL=http://mes-server:9000
ERP_MES_AUTH_TOKEN=
ERP_MES_MATERIAL_PATH=/api/mes/materials/resolve
ERP_MES_TIMEOUT_MS=10000
ERP_PUBLIC_BASE_URL=http://192.168.70.21:8088
```

`ERP_PUBLIC_BASE_URL` 用于生成图纸的绝对访问地址。如果 MES 要下载 `drawingUrl`，这个地址必须是 MES 能访问到的内网地址。

## 3. HTTP 契约

### 3.1 请求

```http
POST {ERP_MES_BASE_URL}{ERP_MES_MATERIAL_PATH}
Content-Type: application/json
Authorization: Bearer {ERP_MES_AUTH_TOKEN}
```

说明：

- `Authorization` 只有在 QPO 配置了 `ERP_MES_AUTH_TOKEN` 时才会发送。
- 如果 MES 不需要认证，可以不配置 token。
- 请 MES 尽量返回 `Content-Type: application/json`。

### 3.2 请求 JSON 示例

```json
{
  "mesRequestId": "MES-123-8f8c2",
  "ticketId": 123,
  "rfqId": 88,
  "rfqNo": "RFQ-20260721-001",
  "customerName": "客户A",
  "partNo": "P-1001",
  "version": "A",
  "drawingUrl": "http://192.168.70.21:8088/uploads/drawings/xxx.pdf",
  "internalProjectNo": "IP-20260721-001",
  "sourceType": "RFQ",
  "sourceOrderId": 88,
  "sourceItemId": 10086
}
```

### 3.3 请求字段

| 字段 | 类型 | 是否可能为空 | 说明 |
| --- | --- | --- | --- |
| `mesRequestId` | string | 否 | QPO 本次请求唯一标识。MES response 和后续 callback 必须原样返回。 |
| `ticketId` | integer | 否 | QPO 已提零件行 ID，可作为幂等键之一。 |
| `rfqId` | integer | 通常否 | QPO 订单/RFQ ID。 |
| `rfqNo` | string | 可能为空 | RFQ 编号。 |
| `customerName` | string | 可能为空 | 客户名称。 |
| `partNo` | string | 否 | 图号，QPO 调用前会校验非空。 |
| `version` | string | 可能为空字符串 | 版本，QPO 无版本时传空字符串。 |
| `drawingUrl` | string | 否 | 图纸绝对 URL，QPO 调用前会校验非空。 |
| `internalProjectNo` | string | 否 | 内部项目号，QPO 调用前会校验非空。 |
| `sourceType` | string | 否 | 来源类型，当前可能为 `RFQ` 或 `SALES_ORDER`。 |
| `sourceOrderId` | number | 可能为空 | 来源订单 ID。 |
| `sourceItemId` | number | 可能为空 | 来源零件 ID。 |

## 4. MES 响应要求

### 4.1 即时成功响应

HTTP 状态码建议返回 `200`，响应体：

```json
{
  "success": true,
  "status": "SUCCESS",
  "materialNo": "MAT-000123",
  "message": "物料号创建成功",
  "externalTraceId": "MES-TRACE-001",
  "mesRequestId": "MES-123-8f8c2",
  "callbackRequired": false
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `success` | boolean | 是 | `true` 表示 MES 处理成功。 |
| `materialNo` | string | 成功时必填 | MES 返回给 QPO 保存的物料号。 |
| `status` | string | 建议 | 即时成功返回 `SUCCESS`。 |
| `mesRequestId` | string | 是 | 必须等于 QPO 请求中的 `mesRequestId`。 |
| `callbackRequired` | boolean | 建议 | 即时成功返回 `false`。 |
| `message` | string | 否 | 成功或失败说明，会进入 QPO 状态信息。 |
| `externalTraceId` | string | 否 | MES 侧流水号，建议返回，方便双方排查。 |

QPO 成功判定：

- HTTP 为 2xx；
- `success=true`；
- `materialNo` 非空；
- `mesRequestId` 等于 QPO 请求中的 `mesRequestId`；
- `materialNo` 去掉首尾空格后长度不超过 100。

### 4.2 已受理待回调响应

当 MES 需要人工或长时间处理物料号时，HTTP 仍建议返回 `200`，响应体：

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

QPO 待回调判定：

- HTTP 为 2xx；
- `success=true`；
- `callbackRequired=true`，或 `status` 为 `CALLBACK_PENDING` / `ACCEPTED` / `PENDING`；
- `mesRequestId` 等于 QPO 请求中的 `mesRequestId`。

QPO 处理结果：

- 已提零件 `mes_sync_status = CALLBACK_PENDING`，页面显示 `待MES回调`；
- `system_task` 按成功完成，不长时间等待 MES 人工处理；
- 批量任务中该行计入待回调数量，不计入失败。

### 4.3 失败响应

推荐失败也返回 HTTP `200`，并在响应体中说明业务失败原因：

```json
{
  "success": false,
  "message": "图纸无法识别，缺少材料信息",
  "externalTraceId": "MES-TRACE-002",
  "mesRequestId": "MES-123-8f8c2"
}
```

QPO 失败判定：

- HTTP 非 2xx；
- 连接超时或读取超时；
- 响应体为空；
- `success` 不是 `true`；
- `success=true` 但既没有 `materialNo`，也没有明确 `callbackRequired=true` / 待回调状态；
- `mesRequestId` 缺失或不等于 QPO 请求中的 `mesRequestId`；
- `materialNo` 长度超过 100。

失败后 QPO 会把该已提零件的 MES 状态标记为 `FAILED`，用户可重新触发。

## 5. 批量选择时的处理方式

QPO 前端支持批量选择多个已提零件后提交“获取物料号”任务。

对 MES 来说，批量不是一个大 JSON 数组请求。当前 QPO 的处理方式是：

```text
用户选择 N 个零件
  -> QPO 创建 1 个批量 system_task
  -> 任务内部按 ticketId 顺序循环
  -> 每行先按 图号 + 版本 做 QPO 内部去重判断
  -> 只有 MASTER 主请求行会 POST 调 MES
  -> FOLLOWER 关联等待行不调用 MES，等待主请求回调或即时成功分发
  -> REUSED 复用行直接使用同图号版本唯一已确认物料号
  -> 最后 QPO 汇总成功、失败、跳过、关联待回调、复用和实际提交 MES 数量
```

也就是说，MES 仍会收到单零件请求，而不是一次包含多个零件的大请求；但同图号版本去重后，MES 只会收到需要真实处理的主请求，不会收到 QPO 内部关联等待行的重复请求。

并发说明：

- 一个 QPO 批量任务内部默认顺序处理，不会把同一批零件全部同时打到 MES。
- 多个用户或多个后台任务同时运行时，MES 可能同时收到多个 QPO 请求。
- QPO 当前后台任务线程池默认：核心线程 `2`，最大线程 `4`，队列 `50`。
- 建议 MES 至少支持 `4` 个来自 QPO 的并发请求；如果 MES 支持更低并发，请提前告知 QPO 侧调整任务线程池或增加限流。
- 批量中如果某行进入 `CALLBACK_PENDING`，QPO 会继续处理后续行，最终汇总 `callbackPendingCount`。
- 同图号版本去重汇总字段：`dedupFollowerCount` 表示关联等待未重复发 MES 的数量；`reusedMaterialCount` 表示复用已确认物料号的数量；`submittedToMesCount` 表示实际提交 MES 的主请求数量。

## 6. 数据中心回调 QPO（业务来源 MES）

MES 返回待回调后，后续由数据中心调用 QPO 回调接口，把 MES 正式物料号结果写回 QPO。回调接口已经独立成文档，本文只保留入口和关键口径：

- 独立文档：`docs/QPO-MES-WMS对接/08-MES物料号回调接口对接说明.md`。
- 接口入口：`POST {QPO_PUBLIC_BASE_URL}/api/integrations/mes/material-callback`；`QPO_PUBLIC_BASE_URL` 由部署后的可访问地址确定。
- 认证方式：`Authorization: Bearer {QPO_INBOUND_CALLBACK_TOKEN}`；回调 URL 和 token 由 QPO 管理员从系统配置页面的入站接口提供给数据中心，真实 token 单独通过受控渠道交付数据中心。回调接口详情见 `08-MES物料号回调接口对接说明.md`。
- 定位方式：数据中心转发时必须原样带回 QPO 请求中的主 `mesRequestId`，并带回 `ticketId` 做交叉校验。
- 去重口径：同图号版本去重场景下，数据中心只需要转发主 `mesRequestId` 的 MES 结果；关联等待行是 QPO 内部关系，数据中心和 MES 都不需要分别回调每个相同图号版本零件。
- 写回口径：QPO 收到主请求成功回调后写回 `order_delivery_ticket.material_no`，并 fan-out 写回所有关联等待行；销售订单来源提单行还会同步回写 `sales_order_item.material_no` 与物料号确认字段。

回调请求字段、响应字段、错误码、幂等规则、冲突规则和联调样例，以 `08-MES物料号回调接口对接说明.md` 为准。

## 7. 幂等与重复请求建议

QPO 正常情况下不会在已成功拿到物料号后重复提交同一行；但以下情况可能出现重复请求：

- 用户对失败任务点击重试；
- 网络超时导致 QPO 不知道 MES 是否已经处理成功；
- 测试/联调时重复提交同一条数据。

建议 MES 以 `mesRequestId` 作为一次请求和后续回调的幂等主键，同时可结合 `ticketId`、`internalProjectNo + partNo + version` 做业务幂等判断：

- 如果同一零件已经创建过物料号，建议直接返回原 `materialNo`；
- 不建议因为重复请求创建多个不同物料号；
- 如果 MES 认为请求重复但不能返回物料号，请返回 `success=false` 和明确 `message`。

## 8. 联调验收用例

请 MES 至少支持以下联调用例：

| 场景 | MES 返回 | QPO 预期 |
| --- | --- | --- |
| 正常创建物料号 | `success=true` + `materialNo` | QPO 保存物料号，状态 `SUCCESS`。 |
| 已存在物料号 | `success=true` + 已有 `materialNo` | QPO 保存/保持物料号，状态 `SUCCESS`。 |
| 需要人工维护物料号 | `success=true` + `status=CALLBACK_PENDING` + `callbackRequired=true` + `mesRequestId` | QPO 状态 `CALLBACK_PENDING`，任务成功完成。 |
| 待回调后成功 | `POST /api/integrations/mes/material-callback` + `materialNo` | QPO 状态 `SUCCESS`，保存物料号。 |
| 重复相同回调 | 相同 `mesRequestId` + 相同 `materialNo` | QPO 返回成功，数据不重复变更。 |
| 冲突回调 | 相同 `mesRequestId` + 不同 `materialNo` | QPO 拒绝覆盖，记录冲突。 |
| 图纸不可识别 | `success=false` + `message` | QPO 状态 `FAILED`，显示失败原因。 |
| MES 内部异常 | HTTP 500 或 `success=false` | QPO 状态 `FAILED`。 |
| 响应超时 | 无响应超过超时时间 | QPO 状态 `FAILED`，用户可重试。 |
| `materialNo` 为空且无待回调标识 | `success=true` 但无物料号、无 `callbackRequired` | QPO 状态 `FAILED`。 |
| 回调 token 错误 | 未带或带错 `QPO_INBOUND_CALLBACK_TOKEN` | QPO 拒绝。 |

## 9. 双方上线前确认

- QPO 后端服务器能访问 MES 的测试/生产地址。
- MES 如需下载图纸，MES 服务器能访问 QPO 发出的 `drawingUrl`。
- QPO 和数据中心确认 QPO 入站回调 URL、`QPO_INBOUND_CALLBACK_TOKEN`、IP 白名单、防火墙策略；QPO 和 MES 确认物料号业务字段与响应口径。
- 双方确认超时时间、并发上限、是否需要限流。
- MES 返回 JSON 字段名与本文档一致，尤其是 `success`、`status`、`callbackRequired`、`materialNo`、`message`、`mesRequestId`。
- 双方约定联调样例数据，并保留 `ticketId` / `mesRequestId` / `externalTraceId` 方便查日志。
