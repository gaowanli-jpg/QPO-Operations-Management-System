# QPO MES 物料号同图号版本去重开发文档

最后更新：2026-08-07

> 当前口径：已确认物料号的复用已经前移到 RFQ 重复件核价和销售订单自动异常治理匹配。提单行继承来源主表中的确认信息；只有未取得确认物料号的提单行才进入 MES 获取流程。MES 去重只负责活动主请求和 follower，不再承担历史成功提单行物料号复用。

> 2026-08-08 两级工艺主数据阶段 15 收口：MES 去重链路不参与工艺复用或工艺计价；前置重复件核价、销售订单异常治理和 RFQ 派生销售订单负责生成或复制标准工艺快照。已提零件 MES 去重只处理物料号请求并把结果 fan-out 写回对应来源主表。

## 1. 背景与结论

同一批或不同批次中，可能存在多个 **图号 + 版本完全相同** 的已提零件。对于尚未在来源主表取得已确认物料号的零件，QPO 必须避免并发向 MES 创建重复任务，同时保证每条提单行都能获得最终结果。

最终职责边界如下：

```text
RFQ 重复件核价 / 销售订单自动异常治理匹配
  -> 复用来源主表已确认物料号及确认元组
  -> 提单行继承确认物料号并直接标记 SUCCESS

来源主表没有确认物料号
  -> 已提零件请求 MES
  -> 一个活动 MASTER 真实调用 MES
  -> 同图号版本后续请求成为 FOLLOWER
  -> MES 只返回或回调主 mesRequestId
  -> QPO fan-out 写回所有 follower 及其来源主表
```

核心结论：

- 去重键固定为 **图号 + 版本**，不区分客户、订单、图纸文件或内部项目号。
- 同一 `dedupKey` 同一时间只允许一个活动主请求。
- 存在活动主请求时，后续提单行成为 follower，不调用 MES。
- 不查询 `order_delivery_ticket` 历史成功行，也不从其他成功提单行复制物料号。
- 没有活动主请求时创建新的 MES 主请求，由数据库唯一约束处理并发竞争。
- MES 只处理主请求；QPO 负责即时成功或回调成功后的 follower fan-out。
- 不新增用户权限，继续使用 `market:delivered-item:mes-sync`。

## 2. 前置物料号复用边界

### 2.1 RFQ 重复件核价

`EngineerService.applyRepeatQuotes(...)` 在复用历史已提单 RFQ 零件的核价快照时，同时检查来源物料号确认元组：

```text
material_no 非空
material_no_status = 已确认
```

满足条件时，复制 `material_no`、`material_no_status`、`material_no_source`、`material_no_source_ticket_id`、`material_no_synced_at` 和 `material_no_message`。来源未确认不阻断材料、工艺、费用和价格快照复用。

### 2.2 销售订单自动异常治理匹配

`SalesOrderMatchService.applyProductionSnapshot(...)` 命中历史 RFQ 来源时，按相同规则只补空缺的物料号确认元组。目标已有相同物料号时幂等补齐元数据；已有不同物料号时拒绝覆盖，但不阻断其他生产快照补全。

### 2.3 提单行继承

RFQ 或销售订单来源主表满足“物料号非空且状态为已确认”时，`DeliveryService` 生成提单行后直接写入：

```text
order_delivery_ticket.material_no = source.material_no
order_delivery_ticket.mes_sync_status = SUCCESS
```

这类提单行不进入 MES 待处理池。来源主表没有确认物料号时，提单行保持 `PENDING`，按本文后续流程获取 MES 物料号。

## 3. 活动请求去重模型

### 3.1 去重键

```text
dedupKey = normalize(part_no) + "|" + normalize(version)
```

- 图号和版本去除首尾空格并统一大写。
- 空版本统一为 `-`。
- 不拼接客户、订单、图纸或项目号。

### 3.2 主请求

主请求是某个 `dedupKey` 当前唯一真实调用 MES 的提单行：

- `mes_request_role = MASTER`。
- `order_delivery_ticket_mes_request.request_role = MASTER`。
- 请求状态为 `PROCESSING` 或 `CALLBACK_PENDING` 时属于活动主请求。
- MES 只需要处理该主请求的 `mesRequestId`。

