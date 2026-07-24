# QPO 对接 WMS 库存锁库接口说明

本文档提供给 WMS 系统开发/实施人员，用于说明 QPO 如何调用 WMS 获取库存锁库和在产锁定信息。

## 1. 对接结论

- 调用方向：QPO -> WMS。
- 调用方式：HTTP `POST`，请求体为 `application/json`。
- 返回方式：WMS 在同一次 HTTP 请求的响应体中同步返回锁库结果。
- 回调要求：当前 QPO 不需要 WMS 回调，也没有为本流程提供 WMS 回调接口。
- 前置条件：QPO 必须已经有 MES 返回的 `materialNo`，才能调用 WMS。

简单理解：

```text
用户在 QPO 点击“获取库存数据/提交WMS锁库”
  -> QPO 创建内部异步任务
  -> 任务里 QPO POST 调 WMS
  -> WMS 立即在 HTTP response 里返回库存锁定/在产锁定数量
  -> QPO 拆分并保存锁库零件明细
```

## 2. WMS 需要提供给 QPO 的信息

请 WMS 提供以下信息，用于 QPO 配置和联调：

| 信息 | 示例 | 说明 |
| --- | --- | --- |
| WMS 内网访问地址 | `http://wms-server:9100` | QPO 后端所在机器必须能访问。 |
| 库存锁库接口路径 | `/api/wms/inventory/allocate` | QPO 默认路径，可按 WMS 实际路径配置。 |
| 认证方式 | `Bearer Token` / 无认证 | 当前 QPO 支持可选 `Authorization: Bearer {token}`。 |
| 测试环境地址 | `http://wms-test:9100` | 用于联调。 |
| 生产环境地址 | `http://wms-prod:9100` | 上线前配置。 |
| 超时时间建议 | `10000 ms` | QPO 当前默认 10 秒。 |
| 并发能力 | 例如 `4` 个并发请求 | QPO 默认后台任务线程池最大 4 个任务并行。 |
| 锁库幂等规则 | 按 `ticketId` 或 `materialNo` | 避免重复锁库。 |
| 错误码/错误信息规则 | JSON 示例 | 方便 QPO 页面展示失败原因。 |

QPO 侧配置项：

```env
ERP_WMS_BASE_URL=http://wms-server:9100
ERP_WMS_AUTH_TOKEN=
ERP_WMS_ALLOCATION_PATH=/api/wms/inventory/allocate
ERP_WMS_TIMEOUT_MS=10000
```

## 3. HTTP 契约

### 3.1 请求

```http
POST {ERP_WMS_BASE_URL}{ERP_WMS_ALLOCATION_PATH}
Content-Type: application/json
Authorization: Bearer {ERP_WMS_AUTH_TOKEN}
```

说明：

- `Authorization` 只有在 QPO 配置了 `ERP_WMS_AUTH_TOKEN` 时才会发送。
- 如果 WMS 不需要认证，可以不配置 token。
- 请 WMS 尽量返回 `Content-Type: application/json`。

### 3.2 请求 JSON 示例

```json
{
  "ticketId": 123,
  "rfqId": 88,
  "rfqNo": "RFQ-20260721-001",
  "customerName": "客户A",
  "materialNo": "MAT-000123",
  "partNo": "P-1001",
  "version": "A",
  "internalProjectNo": "IP-20260721-001",
  "demandQty": 10
}
```

### 3.3 请求字段

| 字段 | 类型 | 是否可能为空 | 说明 |
| --- | --- | --- | --- |
| `ticketId` | integer | 否 | QPO 已提零件行 ID，建议 WMS 用作幂等键之一。 |
| `rfqId` | integer | 通常否 | QPO 订单/RFQ ID。 |
| `rfqNo` | string | 可能为空 | RFQ 编号。 |
| `customerName` | string | 可能为空 | 客户名称。 |
| `materialNo` | string | 否 | MES 返回并由 QPO 保存的物料号。 |
| `partNo` | string | 否 | 图号，QPO 调用前会校验非空。 |
| `version` | string | 可能为空字符串 | 版本，QPO 无版本时传空字符串。 |
| `internalProjectNo` | string | 否 | 内部项目号，QPO 调用前会校验非空。 |
| `demandQty` | integer | 否 | 需求数量，必须大于 0。 |

