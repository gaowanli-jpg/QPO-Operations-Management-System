# 销售订单可选 WMS 库存获取开发文档

## 1. 目标流程

```text
已提零件
  └─ MES 物料号成功
       └─ 创建原提单数量的 ACTIVE / PRODUCTION_REQUIRED allocation
            ├─ 不获取 WMS：按全量数量下发 MES 生产
            └─ 销售订单详情获取 WMS：拆为锁库结果和剩余待生产数量
```

已提零件默认池只包含物料号尚未成功的提单行。库存查询是销售订单详情中的可选动作，不再是进入待产池的前置条件。

## 2. 默认生产 allocation

`ProductionAllocationLifecycleService.recordMaterialSuccess` 是统一幂等入口。MES 即时成功、回调成功、去重 follower 成功和处理中任务恢复成功都调用该服务。它锁定提单行，更新已确认物料号，并在尚无对应分配时创建数量为 `order_delivery_ticket.qty` 的 `PRODUCTION_REQUIRED` 行。

allocation 使用 `allocation_source` 区分 `MES_MATERIAL`、`WMS_RESERVATION` 和历史 `LEGACY` 行。V57 为每个安全的历史物料号成功提单补建全量生产行。已有 allocation 保持原样；存在 WMS 处理中、未知结果、响应已保存或本地应用失败的提单不自动补建，需沿原批次恢复核对。

## 3. 销售订单级 WMS 操作

- 页面入口：销售订单详情 > 提单行 > 获取库存信息。
- 接口：`POST /api/market/sales-order-management/orders/{salesOrderId}/wms-allocation/batch`。
- 处理范围：订单关联的全部提单，包括 RFQ 派生提单；后端查询不受详情页分页影响。
- 权限：`market:sales-order:wms-allocate`，角色默认映射沿用拥有旧 `market:delivered-item:wms-allocate` 权限的市场角色。
- 执行方式：创建 `WMS_ALLOCATION_BATCH` 异步任务，任务输入保留销售订单 ID 和提单 ID；审计摘要记录订单 ID、任务 ID、总行数和合格行数，不记录外部请求体或凭据。
- 逐行处理：未获取物料号、已废弃、已完成 WMS、MES 已开始 / 结果未知 / 已成功的行返回跳过原因；WMS 处理中和本地应用失败行由既有批次恢复逻辑处理。没有合格行时不创建任务。

旧 `/api/order-delivery/tickets/wms-allocation/batch` 接口及 `market:delivered-item:wms-allocate` 权限仍保留兼容；已提零件页面移除 WMS 按钮和 WMS 待办筛选。

## 4. 数量拆分与互斥

WMS 响应应用事务锁定提单行和该提单的 allocations，验证 `stockFrozenCount + inProductionCount + surplusCount = ticket.qty`。库存锁定和在产锁定数量生成相应锁库 allocation；`surplusCount` 更新到唯一有效的待生产行。无剩余待生产数量时，将原待生产行置为 `SUPERSEDED`，不创建零数量 allocation。

MES 生产与 WMS 获取均先以稳定顺序锁定相同的 `order_delivery_ticket` 行：

| 状态 | MES 生产 | WMS 获取 |
| --- | --- | --- |
| 从未获取 WMS / WMS 明确失败 | 按原提单全量生产 | 可以获取 |
| WMS 处理中、结果未知、响应待应用 | 阻止 | 只恢复原批次 |
| WMS 已成功 | 只处理有效的剩余待生产 allocation | 跳过，禁止重复获取 |
| MES 生产处理中、结果未知或已成功 | 不重复提交 | 禁止获取 |
| MES 生产明确失败 | 可以按失败分配重试 | 可以获取并拆分 |

分配行状态必须为 `ACTIVE` 才能进入待产列表、MES 下发和仪表盘待产统计。`SUPERSEDED` 行仅作历史审计，不参与数量和工作池计算。

## 5. 任务与审计

- 前端显示异步任务状态与逐行 WMS 结果；任务未完成时可刷新任务，不重复提交。
- 文件日志继续使用现有 WMS batch / group / recovery 事件，并包含 `taskId`、`batchId`、`externalBizId`、`saleOrderNo`、数量、状态和耗时摘要；异常堆栈进入 `qpo-error.log`。
- 销售订单入口的提交审计动作：`SALES_ORDER_WMS_ALLOCATION_SUBMIT`，模块 `MARKET`，target 为 `system_task`。
- 不记录 token、Authorization header、完整 WMS 请求 / 响应、完整工艺快照或材料数据。

## 6. Flyway 与回归

V57 新增 allocation 来源字段、全量生产行回填、幂等唯一索引和销售订单 WMS 操作权限。V1/V2 与历史已执行迁移保持不变。

重点回归覆盖物料号成功各入口幂等建行、WMS 全量 / 部分覆盖数量守恒、WMS 和 MES 并发互斥、订单分页外和 RFQ 派生提单、逐行跳过原因、旧接口权限兼容，以及迁移不处理 WMS 未决提单。
