# 销售订单与 Allocation 关联及数量联动开发文档

## 1. 关联模型

`sales_order_item.allocation_id` 指向 `order_delivery_ticket_allocation.id`，并通过部分唯一索引保证一个 allocation 只对应一条销售订单零件行。`allocation_type` 镜像 allocation 的拆行类型：

- `PRODUCTION_REQUIRED`：待产。
- `IN_STOCK_LOCK`：锁已入库。
- `IN_PRODUCTION_LOCK`：锁在产。

物料号确认后立即创建或复用全量待产 allocation，并绑定原销售订单零件。重复回调、去重跟随和任务恢复都走同一幂等入口。

## 2. WMS 拆行

WMS 成功应用时，原销售订单零件行继续代表待产 allocation；锁已入库和锁在产 allocation 按正数量各追加一条普通销售订单零件行，并追加到销售订单末尾。库存完全覆盖时，待产行不删除，`sales_order_item.qty` 与 allocation 数量均为 `0`。池子和仪表盘过滤零数量行，销售订单详情与锁库拆行保留零数量行。

WMS 重试、恢复和重复应用按 `allocation_id` 查找已有销售订单行，不能重复追加。WMS 应用阶段校验原始拆分数量守恒；之后人工调整某一行只同步该行，不自动改分配的其他行，也不重写已生成的 WMS/MES 请求快照。

## 3. 数量编辑

销售订单详情保存销售订单零件数量时，同事务更新关联 allocation。新 WMS 流程始终以 Allocation 为操作对象和数量来源；无论 WMS 是否已执行，都不为 WMS 目的同步修改来源提单数量。WMS 请求快照保存 Allocation ID 和请求时数量；请求处理中禁止编辑对应数量，历史请求快照不随之后编辑改写。

锁库拆行页面通过 `POST /api/market/sales-order-management/orders/{salesOrderId}/allocation-splits/batch` 批量保存 allocation 数量，同时更新对应销售订单零件，数量允许为 `0`。该操作不因 MES 生产或提库存工单已成功而禁止。

每次保存记录 `SALES_ORDER_ALLOCATION_QTY_UPDATE` 审计动作，包含订单、销售订单行、allocation、旧数量、新数量和是否同步提单。

## 4. 历史数据

Flyway `V58__sales_order_item_allocation_link.sql` 追加字段、约束和索引，并按销售订单零件、提单和 allocation 回填关联。直销订单同一源零件对应多个提单时先拆成提单行；历史锁库 / 在产 allocation 追加销售订单零件行。完全锁库但缺少待产行时补建零数量待产 allocation，不改动 V1、V2 或其他已执行迁移。

## 5. WMS 请求来源

销售订单锁库拆行页仅对该订单有效、数量大于 `0` 的 `PRODUCTION_REQUIRED` Allocation 发起 WMS 请求。请求数量、快照、结果恢复和拆分恒等式都以 Allocation 为准；提单 ID 仅保留来源追溯。V59 为快照增加 `allocation_id`，活动请求按 Allocation 互斥；同一来源提单下的不同 Allocation 可以进入同一批次。历史 `allocation_id IS NULL` 快照仍使用原提单唯一性和恢复逻辑。
