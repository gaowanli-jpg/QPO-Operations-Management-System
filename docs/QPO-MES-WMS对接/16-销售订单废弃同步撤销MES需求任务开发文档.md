# 16-销售订单废弃同步撤销 MES 需求任务开发文档

> 对接接口：`POST /api/v1/mes/openapi/demand/delete`（MES 内部叫"删除需求任务"，语义上是逻辑删除）
> MES 答复依据：`mes-new/tmp/QPO-需求任务删除对接确认答复.md`（代码版本 `b81289801e`）
> 本文只覆盖"销售订单废弃 → 撤销 MES 需求任务"这一条链路，不含 MES 侧待排期项。

---

## 1. MES 口径（决定方案形态的 6 条）

| # | 结论 | 对 QPO 的影响 |
|---|---|---|
| 1 | **只有"开立"(status=0) 可删**，提交审核后一律 `1030700006` | 废弃多发生在下发后数小时/数天，**大概率大部分行删不掉**，必须把失败结果显式暴露给业务 |
| 2 | `id` = `demand/create` 响应 `items[].id`（MES 主键），`planNo` 不能用于删除 | 必须用 `mes_production_batch_item.mes_record_id`，不能用 `mes_plan_no` |
| 3 | `bizId` 只是幂等键，不参与业务校验；每次删除必须唯一，≤128 字符 | 每次删除新生成 `QPO-DEMDEL-...`，**超时重试必须复用同一个 bizId** |
| 4 | 不支持批量，一次一条 | 逐条调用；一个订单有 N 条已下发就要 N 次 HTTP |
| 5 | 不是幂等成功：同 bizId 重发 → `1030900002`；换 bizId 删同一条 → `1030700000` | 这两个码都要当"已删除"处理，否则会误报失败 |
| 6 | 没有查询状态接口；删除不级联（能删时下游本就不存在） | 只能盲调认码；不需要处理下游工单 |

**唯一可靠的结果核对手段**（MES 明确要求）：超时后用**原 bizId 原样重发一次**，
`1030900002` = 上次其实成功了，按成功处理。

---

## 2. 现状链路与缺口

### 2.1 需求任务是怎么产生的

只有一个入口：`MesProductionDispatchService.dispatchBatch` → `demand/create`。

- 手动：待产零件页「下发MES生产」（`ProductionItemManage.vue:66`）
- 自动：每日定时下发 `ProductionItemAutoDispatchService`（V66），候选 SQL 在
  `OrderDeliveryTicketAllocationMapper:542`

生成提单、WMS 拆行、MES 物料号同步**都不产生**需求任务。所以"订单废弃要不要撤销需求任务"= 这张订单的待产行有没有被下发过。

### 2.2 销售订单页的四个"废弃"入口

| 入口 | 后端 | 现状 |
|---|---|---|
| 订单「废弃」 | `SalesOrderManageService.discard` → `changeSalesOrderStatus:868` | 只把 header + item 状态改「废弃」，不动提单、待产行、WMS、MES |
| 订单「关联废弃」 | `SalesOrderLinkedDiscardService.discardOrder:268` | 级联废弃提单/待产行/锁库行，但 **SUCCESS 行被 `isCompletedAllocation:1079` 判为"已完成"跳过** |
| 零件「废弃」 | `changeItemU9ChangeExclusion` | 只打 `u9_change_excluded`，纯 U9 语义 |
| 零件「关联废弃」 | `SalesOrderLinkedDiscardService.discard:76` | 单行级联，同样跳过 SUCCESS |

### 2.3 三个必须补的缺口

1. **订单「废弃」对 MES 零动作** —— 主缺口。
2. **「关联废弃」恰好跳过最该删的行** —— 已成功下发（`mes_production_status=SUCCESS`）的待产行被判"已完成"，既不废弃也不删 MES。所以不能只在"被废弃的行"上挂删除，必须按订单独立捞 SUCCESS 行。
3. **定时下发没过滤订单状态** —— `selectAutoDispatchCandidateIds` / `countAutoDispatchCandidates`（`OrderDeliveryTicketAllocationMapper:542/565`）join 了 `sales_order_header` 却只判存在、没判 `status`。废弃订单里仍是 PENDING 的待产行第二天会被重新下发给 MES，形成"删了又建"。