## 4. WMS 响应要求

### 4.1 成功响应

HTTP 状态码建议返回 `200`，响应体：

```json
{
  "success": true,
  "materialNo": "MAT-000123",
  "inStockLockedQty": 3,
  "inProductionLocks": [
    {
      "productionOrderNo": "MO-20260721-001",
      "lockedQty": 4
    },
    {
      "productionOrderNo": "MO-20260721-002",
      "lockedQty": 2
    }
  ],
  "message": "锁库成功",
  "externalTraceId": "WMS-TRACE-001"
}
```

字段说明：

| 字段 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `success` | boolean | 是 | `true` 表示 WMS 处理成功。 |
| `materialNo` | string | 成功时必填 | 必须与 QPO 请求里的 `materialNo` 完全一致。 |
| `inStockLockedQty` | integer | 否 | 已入库库存锁定数量，建议返回非负整数；为空时 QPO 按 0 处理。 |
| `inProductionLocks` | array | 否 | 在产锁定列表；为空或不返回时 QPO 按空列表处理。 |
| `inProductionLocks[].productionOrderNo` | string | 在产锁定时必填 | 在产订单号。 |
| `inProductionLocks[].lockedQty` | integer | 在产锁定时必填 | 该在产订单锁定数量，必须大于 0。 |
| `message` | string | 否 | 成功或失败说明，会进入 QPO 状态信息。 |
| `externalTraceId` | string | 否 | WMS 侧流水号，建议返回，方便双方排查。 |

### 4.2 QPO 对锁库数量的处理规则

QPO 收到 WMS 成功响应后会做拆分：

```text
demandQty = QPO 请求数量
inStockQty = inStockLockedQty
inProductionQty = sum(inProductionLocks.lockedQty)
coveredQty = inStockQty + inProductionQty

如果 coveredQty > demandQty：
  QPO 判定失败，不写入锁库明细

如果 coveredQty <= demandQty：
  QPO 写入：
    IN_STOCK_LOCK：已入库锁定数量
    IN_PRODUCTION_LOCK：每个在产订单一条
    PRODUCTION_REQUIRED：demandQty - coveredQty 的剩余待生产数量
```

示例：QPO 请求 `demandQty=10`，WMS 返回已入库 `3`，在产 `4+2`。

QPO 最终保存：

| 类型 | 数量 | 说明 |
| --- | --- | --- |
| `IN_STOCK_LOCK` | 3 | 已入库库存锁定。 |
| `IN_PRODUCTION_LOCK` | 4 | 在产订单 `MO-20260721-001`。 |
| `IN_PRODUCTION_LOCK` | 2 | 在产订单 `MO-20260721-002`。 |
| `PRODUCTION_REQUIRED` | 1 | WMS 未覆盖的剩余需求，QPO 视为待生产。 |

### 4.3 失败响应

推荐失败也返回 HTTP `200`，并在响应体中说明业务失败原因：

```json
{
  "success": false,
  "message": "库存不足且无可锁定在产订单",
  "externalTraceId": "WMS-TRACE-002"
}
```

QPO 失败判定：

- HTTP 非 2xx；
- 连接超时或读取超时；
- 响应体为空；
- `success` 不是 `true`；
- `success=true` 但 `materialNo` 为空；
- WMS 返回的 `materialNo` 与 QPO 请求的 `materialNo` 不一致；
- `inProductionLocks` 中任意一条缺少 `productionOrderNo`；
- `inProductionLocks` 中任意一条 `lockedQty` 为空或小于等于 0；
- 已入库数量 + 在产锁定数量大于 `demandQty`。

失败后 QPO 会把该已提零件的 WMS 状态标记为 `FAILED`，用户可重新触发。

