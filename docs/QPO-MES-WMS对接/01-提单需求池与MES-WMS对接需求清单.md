# QPO 提单需求池与 MES/WMS 对接需求清单

> 2026-08-08 两级工艺主数据阶段 15 收口：`order_delivery_ticket.process_route` 统一保存标准工艺快照，内部字段包含 `processDetailId / processCode / categoryName / detailName / hourlyRate / unitWorkHours / lineCost / sortNo`；WMS allocation 复制该快照用于展示和追溯；MES 生产下发只对外传 `processCode / categoryName / detailName / unitWorkHours / sortNo`，不传 QPO 内部工艺成本字段。

## 1. 背景

QPO 原规划中，`order_delivery_ticket` 作为已提单零件明细表向 APS 提供同步数据。当前需求调整为不再对接 APS，改为对接 MES 和 WMS：

- MES 负责根据 QPO 提供的零件关键信息生成或返回物料号。
- WMS 负责根据物料号等信息返回库存覆盖结果。
- QPO 需要把一条已提单需求拆分为库存锁定、在产锁定和待生产数量明细，形成后续生产/仓储协同的数据基础。

本需求清单先冻结业务口径、数据职责、页面模块、权限日志边界，后续再拆分为具体开发阶段。

## 2. 已确认决策

### 2.1 不再对接 APS

- 后续新流程不再以 APS 为目标系统。
- 旧 APS 数据源能力仅作为历史兼容出口，后续需要按阶段退役或改造；它不是新目标系统，也不作为本需求的新数据出口。
- 新增 MES/WMS 能力不得复用 APS 数据源口径或把 APS 描述为后续目标系统。
- `order_process_sheet` 已退役，工艺展示和导出继续以 `order_delivery_ticket.process_route` 快照为准。

### 2.2 `order_delivery_ticket` 作为需求池

`order_delivery_ticket` 保持为 MES/WMS 原始提单需求池：

- 一次提单生成一条或多条需求行。
- 同一个订单零件 `item_id` 可以因分批提单产生多条需求行。
- `qty` 表示该提单需求行的原始需求数量。
- `process_route` 表示本批次提单时的工艺快照。
- `process_route` 使用两级工艺标准快照，工艺身份以 `processDetailId + processCode + categoryName + detailName` 固化，后续字典价格变化不改写历史提单事实。
- `material_data`、材料重量、零件净重等字段继续作为提单快照保存。
- 原始提单需求行不因 WMS 拆分而删除或改写为多行。

### 2.3 新增锁库明细子表

采用推荐方案：新增 `order_delivery_ticket_allocation` 子表保存拆分结果。

- `order_delivery_ticket` 是 MES/WMS 需求源和需求池，不因 WMS 拆分而改变原始提单数量。
- `order_delivery_ticket_allocation` 是 WMS 拆分结果表，保存库存锁定、在产锁定和待生产分配结果。
- 页面可把 allocation 展示为“拆分后的锁库零件行”。
- 原始需求行和拆分明细通过 `ticket_id` 关联，方便追溯。

### 2.4 同步触发方式

MES/WMS 同步采用“页面按钮 + 可重试任务记录”的方式：

- 用户在页面主动触发。
- 后端创建任务并记录任务状态。
- 失败后可查看原因并重试。
- 不采用一开始就后台自动定时处理的方式，避免外部系统异常时难以追踪。

## 3. 目标模块

在左侧 **订单** 分组下新增两个同级模块：

| 模块名 | 数据来源 | 模块职责 |
| --- | --- | --- |
| 已提零件 | `order_delivery_ticket` | 展示提单后的 MES/WMS 原始需求池，维护 MES 物料号获取和 WMS 锁库提交入口。 |
| 锁库零件 | `order_delivery_ticket_allocation` | 展示 WMS 拆分结果，包括库存锁定、在产锁定和待生产明细。 |

模块命名保持四字，和现有“订单管理”“历史订单”风格一致。