---

## 3. 本次范围

### 做

1. 新增 MES 需求任务删除客户端 + 数据中台端点 `MES_DEMAND_DELETE` + 迁移 V72。
2. 订单级「废弃」和「关联废弃」两个入口，提交后按订单撤销全部已成功下发的需求任务。
3. 新增两个待产行状态 `CANCELLED` / `CANCEL_FAILED`，并在待产零件页可见、可筛选。
4. 订单「恢复」时把这两个状态都改回 `PENDING`（用户已确认：**不自动重建**，由用户重新下发）。
5. 定时下发候选 SQL 补订单状态过滤（缺口 3）。
6. 前端两个确认框文案 + 待产零件页状态标签。
7. 审计 + 测试。

### 不做（可要可不要，暂时不管）

- 零件级两个入口（语义偏 U9，挂上去会冲突）。
- **MES 侧"失败待办通道" —— 已明确不采纳**（2026-10-30 决定）。MES 建议"必须建立待办通道，
  否则残留任务无人知晓"，QPO 侧不做推送/待办表/通知机制。撤销结果只落在待产零件页的
  「撤销失败」「撤销待核对」筛选 + 审计记录，由业务自己看。**不要再把这条当缺口重开。**
  若后续实测 `1030700006` 占比确实高到影响生产，再单独立项，不在本次范围。
- MES 待排期项：`demand/status` 查询、`demand/cancel` 作废语义、批量删除、按订单行删除、错误码拆细。
- 不改 `allocation.discarded`。订单级「废弃」仍然是"只改状态"的软废弃，本次只叠加 MES 撤销动作。
- 不做失败自动重试任务/定时补偿。

---

## 4. 状态设计

`order_delivery_ticket_allocation.mes_production_status` 是 `varchar(30)` 且**无 CHECK 约束**（`V1:612`），新增值不需要改表结构。

| 新状态 | 含义 | 写入时机 |
|---|---|---|
| `CANCELLED` | 需求任务已删除（MES 返回 0 / `1030900002` / `1030700000`） | 删除成功 |
| `CANCEL_FAILED` | 没删掉（MES 拒绝、超时、系统异常） | 具体原因写 `mes_production_message` |

只分"删掉了"和"没删掉"两种，**不再细分"结果未知"**。理由：QPO 侧这个状态只用于展示和筛选，
不参与任何去重或重试判断，细分没有业务收益。结果未知的情况在 message 里注明即可。

两个值同时写入 `mes_production_batch_item.status`，保留台账痕迹（不物理删）。

**为什么不能置空或复用 `FAILED`**：
- 置空会退回 `PENDING`，被定时下发重新捡走；
- `FAILED` 在 `markMesProductionLineReserved`（`OrderDeliveryTicketAllocationMapper:313`）里是"可重新下发"的合法前置状态，会和"已撤销"混淆。

**恢复语义**：`CANCELLED` / `CANCEL_FAILED` 一律 → `PENDING`。恢复订单就是这单还要做，
状态归零，是否重新下发由业务自己决定。MES 侧任务是否还在、会不会重复建，由 MES 业务跟进，
QPO 不做判断也不做兜底。

---

## 5. 码表与重试规则

### 5.1 单次调用结果判定

| MES code | 判定 | 写回状态 |
|---|---|---|
| `0` | 删除成功 | `CANCELLED` |
| `1030900002` | 相同 bizId 已成功 → 视为成功 | `CANCELLED` |
| `1030700000` | 需求计划不存在（已删过）→ 视为成功 | `CANCELLED` |
| `1030700006` | 状态不允许删除（已提交审核/已执行） | `CANCEL_FAILED` |
| `400` / `403` | 参数错误 / scope 不足 | `CANCEL_FAILED` |
| `1030900001` / `1030900003` | 鉴权异常 / 同 bizId 处理中，可重试 | 同 bizId 重试一次 |
| 超时 / 5xx | 结果未知 | 同 bizId 重发一次 |
| 其他非 0 码 | 未知业务失败 | `CANCEL_FAILED` |