## 5. 批量选择时的处理方式

QPO 前端支持批量选择多个已提零件后提交“获取库存数据/提交 WMS 锁库”任务。

对 WMS 来说，批量不是一个大 JSON 数组请求。当前 QPO 的处理方式是：

```text
用户选择 N 个零件
  -> QPO 创建 1 个批量 system_task
  -> 任务内部按 ticketId 顺序循环
  -> 每个零件单独 POST 调一次 WMS
  -> 每个零件等待 WMS 同步响应后再处理下一个
  -> 最后 QPO 汇总成功、失败、跳过数量
```

也就是说，WMS 会收到多次单零件请求，而不是一次包含多个零件的请求。

并发说明：

- 一个 QPO 批量任务内部默认顺序处理，不会把同一批零件全部同时打到 WMS。
- 多个用户或多个后台任务同时运行时，WMS 可能同时收到多个 QPO 请求。
- QPO 当前后台任务线程池默认：核心线程 `2`，最大线程 `4`，队列 `50`。
- 建议 WMS 至少支持 `4` 个来自 QPO 的并发请求；如果 WMS 支持更低并发，请提前告知 QPO 侧调整任务线程池或增加限流。

## 6. 幂等与重复锁库建议

QPO 正常情况下不会在 WMS 锁库成功后重复提交同一行；但以下情况可能出现重复请求：

- 用户对失败任务点击重试；
- 网络超时导致 QPO 不知道 WMS 是否已经锁定成功；
- 测试/联调时重复提交同一条数据。

建议 WMS 以 `ticketId` 作为最优先幂等键，或使用 `materialNo + internalProjectNo + demandQty` 做辅助判断：

- 如果同一 `ticketId` 已锁库成功，建议返回上一次相同的锁库结果；
- 不建议重复扣减或重复锁定库存；
- 如果 WMS 拒绝重复请求，请返回 `success=false` 和明确 `message`；
- 如果 WMS 需要 QPO 传业务流水号之外的幂等键，请联调前提出，QPO 侧需要评估是否增加字段。

## 7. 联调验收用例

请 WMS 至少支持以下联调用例：

| 场景 | WMS 返回 | QPO 预期 |
| --- | --- | --- |
| 全部库存覆盖 | `inStockLockedQty=demandQty` | QPO 写入 1 条 `IN_STOCK_LOCK`。 |
| 库存 + 在产覆盖 | 已入库数量 + 多条在产锁定 | QPO 写入库存锁定和多条在产锁定。 |
| 部分覆盖 | 覆盖数量小于 `demandQty` | QPO 自动补一条 `PRODUCTION_REQUIRED`。 |
| 完全无库存 | `inStockLockedQty=0` 且无在产 | QPO 写入 1 条 `PRODUCTION_REQUIRED`。 |
| 超额锁定 | 覆盖数量大于 `demandQty` | QPO 状态 `FAILED`，不写锁库明细。 |
| 物料号不一致 | 返回不同 `materialNo` | QPO 状态 `FAILED`。 |
| 在产订单号为空 | 在产明细缺 `productionOrderNo` | QPO 状态 `FAILED`。 |
| 在产数量非法 | `lockedQty<=0` | QPO 状态 `FAILED`。 |
| WMS 内部异常 | HTTP 500 或 `success=false` | QPO 状态 `FAILED`。 |
| 响应超时 | 无响应超过超时时间 | QPO 状态 `FAILED`，用户可重试。 |

## 8. 双方上线前确认

- QPO 后端服务器能访问 WMS 的测试/生产地址。
- 双方确认认证方式、token、IP 白名单、防火墙策略。
- 双方确认超时时间、并发上限、是否需要限流。
- WMS 返回 JSON 字段名与本文档一致，尤其是 `success`、`materialNo`、`inStockLockedQty`、`inProductionLocks`。
- WMS 明确重复请求时的幂等策略，避免重复锁库。
- 双方约定联调样例数据，并保留 `ticketId` / `externalTraceId` 方便查日志。