## 4. 业务流程

### 4.1 主流程

```text
议价提单生成
  -> 写入 order_delivery_ticket
  -> 已提零件页面出现需求池记录
  -> 用户触发获取 MES 物料号
  -> MES 返回 materialNo
  -> QPO 写回 order_delivery_ticket.material_no
  -> 用户触发 WMS 锁库分配
  -> WMS 返回已入库库存、多个在产订单锁定数量
  -> QPO 写入 order_delivery_ticket_allocation
  -> 锁库零件页面展示拆分结果
```

### 4.2 MES 物料号获取

QPO 向 MES 发送：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| 图号 | `order_delivery_ticket.part_no` | 必填。 |
| 版本 | `order_delivery_ticket.version` | 可为空，但需要按空版本统一传输。 |
| 图纸 | 关联 `item_quote_version.drawing_url` 或文件访问地址 | 具体传 URL、文件 ID 或附件流由接口契约确定。 |
| 内部项目号 | `order_delivery_ticket.internal_project_no` | 必填。 |

MES 返回：

| 字段 | 说明 |
| --- | --- |
| `materialNo` | MES 物料号，字符串，写回 QPO。 |
| `message` | 可选，返回说明或失败原因。 |

### 4.3 WMS 锁库分配

QPO 在提单需求行已有 `material_no` 后，向 WMS 发送：

| 字段 | 来源 | 说明 |
| --- | --- | --- |
| 物料号 | `order_delivery_ticket.material_no` | 必填。 |
| 图号 | `order_delivery_ticket.part_no` | 必填。 |
| 版本 | `order_delivery_ticket.version` | 可为空。 |
| 客户 | `order_rfq.customer_name` | 必填。 |
| 内部项目号 | `order_delivery_ticket.internal_project_no` | 必填。 |
| 需求数量 | `order_delivery_ticket.qty` | 建议一并发送，便于 WMS 校验。 |

WMS 返回动态长度的在产订单数组：

```json
{
  "materialNo": "MAT-000123",
  "inStockLockedQty": 3,
  "inProductionLocks": [
    { "productionOrderNo": "MO-001", "lockedQty": 4 },
    { "productionOrderNo": "MO-002", "lockedQty": 2 }
  ],
  "message": "success"
}
```

QPO 支持 `inProductionLocks` 为 0 条、1 条或多条。

### 4.4 拆分规则

以提单需求数量 10 为例：

| 来源 | 数量 | 生成行 |
| --- | ---: | --- |
| 已入库库存 | 3 | 生成 1 条 `IN_STOCK_LOCK` |
| 在产订单 `MO-001` | 4 | 生成 1 条 `IN_PRODUCTION_LOCK` |
| 在产订单 `MO-002` | 2 | 生成 1 条 `IN_PRODUCTION_LOCK` |
| 剩余待生产 | 1 | 生成 1 条 `PRODUCTION_REQUIRED` |

校验规则：

- 已入库锁定数量必须大于等于 0。
- 每条在产锁定行必须有 `productionOrderNo` 和大于 0 的 `lockedQty`。
- 已入库锁定数量 + 所有在产锁定数量不能超过提单需求数量。
- 若覆盖数量小于提单需求数量，QPO 自动生成 `PRODUCTION_REQUIRED` 行。
- 若 WMS 返回覆盖数量超过提单需求数量，QPO 判定为异常，不静默截断。
- 同一 `ticket_id` 重复执行 WMS 分配时，需要先明确采用覆盖旧分配、禁止重复分配或生成新版本；建议默认采用“失败可重试，成功后禁止重复覆盖，需管理员重置/重算权限另行设计”。

## 5. 数据需求