### 5.2 单条删除的执行流程

```
bizId = "QPO-DEMDEL-" + yyyyMMdd + "-" + 12位随机     // 每条唯一，重试复用
attempt 1..2:
    resp = client.delete(bizId, mesRecordId)
    命中 0 / 1030900002 / 1030700000  -> CANCELLED, break
    命中 1030700006 / 400 / 403       -> CANCEL_FAILED, break
    命中 1030900001 / 1030900003      -> 重试（同 bizId）
    超时 / 5xx                        -> 重试（同 bizId）
    两次都未定论                       -> CANCEL_FAILED（message 注明"结果未知"）
```

> 重试只做一次，且**必须复用同一 bizId**，这是 MES 提供的唯一核对手段。换 bizId 重发会拿到 `1030700000`，被误判成"本来就不存在"。

### 5.3 allocation 汇总规则

一条待产行可能有多条历史 SUCCESS 下发记录（重复下发脏数据），MES 要求逐条删（答复 B4）。
逐条处理完后按 allocation 汇总：

- 全部 `CANCELLED` → `CANCELLED`
- 有任何一条 `CANCEL_FAILED` → `CANCEL_FAILED`

---

## 6. 改动清单

### 6.1 后端新增

| 文件 | 内容 |
|---|---|
| `dto/mes/MesDemandDeleteReq.java` | `bizId`、`id`(Long)，`@JsonInclude(NON_NULL)` |
| `dto/mes/MesDemandDeleteResp.java` | `code`(Object)、`msg`(String)、`data`；`isSuccess()`、`isIdempotentSuccess()`(1030900002)、`isNotFound()`(1030700000)、`isStateRejected()`(1030700006) |
| `service/mes/MesDemandDeleteClient.java` | `delete(bizId, id)`；走 `DataCenterIntegrationClient.call(MES_DEMAND_DELETE, ...)`，数据中心关闭时用 RestTemplate 直连；复用 `DataCenterCallException.getCertainty()` 区分"明确失败/结果未知" |
| `service/mes/MesDemandCancelService.java` | 编排：捞候选 → 逐条删 → 写状态 → 汇总 → 审计；`scheduleAfterCommit(salesOrderId, reason)`、`reopenCancelled(salesOrderId)` |

### 6.2 后端修改

