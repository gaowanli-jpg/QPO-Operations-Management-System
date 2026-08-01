# QPO 对接 MES 物料号接口说明

本文档提供给 MES 系统开发/实施人员，用于说明 QPO 如何调用 MES 获取已提零件的物料号。

> 更新说明：本文档原先只描述 MES 在 HTTP response 中同步返回物料号的第一版接口。根据最新联调要求，MES 可以即时返回物料号，也可以先返回“已受理，后续回调”，再通过 QPO 回调接口写回物料号。异步回调的完整契约以 `docs/QPO报价状态流转/18-MES物料号异步回调开发文档.md` 为准。

## 1. 对接结论

- 调用方向：QPO -> MES。
- 调用方式：HTTP `POST`，请求体为 `application/json`。
- 返回方式：MES 可以在同一次 HTTP 请求的响应体中同步返回物料号，也可以返回已受理并后续回调。
- 回调要求：新口径需要 MES 响应和回调都原样带回 QPO 生成的 `mesRequestId`，后续通过 QPO 回调接口写回物料号。
- 文件访问：QPO 会传 `drawingUrl`，如果 MES 需要下载图纸，MES 服务器必须能在内网访问该 URL。

简单理解：

```text
用户在 QPO 点击“获取物料号”
  -> QPO 创建内部异步任务
  -> 任务里 QPO POST 调 MES
  -> MES 立即在 HTTP response 里返回 materialNo，或返回已受理待回调
  -> 如果立即返回 materialNo，QPO 保存 materialNo 和同步状态
  -> 如果待回调，QPO 标记为待MES回调，MES 后续按 mesRequestId 回调 QPO
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
  "ticketId": 123,
  "rfqId": 88,
  "rfqNo": "RFQ-20260721-001",
  "customerName": "客户A",
  "partNo": "P-1001",
  "version": "A",
  "drawingUrl": "http://192.168.70.21:8088/uploads/drawings/xxx.pdf",
  "internalProjectNo": "IP-20260721-001"
}
```

### 3.3 请求字段

| 字段 | 类型 | 是否可能为空 | 说明 |
| --- | --- | --- | --- |
| `ticketId` | integer | 否 | QPO 已提零件行 ID，可作为幂等键之一。 |
| `rfqId` | integer | 通常否 | QPO 订单/RFQ ID。 |
| `rfqNo` | string | 可能为空 | RFQ 编号。 |
| `customerName` | string | 可能为空 | 客户名称。 |
| `partNo` | string | 否 | 图号，QPO 调用前会校验非空。 |
| `version` | string | 可能为空字符串 | 版本，QPO 无版本时传空字符串。 |
| `drawingUrl` | string | 否 | 图纸绝对 URL，QPO 调用前会校验非空。 |
| `internalProjectNo` | string | 否 | 内部项目号，QPO 调用前会校验非空。 |

## 4. MES 响应要求

### 4.1 成功响应

HTTP 状态码建议返回 `200`，响应体：

```json
{
  "success": true,
  "materialNo": "MAT-000123",
  "message": "物料号创建成功",
  "externalTraceId": "MES-TRACE-001"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `success` | boolean | 是 | `true` 表示 MES 处理成功。 |
| `materialNo` | string | 成功时必填 | MES 返回给 QPO 保存的物料号。 |
| `message` | string | 否 | 成功或失败说明，会进入 QPO 状态信息。 |
| `externalTraceId` | string | 否 | MES 侧流水号，建议返回，方便双方排查。 |

QPO 成功判定：

- HTTP 为 2xx；
- `success=true`；
- `materialNo` 非空；
- `materialNo` 去掉首尾空格后长度不超过 100。

### 4.2 失败响应

推荐失败也返回 HTTP `200`，并在响应体中说明业务失败原因：

```json
{
  "success": false,
  "message": "图纸无法识别，缺少材料信息",
  "externalTraceId": "MES-TRACE-002"
}
```

QPO 失败判定：

- HTTP 非 2xx；
- 连接超时或读取超时；
- 响应体为空；
- `success` 不是 `true`；
- `success=true` 但 `materialNo` 为空；
- `materialNo` 长度超过 100。

失败后 QPO 会把该已提零件的 MES 状态标记为 `FAILED`，用户可重新触发。

## 5. 批量选择时的处理方式

QPO 前端支持批量选择多个已提零件后提交“获取物料号”任务。

对 MES 来说，批量不是一个大 JSON 数组请求。当前 QPO 的处理方式是：

```text
用户选择 N 个零件
  -> QPO 创建 1 个批量 system_task
  -> 任务内部按 ticketId 顺序循环
  -> 每个零件单独 POST 调一次 MES
  -> 每个零件等待 MES 同步响应后再处理下一个
  -> 最后 QPO 汇总成功、失败、跳过数量
```

也就是说，MES 会收到多次单零件请求，而不是一次包含多个零件的请求。

并发说明：

- 一个 QPO 批量任务内部默认顺序处理，不会把同一批零件全部同时打到 MES。
- 多个用户或多个后台任务同时运行时，MES 可能同时收到多个 QPO 请求。
- QPO 当前后台任务线程池默认：核心线程 `2`，最大线程 `4`，队列 `50`。
- 建议 MES 至少支持 `4` 个来自 QPO 的并发请求；如果 MES 支持更低并发，请提前告知 QPO 侧调整任务线程池或增加限流。

## 6. 幂等与重复请求建议

QPO 正常情况下不会在已成功拿到物料号后重复提交同一行；但以下情况可能出现重复请求：

- 用户对失败任务点击重试；
- 网络超时导致 QPO 不知道 MES 是否已经处理成功；
- 测试/联调时重复提交同一条数据。

建议 MES 以 `ticketId`，或 `internalProjectNo + partNo + version`，作为幂等判断依据：

- 如果同一零件已经创建过物料号，建议直接返回原 `materialNo`；
- 不建议因为重复请求创建多个不同物料号；
- 如果 MES 认为请求重复但不能返回物料号，请返回 `success=false` 和明确 `message`。

## 7. 联调验收用例

请 MES 至少支持以下联调用例：

| 场景 | MES 返回 | QPO 预期 |
| --- | --- | --- |
| 正常创建物料号 | `success=true` + `materialNo` | QPO 保存物料号，状态 `SUCCESS`。 |
| 已存在物料号 | `success=true` + 已有 `materialNo` | QPO 保存/保持物料号，状态 `SUCCESS`。 |
| 图纸不可识别 | `success=false` + `message` | QPO 状态 `FAILED`，显示失败原因。 |
| MES 内部异常 | HTTP 500 或 `success=false` | QPO 状态 `FAILED`。 |
| 响应超时 | 无响应超过超时时间 | QPO 状态 `FAILED`，用户可重试。 |
| `materialNo` 为空 | `success=true` 但无物料号 | QPO 状态 `FAILED`。 |

## 8. 双方上线前确认

- QPO 后端服务器能访问 MES 的测试/生产地址。
- MES 如需下载图纸，MES 服务器能访问 QPO 发出的 `drawingUrl`。
- 双方确认认证方式、token、IP 白名单、防火墙策略。
- 双方确认超时时间、并发上限、是否需要限流。
- MES 返回 JSON 字段名与本文档一致，尤其是 `success`、`materialNo`、`message`。
- 双方约定联调样例数据，并保留 `ticketId` / `externalTraceId` 方便查日志。