### 5.1 `order_delivery_ticket` 新增字段

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `material_no` | `VARCHAR(100)` | MES 返回的物料号。 |
| `mes_sync_status` | `VARCHAR(30)` | `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`。 |
| `mes_sync_message` | `TEXT` | MES 同步摘要或失败原因。 |
| `mes_synced_at` | `TIMESTAMP` | 最近一次 MES 成功同步时间。 |
| `wms_allocate_status` | `VARCHAR(30)` | `PENDING` / `PROCESSING` / `SUCCESS` / `FAILED`。 |
| `wms_allocate_message` | `TEXT` | WMS 分配摘要或失败原因。 |
| `wms_allocated_at` | `TIMESTAMP` | 最近一次 WMS 成功分配时间。 |

状态建议：

```text
PENDING     未处理
PROCESSING 处理中
SUCCESS     成功
FAILED      失败
```

### 5.2 新增 `order_delivery_ticket_allocation`

建议字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| `id` | `SERIAL` | 主键。 |
| `ticket_id` | `INTEGER` | 关联 `order_delivery_ticket.id`。 |
| `rfq_id` | `INTEGER` | 冗余订单 ID，方便查询和数据权限过滤。 |
| `item_id` | `INTEGER` | 冗余订单零件 ID，方便追溯。 |
| `allocation_type` | `VARCHAR(40)` | `IN_STOCK_LOCK` / `IN_PRODUCTION_LOCK` / `PRODUCTION_REQUIRED`。 |
| `qty` | `INTEGER` | 本分配行数量。 |
| `material_no` | `VARCHAR(100)` | 物料号快照。 |
| `part_no` | `VARCHAR(100)` | 图号快照。 |
| `version` | `VARCHAR(20)` | 版本快照。 |
| `customer_name` | `VARCHAR(255)` | 客户快照。 |
| `internal_project_no` | `VARCHAR(100)` | 内部项目号快照。 |
| `wms_production_order_no` | `VARCHAR(100)` | 在产订单号，仅 `IN_PRODUCTION_LOCK` 必填。 |
| `source_payload` | `JSONB` | WMS 返回原始摘要，不保存敏感信息。 |
| `status` | `VARCHAR(30)` | 分配行状态，初期可固定为 `ACTIVE`。 |
| `created_at` | `TIMESTAMP` | 创建时间。 |
| `updated_at` | `TIMESTAMP` | 更新时间。 |

索引建议：

| 索引 | 目的 |
| --- | --- |
| `(ticket_id, id)` | 查看某提单需求的拆分结果。 |
| `(rfq_id, id)` | 按订单追溯。 |
| `(material_no, id)` | 按物料号查询锁库结果。 |
| `(part_no, version, id)` | 按图号版本查询。 |
| `(allocation_type, id)` | 按分配类型筛选。 |
| `(wms_production_order_no, id)` | 按在产订单号筛选。 |

## 6. 页面需求

### 6.1 已提零件

页面定位：需求池管理和外部系统同步入口。

列表字段建议：

- 提单行 ID
- RFQ 号
- 客户
- 图号
- 版本
- 物料号
- 提单数量
- 内部项目号
- 客户订单号
- 订单类型
- MES 状态
- WMS 分配状态
- 交货日期
- 工艺快照摘要
- 创建/提单时间

筛选条件建议：

- 客户
- RFQ 号
- 图号
- 版本
- 物料号
- 内部项目号
- MES 状态
- WMS 分配状态
- 交货日期范围

操作建议：

- 获取 MES 物料号
- 重试 MES 物料号获取
- 提交 WMS 锁库分配
- 重试 WMS 锁库分配
- 查看锁库拆分结果
- 查看工艺快照
- 预览图纸

### 6.2 锁库零件

页面定位：WMS 分配结果查询。

列表字段建议：

- 分配行 ID
- 提单行 ID
- RFQ 号
- 客户
- 图号
- 版本
- 物料号
- 分配类型
- 数量
- 在产订单号
- 内部项目号
- 状态
- 创建时间

筛选条件建议：

- 客户
- RFQ 号
- 图号
- 版本
- 物料号
- 内部项目号
- 分配类型
- 在产订单号
- 创建时间范围