| 文件 | 改动 |
|---|---|
| `service/DataCenterEndpointRegistry.java` | 新增常量 `MES_DEMAND_DELETE`、`EndpointDefinition`（POST / `MES_DEMAND_DELETE_V1` / `MES_DEMAND_DELETE_RESPONSE_V1` / 最小超时 15000）、`definitions()` 列表、`fallbackPath` 分支 |
| `config/DataCenterProperties.java` | 新增 `mesDemandDeletePath = "/api/v1/mes/openapi/demand/delete"`（默认值即可，不需要改 yml/erp.env） |
| `mapper/OrderDeliveryTicketAllocationMapper.java` | ① 新增 `selectMesDemandCancelTargets(salesOrderId)`：从 `mes_production_batch_item` join `mes_integration_batch` 取该订单全部 SUCCESS 行；② 新增 `markMesDemandCancelStatus(allocationId, status, message)`，条件 `COALESCE(...,'PENDING') <> 'CANCELLED'`（**不能写成 `= 'SUCCESS'`**：同一待产行最后一次下发失败时 allocation 上是 FAILED，但台账里仍有 SUCCESS 行要删，写不进去就会"MES 已删、QPO 还能重新下发"）；③ 新增 `reopenMesDemandCancelled(salesOrderId)`；④ **定时下发两条 SQL 加 `AND header.status <> '废弃'`** |
| `mapper/MesProductionBatchItemMapper.java` | 新增 `markCancelledById(id, status, message)` |
| `service/SalesOrderManageService.java` | `changeSalesOrderStatus`：`ACTION_DISCARD` 成功后 `scheduleAfterCommit`；`ACTION_RESTORE` 时 `reopenCancelled` |
| `service/SalesOrderLinkedDiscardService.java` | `discardOrder` 成功后 `scheduleAfterCommit`；`restoreOrder` 时 `reopenCancelled` |
| `service/mes/MesProductionDispatchService.java` | `skipReason` 拦截 `CANCELLED`（"该行 MES 需求任务已撤销，恢复销售订单后才能重新下发"）和 `CANCEL_FAILED`（"该行 MES 需求任务未撤销成功，请先在 MES 处理或恢复订单后重试"），否则会被 `markMesProductionLineReserved` 拦成一条看不懂的 FAILED |
| `service/ProductionItemService.java` | ① `applyProductionActiveScope:360` 排除 `CANCELLED`，避免已撤销行挂在「待处理」视图；② `applyProductionStatusScope:348` 必须加 `case CANCELLED -> query.eq(...)` **单独分支、不套 active scope**，否则用户主动筛「已撤销」会被 active scope 排除成空列表（现有 `default` 分支就是这个坑）；③ `CANCEL_FAILED` 走 `default` 分支，保留在视图内，需要人看见 |
| `service/AuditService.java` | `labelForAction` 增加 `SALES_ORDER_MES_DEMAND_CANCEL -> "同步撤销MES需求任务"` |

**候选查询口径**（`selectMesDemandCancelTargets`）：

```sql
SELECT bi.id, bi.allocation_id, bi.mes_record_id, bi.mes_plan_no, b.biz_id
FROM mes_production_batch_item bi
JOIN mes_integration_batch b ON b.id = bi.batch_id
WHERE bi.status IN ('SUCCESS', 'CANCEL_FAILED')
  AND COALESCE(NULLIF(TRIM(bi.mes_record_id), ''), '') <> ''
  AND bi.allocation_id IN (
      SELECT alloc.id
      FROM order_delivery_ticket_allocation alloc
      WHERE alloc.allocation_type = 'PRODUCTION_REQUIRED'
        AND (
            EXISTS (SELECT 1 FROM sales_order_item item
                    WHERE item.allocation_id = alloc.id
                      AND item.sales_order_id = #{salesOrderId})
            OR (alloc.source_type = 'SALES_ORDER'
                AND alloc.source_order_id = #{salesOrderId})
        )
  )
ORDER BY bi.id
```

状态取 `SUCCESS` + `CANCEL_FAILED` 而不是只取 `SUCCESS`：撤销失败的行在
「废弃 → 恢复 → 再次废弃」时要再试一次，否则那条 MES 任务永远没人管。
已确认撤销的 `CANCELLED` 不再重复调 MES（幂等）。

两条归属路径（`sales_order_item.allocation_id` 新链 + `alloc.source_order_id` 旧链）与
`SalesOrderLinkedDiscardService.lockOrderRelations` 的判定保持一致，不引入第三套口径。

### 6.3 迁移 V72

`V72__mes_demand_delete_endpoint.sql`，照 `V64:78` 的写法插入
`system_data_center_endpoint_config`：

| 字段 | 值 |
|---|---|
| `endpoint_code` | `MES_DEMAND_DELETE` |
| `display_name` | `MES撤销需求任务` |
| `method` / `path` | `POST` / `/api/v1/mes/openapi/demand/delete` |
| `timeout_ms` | `15000` |
| `request_adapter` / `response_adapter` | `MES_DEMAND_DELETE_V1` / `MES_DEMAND_DELETE_RESPONSE_V1` |

同时把 `SalesOrderLinkedChangeNoticeMigrationTest:53` 的版本门禁抬到最新值（当前 `73`）。

### 6.4 迁移 V73（台账状态约束）

