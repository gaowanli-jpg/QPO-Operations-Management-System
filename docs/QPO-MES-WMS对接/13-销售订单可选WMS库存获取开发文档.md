# 销售订单可选 WMS 库存获取开发文档

> 2026-10-10 销售订单零件与 allocation 联动收口：`sales_order_item.allocation_id` 与
> `order_delivery_ticket_allocation.id` 建立一对一关联。物料号确认后，销售订单零件立即关联全量
> `PRODUCTION_REQUIRED` 行；WMS 拆分后原行保留为待产行，锁已入库 / 锁在产行追加到订单末尾。
> 库存完全覆盖时，原待产行和 allocation 仍保留，数量为 `0`，仅从待产池、锁库池和仪表盘操作统计中过滤。
> 销售订单零件与锁库拆行的数量均可编辑，编辑会同步对应 allocation；WMS 已完成后不回写提单及历史外部请求快照。
> 2026-10-10 WMS 请求口径修正：销售订单入口仅处理数量大于 0 的有效 `PRODUCTION_REQUIRED` Allocation，
> WMS 请求数量、请求快照、恢复和响应拆分校验均以 Allocation 为准。提单只用于来源追溯，
> 不参与新流程的数量校验，也不写入新流程 WMS 状态。V59 将活动快照唯一性按 Allocation 隔离，保留历史无 Allocation 快照按提单恢复。

## 1. 目标流程

```text
已提零件
  └─ MES 物料号成功
       └─ 创建全量 ACTIVE / PRODUCTION_REQUIRED allocation
            ├─ 不获取 WMS：按全量数量下发 MES 生产
            └─ 销售订单详情获取 WMS：拆为锁库结果和剩余待生产数量
```

已提零件默认池只包含物料号尚未成功的提单行。库存查询是销售订单详情中的可选动作，不再是进入待产池的前置条件。

## 2. 默认生产 allocation

`ProductionAllocationLifecycleService.recordMaterialSuccess` 是统一幂等入口。MES 即时成功、回调成功、去重 follower 成功和处理中任务恢复成功都调用该服务。它锁定提单行，更新已确认物料号，并在尚无对应分配时创建全量 `PRODUCTION_REQUIRED` 行。初始数量来自物料号成功时的提单需求；之后 WMS 只读取并处理 Allocation 当前数量，不读取提单数量。

allocation 使用 `allocation_source` 区分 `MES_MATERIAL`、`WMS_RESERVATION` 和历史 `LEGACY` 行。V57 为每个安全的历史物料号成功提单补建全量生产行。已有 allocation 保持原样；存在 WMS 处理中、未知结果、响应已保存或本地应用失败的提单不自动补建，需沿原批次恢复核对。

## 3. 销售订单级 WMS 操作

- 页面入口：销售订单详情 > 锁库拆行 > 获取库存信息。
- 接口：`POST /api/market/sales-order-management/orders/{salesOrderId}/wms-allocation/batch`。
- 处理范围：订单关联的全部提单，包括 RFQ 派生提单；后端查询不受详情页分页影响。
- 权限：`market:sales-order:wms-allocate`，角色默认映射沿用拥有旧 `market:delivered-item:wms-allocate` 权限的市场角色。
- 执行方式：创建 `WMS_ALLOCATION_BATCH` 异步任务，任务输入保留销售订单 ID 和 Allocation ID；审计摘要记录订单 ID、任务 ID、Allocation 数和合格行数，不记录外部请求体或凭据。
- 逐行处理：未获取物料号、已废弃、已完成 WMS、MES 已开始 / 结果未知 / 已成功的行返回跳过原因；WMS 处理中和本地应用失败行由既有批次恢复逻辑处理。没有合格行时不创建任务。

旧 `/api/order-delivery/tickets/wms-allocation/batch` 接口及 `market:delivered-item:wms-allocate` 权限仍保留兼容；已提零件页面移除 WMS 按钮和 WMS 待办筛选。

## 4. 数量拆分与互斥

WMS 请求以每条有效生产 Allocation 的 `qty` 生成请求快照。同一销售订单、客户订单号、物料号和客户项目号下的请求数量为参与聚合的 Allocation 数量之和。响应应用事务锁定销售订单、来源提单和对应 Allocation，逐条验证 `stockFrozenCount + inProductionCount + surplusCount = reservation.reserveCount = allocation.qty`。来源提单只用于追溯，不参与新流程数量校验或状态更新。库存锁定和在产锁定数量生成相应锁库 allocation；`surplusCount` 更新到对应待生产行。无剩余待生产数量时，原待生产 allocation 与销售订单待产行仍保留并置为 `0`，不从销售订单明细删除。

MES 生产与 WMS 获取均先以稳定顺序锁定相同的 `order_delivery_ticket` 行：

| 状态 | MES 生产 | WMS 获取 |
| --- | --- | --- |
| 从未获取 WMS / WMS 明确失败 | 按原提单全量生产 | 可以获取 |
| WMS 处理中、结果未知、响应待应用 | 阻止 | 只恢复原批次 |
| WMS 已成功 | 只处理有效的剩余待生产 allocation | 跳过，禁止重复获取 |
| MES 生产处理中、结果未知或已成功 | 不重复提交 | 禁止获取 |
| MES 生产明确失败 | 可以按失败分配重试 | 可以获取并拆分 |

分配行状态必须为 `ACTIVE` 且 `qty > 0` 才能进入待产列表、锁库列表、MES 下发和仪表盘待产统计。销售订单详情和锁库拆行页面保留并显示数量为 `0` 的关联行。旧提单批量接口继续将提单 ID 解析到生产 Allocation；既有 `allocation_id IS NULL` 的 WMS 快照仍按原提单口径恢复。

## 5. 任务与审计

- 前端显示异步任务状态与逐行 WMS 结果；任务未完成时可刷新任务，不重复提交。
- 文件日志继续使用现有 WMS batch / group / recovery 事件，并包含 `taskId`、`batchId`、`externalBizId`、`saleOrderNo`、数量、状态和耗时摘要；异常堆栈进入 `qpo-error.log`。
- 销售订单入口的提交审计动作：`SALES_ORDER_WMS_ALLOCATION_SUBMIT`，模块 `MARKET`，target 为 `system_task`。
- 不记录 token、Authorization header、完整 WMS 请求 / 响应、完整工艺快照或材料数据。

## 6. Flyway 与回归

V57 新增 allocation 来源字段、全量生产行回填、幂等唯一索引和销售订单 WMS 操作权限。V1/V2 与历史已执行迁移保持不变。

重点回归覆盖物料号成功各入口幂等建行、WMS 全量 / 部分覆盖数量守恒、WMS 和 MES 并发互斥、订单分页外和 RFQ 派生提单、逐行跳过原因、旧接口权限兼容，以及迁移不处理 WMS 未决提单。

## 7. 按选中 Allocation 获取库存

锁库拆行表支持表头全选和单行选择。选择框只允许有效、数量大于 0、物料号已确认且尚未完成 MES/WMS 处理的 `PRODUCTION_REQUIRED` Allocation；锁入库、锁在产、零数量和已废弃行不能选中。

批量按钮仍调用：

```text
POST /api/market/sales-order-management/orders/{salesOrderId}/wms-allocation/batch
{
  "allocationIds": [101, 102]
}
```

空请求继续表示整单获取库存。选中请求只提交当前销售订单中的指定 Allocation，仍使用 `WMS_ALLOCATION_BATCH`、原有客户订单号 / 客户项目号分组、恢复和响应应用逻辑。选中行在提交后刷新锁库拆行和任务结果，未选中的待产行保持原状态。