分配类型中文展示：

| 值 | 中文 |
| --- | --- |
| `IN_STOCK_LOCK` | 已入库库存锁定 |
| `IN_PRODUCTION_LOCK` | 在产库存锁定 |
| `PRODUCTION_REQUIRED` | 待生产数量 |

### 6.3 页面关系

- 已提零件点击“查看锁库结果”可跳转或弹窗展示该 `ticket_id` 下的 allocation 明细。
- 锁库零件点击“提单行 ID”可反查原始已提零件需求。
- 两个页面都需要分页，不一次性加载全量数据。
- 两个页面都需要复用现有 QPO 表格、筛选、分页、权限按钮风格。

## 7. 后端接口需求

### 7.1 已提零件查询

建议接口：

```text
GET /api/order-delivery/tickets
GET /api/order-delivery/tickets/{ticketId}
```

要求：

- 支持分页。
- 支持客户、RFQ、图号、版本、物料号、内部项目号、同步状态筛选。
- 接入订单数据范围。
- 无数据权限时返回空列表，不泄露数据。

### 7.2 MES 物料号任务

建议接口：

```text
POST /api/order-delivery/tickets/mes-material/batch
```

要求：

- 仅保留批量入口；单个零件也通过 `ticketIds` 长度为 1 的批量请求提交。
- 触发后创建可追踪任务。
- 成功写回 `material_no`。
- 失败写回 `mes_sync_status=FAILED` 和失败摘要。
- 已有物料号时默认不重复覆盖，除非使用重试/刷新动作。

### 7.3 WMS 锁库任务

建议接口：

```text
POST /api/order-delivery/tickets/{ticketId}/wms-allocation
POST /api/order-delivery/tickets/wms-allocation/batch
```

要求：

- 仅允许对已有 `material_no` 的提单行执行。
- 执行前锁定提单行，避免重复分配。
- 成功后写入 `order_delivery_ticket_allocation`。
- allocation 数量合计必须等于 `order_delivery_ticket.qty`。
- WMS 返回异常或数量超额时任务失败，不写入半成品拆分结果。

### 7.4 锁库结果查询

建议接口：

```text
GET /api/order-delivery/locked-items
GET /api/order-delivery/tickets/{ticketId}/allocations
```

要求：

- 支持分页。
- 支持按客户、RFQ、图号、版本、物料号、分配类型、在产订单号筛选。
- 接入订单数据范围。

## 8. 权限需求

新增权限必须纳入角色授权模块树，不新增可分配 `common:*` 权限。

建议权限点：

| 模块 | 权限编码 | 类型 | 说明 |
| --- | --- | --- | --- |
| 已提零件 | `market:delivered-item:view` | `MENU` | 查看已提零件需求池。 |
| 已提零件 | `market:delivered-item:mes-sync` | `BUTTON/API` | 获取或重试 MES 物料号。 |
| 已提零件 | `market:delivered-item:wms-allocate` | `BUTTON/API` | 提交或重试 WMS 锁库分配。 |
| 已提零件 | `market:delivered-item:file-preview` | `API` | 预览图纸。 |
| 锁库零件 | `market:locked-item:view` | `MENU` | 查看锁库零件分配结果。 |

默认角色建议：

| 角色 | 建议权限 |
| --- | --- |
| 超级管理员 | 全部新增权限。 |
| 市场主管 | 查看已提零件、查看锁库零件、触发 MES/WMS。 |
| 市场人员 | 查看已提零件、查看锁库零件，是否允许触发 MES/WMS 按公司流程决定。 |
| 工程核价员 | 默认不需要。 |
| 只读账号 | 只给查看权限。 |

## 9. 日志与审计需求

### 9.1 操作日志

建议新增动作：