**实测发现的坑**：`mes_production_batch_item.status` 在 `V6:92` 有 CHECK 约束
`chk_mes_production_batch_item_status`，只允许
`RESERVED / SUBMITTING / SUCCESS / FAILED / RESULT_UNKNOWN / SKIPPED`。
直接写 `CANCELLED` / `CANCEL_FAILED` 会违反约束抛异常，被 `writeBatchItemStatus` 的
try/catch 吞掉 → 台账留痕静默失效，只刷一条 warn。

`V73__mes_production_batch_item_cancel_status.sql` 重建该约束，加入两个新状态
（新集合是旧集合的超集，不影响既有数据）。

注意：`order_delivery_ticket_allocation.mes_production_status` 是 `varchar(30)` 且**无 CHECK**
（`V1:612`），新状态不需要改它的列定义。

### 6.5 消息长度

`mes_production_batch_item.message` 是 `VARCHAR(500)`，而 MES 的 `msg` 和异常信息可能更长。
超长会让整条 UPDATE 失败、留痕丢失，所以 `MesDemandCancelService.truncateMessage`
统一压到 500 字符（压缩空白 + 截断加省略号）再写库。

### 6.6 前端

| 文件 | 改动 |
|---|---|
| `components/SalesOrderManage.vue:1206` | 「废弃」确认文案改为：会同步撤销该订单已下发到 MES 的需求任务，撤销失败的行请在待产零件页筛选「撤销失败」人工处理 |
| `components/SalesOrderManage.vue:2286` | 「关联废弃」文案同上 |
| `components/ProductionItemManage.vue:610` | `mesProductionStatusOptions` 增加 `CANCELLED 已撤销` / `CANCEL_FAILED 撤销失败` |
| `components/ProductionItemManage.vue:928` | `filterProductionRows` 默认视图排除 `SUCCESS` + `CANCELLED`；**用户主动按状态筛选时必须放行**，否则筛「已撤销」会被前端二次过滤成空列表（后端已单开 `case CANCELLED`，前端这里是同一个坑） |
| `components/ProductionItemManage.vue:1056` | 下发预览的 `blocked` 统计补 `CANCELLED` / `CANCEL_FAILED` 两项，各给一句提示 |

不改 `viewModeOptions`、不改批量操作按钮逻辑（`CANCELLED` 行会被后端 `skipReason` 拦住）。

**已知信息缺口**：主表格没有「生产下发状态」列，用户按「撤销失败」筛出来后只能看到零件行，
看不到失败原因（`mes_production_message` 也没展示）。要定位具体原因目前只能查审计汇总或数据库。
是否补一列状态 + 原因 tooltip 待定（会改变默认视图宽度）。

### 6.7 测试

| 用例 | 覆盖点 |
|---|---|
| `MesDemandCancelServiceTest`（新，10 例） | 0 / 1030900002 / 1030700000 → CANCELLED；1030700006 → CANCEL_FAILED 且不重试；超时后用**同 bizId** 重发一次；两次超时 → CANCEL_FAILED 且 message 含"结果未知"；同 allocation 多条 SUCCESS 的汇总规则；连续 5 条传输失败熔断；无候选不调 MES；恢复委托 Mapper；`1_030_900_002` 归一化 |
| `SalesOrderLinkedChangeNoticeMigrationTest` | 迁移版本门禁抬到 `73` |
| `ProductionItemModuleSplitStage2Test` | 源码文本断言同步到 `NOT IN ('SUCCESS','CANCELLED')` + 两个新筛选分支 |

本次没有新增 API 级集成测试（`MesDemandCancelApiTest` 等）——撤销逻辑的入口只做
`scheduleAfterCommit` 一行委托，端到端行为靠上面的单测 + 手工验收覆盖。
| `SalesOrderLinkedChangeNoticeMigrationTest` | 版本门禁 72 |
| `DataCenterEndpointContractTest` | 新端点定义可用、超时下限 15000 |
| 前端 `sales-order-management-*.workflow.test.js` | 确认框文案断言同步更新 |

---

## 7. 时序与事务