### 3.3 follower

follower 是同一 `dedupKey` 下关联活动主请求、等待其结果的提单行：

- `mes_request_role = FOLLOWER`。
- 不向 MES 发起真实请求。
- 不与主请求共用 `order_delivery_ticket.mes_request_id`。
- 通过 `mes_master_ticket_id`、`mes_master_request_id` 和 `order_delivery_ticket_mes_waiter` 追溯主请求。
- 页面显示 `PROCESSING` 或 `CALLBACK_PENDING`，主请求成功后由 QPO fan-out 写回。

## 4. 数据库约束与索引

### 4.1 提单行追溯字段

| 字段 | 说明 |
| --- | --- |
| `mes_dedup_key` | 规范化后的图号版本去重键。 |
| `mes_request_role` | 当前只产生 `MASTER` 或 `FOLLOWER`。 |
| `mes_master_ticket_id` | follower 关联的主提单行 ID。 |
| `mes_master_request_id` | follower 关联的主请求 ID。 |

### 4.2 请求流水与等待表

- `order_delivery_ticket_mes_request` 保存真实主请求流水及状态。
- `order_delivery_ticket_mes_waiter` 保存 follower 与主请求的等待关系。
- 活动主请求唯一约束只覆盖 `PROCESSING`、`CALLBACK_PENDING`，保证同一 `dedupKey` 同时只有一个活动主请求。
- 等待表必须保证同一 `master_mes_request_id + waiter_ticket_id` 不重复。
- 保留活动主请求和 waiter 查询索引；不新增用于扫描历史成功提单行的索引。

## 5. 单条与批量处理

### 5.1 单条获取物料号

`MesMaterialSyncService.syncMaterialNo(ticketId, taskId)` 按以下顺序处理：

1. 加载提单行并校验状态、数据权限、图纸和内部项目号。
2. 已成功且有物料号时拒绝重复获取。
3. 当前行已处理或待回调时拒绝重复提交。
4. 计算 `dedupKey`，只查询活动主请求。
5. 命中活动主请求时创建 follower，返回 `FOLLOW_EXISTING_ACTIVE`，不调用 MES。
6. 未命中时创建 `MASTER` 请求流水并调用 MES。
7. 并发插入触发活动主请求唯一约束时，重新查询主请求并转为 follower。
8. 主请求即时成功、待回调或失败时，更新主请求流水并分发对应状态。

### 5.2 批量获取物料号

批量任务仍按提单行顺序处理，每行调用统一单条服务。汇总字段为：

| 字段 | 含义 |
| --- | --- |
| `successCount` | MES 即时成功的主请求数量。 |
| `callbackPendingCount` | 主请求待回调和 follower 等待数量。 |
| `dedupFollowerCount` | 未重复调用 MES、已关联活动主请求的数量。 |
| `submittedToMesCount` | 实际提交数据中心 / MES 的主请求数量。 |
| `failedCount` | 校验或外部调用失败数量。 |
| `skippedCount` | 已成功、处理中等不允许重复执行的数量。 |

行级状态：

| 状态 | 是否调用 MES | 说明 |
| --- | --- | --- |
| `SUCCESS` | 是 | 主请求即时获得物料号。 |
| `CALLBACK_PENDING` | 是或否 | 主请求已受理，或当前行已处于待回调。 |
| `DEDUP_FOLLOWING` | 否 | 当前行关联活动主请求。 |
| `FAILED` | 可能 | 校验失败或主请求调用失败。 |
| `SKIPPED` | 否 | 当前状态不允许重复执行。 |

## 6. 回调与 fan-out

MES 物料号回调入口保持：

```text
POST /api/integrations/mes/material-callback
```

数据中心只需要原样转发主请求的 `mesRequestId`。QPO 处理成功回调时：

1. 按 `mesRequestId` 定位主请求流水和主提单行。
2. 校验 ticket、最新请求、幂等和物料号冲突。
3. 写主提单行物料号和 `SUCCESS`。
4. 回写主提单行对应的 RFQ 或销售订单来源主表。
5. 查询该主请求下等待中的 follower。
6. QPO fan-out 写回 follower 提单行和各自来源主表。
7. 更新等待关系和主请求流水状态。