| 动作编码 | 中文含义 | 记录摘要 |
| --- | --- | --- |
| `DELIVERY_MES_MATERIAL_SYNC_SUBMIT` | 提交 MES 物料号批量获取 | taskId、ticketIds、ticketCount。 |
| `DELIVERY_MES_MATERIAL_SYNC_SUCCESS` | MES 物料号获取成功 | ticketId、materialNo。 |
| `DELIVERY_MES_MATERIAL_SYNC_FAILED` | MES 物料号获取失败 | ticketId、失败摘要。 |
| `DELIVERY_WMS_ALLOCATION_SUBMIT` | 提交 WMS 锁库分配 | ticketId、materialNo、qty。 |
| `DELIVERY_WMS_ALLOCATION_SUCCESS` | WMS 锁库分配成功 | ticketId、allocationCount、inStockQty、inProductionQty、productionRequiredQty。 |
| `DELIVERY_WMS_ALLOCATION_FAILED` | WMS 锁库分配失败 | ticketId、失败摘要。 |

### 9.2 文件日志

主流程日志建议事件名：

```text
MES material sync submitted ticketId={} taskId={}
MES material sync started ticketId={} taskId={}
MES material sync completed ticketId={} taskId={} materialNo={} durationMs={}
MES material sync failed ticketId={} taskId={} durationMs={}

WMS allocation submitted ticketId={} taskId={}
WMS allocation started ticketId={} taskId={} materialNo={} demandQty={}
WMS allocation completed ticketId={} taskId={} allocationCount={} inStockQty={} inProductionQty={} productionRequiredQty={} durationMs={}
WMS allocation failed ticketId={} taskId={} durationMs={}
```

要求：

- 失败日志必须进入 `qpo-error.log`，并带 `taskId` 和异常堆栈。
- 不记录完整图纸内容、完整请求 token、外部系统密钥。
- WMS 返回明细可记录数量摘要，不在 INFO 中打印完整大 JSON。
- 需要保留 `requestId`，方便从页面操作反查后端文件日志。

## 10. 外部接口契约要求

### 10.1 MES

MES 接口需要明确：

- 认证方式。
- 请求字段名和是否必填。
- 图纸传递方式：URL、文件 ID、文件流或其他。
- 同一图号+版本+内部项目号重复请求时，是否幂等返回同一物料号。
- 失败时错误码和错误信息格式。

### 10.2 WMS

WMS 接口需要明确：

- 认证方式。
- 请求字段名和是否必填。
- 是否以 QPO 的需求数量作为锁库上限。
- 已入库库存锁定是否由 WMS 在接口调用时真实锁定。
- 在产订单数组格式，必须包含每个在产订单号及对应可锁定数量。
- 重复调用同一 `ticket_id` 时，WMS 是否幂等。
- WMS 返回成功但 QPO 写库失败时的补偿方式。

## 11. 数据一致性要求

- `order_delivery_ticket.qty` 不因 allocation 生成而变化。
- `order_delivery_ticket_allocation` 是 WMS 拆分结果，不改写 `order_delivery_ticket` 原始需求行。
- `order_delivery_ticket_allocation.qty` 合计必须等于原始提单行 `qty`。
- 同一 `ticket_id` 成功分配后，默认不允许再次成功写入另一套 allocation，避免重复锁库。
- MES 物料号成功后应作为稳定快照保存，后续 WMS 使用 QPO 保存的 `material_no`。
- WMS 分配成功写库必须在一个事务中完成：清理/创建 allocation、更新 ticket 状态、记录摘要。
- 外部接口失败不得产生部分 allocation 行。

## 12. 非目标范围

本需求清单暂不包含：

- MES/WMS 的具体网络协议实现细节。
- 外部系统密钥管理方案。
- 自动定时同步。
- WMS 锁库释放、撤销或反向回滚。
- 已成功 WMS 分配后的重新拆分。
- 把库存数量维护搬进 QPO。
- 恢复 APS 对接；旧 APS 出口仅为历史兼容，不是新目标系统。
- 删除旧 APS 代码的具体实施步骤。

## 13. 验收口径

### 13.1 功能验收