MES 调用**不能**放在废弃事务里：HTTP 耗时 + MES 失败绝不能回滚本地废弃（MES 也认可这个口径）。

```
changeSalesOrderStatus / discardOrder  (@Transactional)
  ├─ 本地状态更新、审计
  └─ scheduleAfterCommit(salesOrderId, reason)
        └─ TransactionSynchronization.afterCommit
              └─ MesDemandCancelService.cancel(...)   ← 无外层事务，逐行自动提交
                    for each batchItem:
                        client.delete()  → 写 batch_item 状态（单行提交）
                    for each allocation:
                        汇总写 allocation 状态（单行提交）
                    审计 SALES_ORDER_MES_DEMAND_CANCEL（一条汇总）
```

关键点：

- **取消执行方法不加 `@Transactional`**。逐行自动提交，中途进程中断不会丢已完成的结果，也不会把已删的 MES 任务回滚成"未删"。
- 逐条 try/catch，任何单条异常都不向外抛。
- **不设总时间预算**，正常情况下把候选行全部处理完，不做人为截断。
- **单条超时 15s**（端点配置的 `minimumTimeoutMs`），这是防"单条死等"的底线。
- **连续失败熔断**：连续 5 条都因超时/连接异常失败，判定 MES 不可用，剩余行不再逐条等超时，
  直接写 `CANCEL_FAILED`（message 注明"MES 不可用，未执行"）。这是唯一的提前终止条件，
  目的是避免 MES 挂掉时几十行 × 15s 逐个耗完，而不是给正常流程设上限。

---

## 8. 已知限制（必须让业务知道）

1. **成功率取决于时间窗口**。只有 MES 还停在"开立"的任务能删；MES 计划员一提交审核就全部 `1030700006`。订单废弃通常发生在下发后数小时/数天，**实际可能是"大部分失败"**。
2. **删不掉的任务由 MES 侧业务跟进**。QPO 只负责发删除请求、把结果记录并展示在待产零件页
   （「撤销失败」筛选）+ 审计。任务后续怎么处理是 MES 的事，QPO 不做兜底、不做待办通道、
   不做自动重试（已决定，见 3 节）。
3. **没有状态查询接口**，QPO 无法预判可删性，只能盲调。
4. **超时重发不是 100% 可靠**：MES 侧幂等记录若仍在 `PROCESSING`（进程中断未回写），重发会拿到 `1030900003`，最终落 `CANCEL_FAILED`（message 注明"结果未知"）。
5. **删除 ≠ 作废**。MES 的"已删除"是逻辑删除、业务视图查不到；"已作废(90)"是保留记录的状态变更。QPO 页面写「已撤销」，对应 MES 的已删除。
6. **撤销后重新下发必须换新 bizId** —— 本方案天然满足：`MesProductionDispatchService.newProductionBizId()` 每次生成新的 `QPO-DEM-...`（`reserveProductionBatch` 不复用旧批次），无需额外处理。

---

## 9. 验证清单

1. 待产行下发成功 → 订单「废弃」→ 该行 `mes_production_status` 变 `CANCELLED`，MES 需求任务列表里查不到。
2. MES 已提交审核的行 → 废弃 → 落 `CANCEL_FAILED`，`mes_production_message` 带 `1030700006`，待产零件页可按「撤销失败」筛出。
3. 订单「关联废弃」→ SUCCESS 行 `discarded` 仍为 false（保持原有跳过逻辑），但 MES 需求任务已被撤销。
4. 废弃订单里 PENDING 的待产行，第二天定时下发**不会**再被选中（缺口 3 修复）。
5. 「恢复」后 `CANCELLED` / `CANCEL_FAILED` 行都变回 `PENDING`，可重新下发且生成新 bizId。
6. 撤销失败时订单废弃仍然成功返回，前端不报错。
7. 断开 MES（改错 base url）→ 废弃 → 行落 `CANCEL_FAILED`，订单废弃成功，且连续 5 条后熔断不再逐条等超时。
8. 后端全量测试通过（基线 1711 用例），前端 `npm test` + `vite build` 通过。