即时成功同样调用 fan-out；主请求进入待回调时，follower 同步进入等待状态；主请求失败时，follower 写为失败并允许后续重试。

冲突回调不得覆盖主请求、follower、RFQ 主表或销售订单主表中已经确认的不同物料号。

## 7. 页面口径

已提零件页面只展示当前活动请求追溯信息：

| 字段 | 展示 |
| --- | --- |
| `mesRequestRole` | 主请求 / 关联等待 |
| `mesMasterTicketId` | follower 关联的主提单行 ID |
| `mesMasterRequestId` | follower 关联的主请求 ID |
| `mesDedupKey` | 图号版本去重键 |

follower 说明统一为：

```text
已关联同图号版本 MES 主请求，等待主请求结果
```

页面不展示历史物料号复用统计。`SUCCESS` 行不显示获取按钮，`PROCESSING` 和 `CALLBACK_PENDING` 禁止重复提交，`FAILED` 可以重试。

## 8. 权限、日志与安全

### 8.1 权限

- 本次不新增权限。
- 单条、批量获取和失败重试继续使用 `market:delivered-item:mes-sync`。
- follower 是服务端并发控制结果，不新增独立按钮或 API 权限。
- 回调接口继续使用 QPO 入站机器 token，不进入用户权限树。

### 8.2 操作日志

- MES 提交、成功、待回调、失败和回调继续使用现有同步 / 回调动作。
- follower 建立记录 `DELIVERY_MES_MATERIAL_DEDUP_FOLLOW`。
- fan-out 记录 `DELIVERY_MES_MATERIAL_DEDUP_FANOUT`。
- 正常读取、轮询、状态展示不写操作日志。

### 8.3 文件日志

保留以下低噪声事件：

```text
MES material dedup master created
MES material dedup follow
MES material dedup fanout completed
MES material dedup fanout failed
```

日志只记录 `ticketId`、`taskId`、`mesRequestId`、`dedupKey`、主请求标识、等待数量、状态和脱敏失败摘要。不得记录 MES token、回调 token、Authorization header、完整请求体、完整响应体或完整回调 JSON。

## 9. 回归测试

后端必须覆盖：

- `resolveBeforeMes(...)` 不查询历史成功提单行。
- 没有活动主请求时创建一个主请求。
- 活动主请求存在时创建 follower，不调用 MES。
- 并发唯一约束冲突转为 follower。
- 主请求即时成功、待回调、回调成功、失败和 fan-out 不回退。
- RFQ 和销售订单来源主表回写及冲突保护不回退。
- WMS 继续读取 `order_delivery_ticket.material_no`。

前端必须覆盖：

- 不展示历史物料号复用文案或统计。
- follower 等待说明和追溯字段正常展示。
- follower 和待回调行不能重复提交。
- 现有 `market:delivered-item:mes-sync` 权限仍有效。

全量命令：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

## 10. 最终验收

- 前置匹配负责已确认物料号复用和确认元组沉淀。
- 已确认物料号在提单时继承，不进入 MES 待处理池。
- MES 去重只负责活动主请求和 follower。
- 已提零件获取 MES 时不查询其他历史成功提单行。
- 同一 `dedupKey` 并发只产生一个真实 MES 主请求。
- 主请求结果可以正确 fan-out 到所有 follower 和来源主表。
- WMS、待产、锁库、MES 生产下发和 U9 链路不回退。
- 不新增用户权限，日志不泄露 token、Authorization header 或完整外部报文。

### 阶段 8 收口补充

前置重复匹配负责复用 RFQ / 销售订单主表中的已确认物料号；已提零件 MES 去重只负责活动主请求和 follower 并发关系。RFQ 派生销售订单的关联 ticket 完成后，MES 结果按 37 号文档双写两边主表，历史成功提单行不再作为新流程复用来源。

U9 只读取派生销售订单表中的确认物料号和含税单价，未确认或含税单价缺失时不发起外部调用。本次不新增权限，普通查询和状态展示不记操作日志。