- 提单生成后，已提零件页面能看到对应需求池记录。
- 对单条已提零件执行 MES 物料号获取，成功后能看到物料号。
- 无物料号的已提零件不能提交 WMS 锁库。
- WMS 返回多个在产订单时，QPO 能动态生成多条在产库存锁定行。
- WMS 覆盖不足时，QPO 自动生成待生产数量行。
- 锁库零件页面能按分配类型、图号、物料号、在产订单号查询。
- 原始提单需求行仍可追溯到订单、零件、工艺快照和图纸。
- 文档和开发口径明确 `order_delivery_ticket` 是 MES/WMS 需求池，`order_delivery_ticket_allocation` 是 WMS 拆分结果，旧 APS 出口只是历史兼容。

### 13.2 权限验收

- 未授权 `market:delivered-item:view` 时，左侧不显示已提零件，直接访问被拦截。
- 未授权 `market:locked-item:view` 时，左侧不显示锁库零件，直接访问被拦截。
- 未授权 MES/WMS 操作权限时，按钮不可见或不可用，后端接口返回 403。
- 授权树中能按订单模块展开看到已提零件和锁库零件。

### 13.3 日志验收

- 提交 MES/WMS 任务后，操作日志能查到提交动作。
- MES/WMS 成功后，操作日志能查到成功摘要。
- MES/WMS 失败后，`qpo-error.log` 能查到 `taskId` 和异常堆栈。
- 通过 `requestId` 能从操作日志反查后端文件日志。

### 13.4 数据验收

- allocation 合计数量等于原提单需求数量。
- 多个在产订单生成多条 `IN_PRODUCTION_LOCK`。
- 已入库库存生成 `IN_STOCK_LOCK`。
- 剩余数量生成 `PRODUCTION_REQUIRED`。
- WMS 返回数量超过需求时拒绝写入。

## 14. 后续开发文档建议

后续可基于本需求清单拆出企业级开发文档：

```text
docs/QPO-MES-WMS对接/02-MES-WMS对接开发文档.md
```

建议阶段：

1. 测试护栏与接口契约冻结。
2. 数据库迁移与实体 DTO。
3. 已提零件页面与查询接口。
4. MES 物料号任务。
5. WMS 锁库分配任务。
6. 锁库零件页面。
7. 权限、日志、审计、文档收口。
8. 全量回归与提交。
## 15. 阶段 7 补充：WMS 批量 reserve 现行口径

- WMS 锁库只保留后台任务 `WMS_ALLOCATION_BATCH`，单个零件和多个零件都走同一个批量入口。
- QPO 只通过数据中心 endpoint `WMS_INVENTORY_ALLOCATE` 调用 WMS，链路为 `QPO -> 数据中心 -> WMS`。
- 数据中心路径为 `/api/v1/wms/admin-api/wms/datacenter/sale-lading/reserve`。
- 请求字段为 `externalBizId`、`saleNo`、`items[].productCode`、`items[].reserveCount`、`items[].remark`。
- 响应字段为 `saleOrderNo`、`externalBizId`、`items[].productCode`、`items[].reserveCount`、`items[].stockCount`、`items[].inProductionCount`、`items[].frozenCount`、`items[].surplusCount`。
- `inProductionCount` 归入“在产覆盖”，库存覆盖为 `frozenCount - inProductionCount`，缺口为 `surplusCount`。
- QPO 按销售订单分组、按 `productCode` 聚合，再按 `sales_order_item.line_no ASC, ticket.id ASC` 稳定拆回 ticket。
- WMS 审计动作覆盖：`DELIVERY_WMS_ALLOCATION_SUBMIT`、`DELIVERY_WMS_ALLOCATION_SUCCESS`、`DELIVERY_WMS_ALLOCATION_FAILED`、`DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN`、`DELIVERY_WMS_ALLOCATION_APPLY_FAILED`、`DELIVERY_WMS_ALLOCATION_RECONCILED`。
