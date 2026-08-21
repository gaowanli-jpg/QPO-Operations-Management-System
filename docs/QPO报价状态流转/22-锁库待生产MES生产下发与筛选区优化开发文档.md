# 锁库待生产 MES 生产下发与筛选区优化开发文档
> MES 真实批量阶段 7 收口：正式和测试部署统一使用数据中心 endpoint，物料批量为 `MES_MATERIAL_RESOLVE` -> `/api/v1/mes/openapi/material-preparation/create`，生产需求批量为 `MES_PRODUCTION_DISPATCH` -> `/api/v1/mes/openapi/demand/create`，最低超时 `60000ms`。出站链路固定为 `QPO -> 数据中心 -> MES`；物料完成回调固定为 `MES -> 数据中心 -> QPO` 的 `POST /api/integrations/mes/material-callback`。权限不新增，继续复用 `market:delivered-item:mes-sync` 和 `market:production-item:mes-production-submit`；批次核对动作标签保留 `MES_INTEGRATION_BATCH_RECONCILE`。数据中心必须透传 QPO 的 4xx/429/5xx 语义，任何操作日志和文件日志都不得记录完整请求、完整响应、完整回调内容、Authorization、Cookie、任一段 Token 或凭据。
最后更新：2026-08-02

> 阶段 31 当前口径：本文档保留为历史阶段记录，待生产下发职责已被 `31-待产零件与锁库零件模块解耦开发文档.md` 替代。当前用户入口为待产零件 `/production-items`，后端接口为 `POST /api/order-delivery/production-items/mes-production`，权限为 `market:production-item:mes-production-submit`，操作日志动作使用 `PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT / SUCCESS / FAILED`。锁库零件模块只保留库存锁定结果展示。
## 1. 背景

当前 QPO 已形成完整的 MES/WMS 主链路：

- **已提零件** 模块读取 `order_delivery_ticket`，负责获取 MES 物料号、提交 WMS 锁库。
- **锁库零件** 模块读取 `order_delivery_ticket_allocation`，展示 WMS 返回后的库存锁定、在产锁定和待生产拆分结果。
- WMS 成功后，一个原始提单行会被拆分为多条 allocation：
  - `IN_STOCK_LOCK`：已入库库存锁定。
  - `IN_PRODUCTION_LOCK`：在产库存锁定。
  - `PRODUCTION_REQUIRED`：待生产数量。

阶段 31 后需要在 **待产零件** 模块承接待生产流转：用户手动选择待生产零件，将这些待生产信息发送给 MES 的生产流程接口。

业务口径：

- 只下发 `allocation_type = PRODUCTION_REQUIRED` 的待产零件行。
- 普通件父子关系传空数组。
- 组合件按来源零件查询父子关系，查到就带出，查不到也传空数组，不提示、不阻断。
- 下发方式为手动触发，交互风格参考已提零件模块顶部批量按钮。
- 趁本次改造，同步优化锁库零件顶部筛选区，避免所有筛选字段平铺导致页面拥挤。

## 2. 当前问题

### 2.1 数据问题

`order_delivery_ticket_allocation.qty` 当前表示 **拆分后的数量**，不是原始提单数量。

示例：

| allocation 类型 | qty 含义 |
| --- | --- |
| `IN_STOCK_LOCK` | 已入库锁定数量 |
| `IN_PRODUCTION_LOCK` | 某个在产订单锁定数量 |
| `PRODUCTION_REQUIRED` | 待生产数量 |

而 MES 生产接口还需要知道原始提单数量：

```text
原始提单数量 = 已入库库存锁定数量 + 在产库存锁定数量 + 待生产数量
```

原始提单数量目前只在 `order_delivery_ticket.qty`。如果每次下发 MES 都实时 join 提单表获取，虽然可行，但会让生产下发结果行不够自洽，也增加后续导出、日志和追溯复杂度。

### 2.2 组合件关系问题

锁库零件行当前已经保存了来源字段：

- `source_type`
- `source_order_id`
- `source_item_id`

因此可以逐级反查到两类源零件主表：

| source_type | 来源订单表 | 来源零件表 | 组合件关系表 |
| --- | --- | --- | --- |
| `RFQ` | `order_rfq` | `item_quote_version` | `item_assembly_relation` |
| `SALES_ORDER` | `sales_order_header` | `sales_order_item` | `sales_order_item_assembly_relation` |

但锁库零件列表 / DTO 当前没有直接返回组合件父子关系，MES 生产下发时需要新增统一组包逻辑。

### 2.3 前端问题

锁库零件筛选区当前把客户、来源单号、图号、版本、物料号、内部项目号、客户订单号、客户项目号、分配类型、交货日期、搜索、刷新等控件平铺展示。

问题：

- 页面顶部拥挤，主次不清。
- 筛选动作和业务处理动作没有明显分组。
- 与已提零件模块已经优化后的工作台风格不一致。
- 用户最关心的是“哪些待生产行还没下发 MES”，但当前没有生产下发状态筛选。

## 3. 目标

### 3.1 业务目标

- 待产零件模块可以手动选择单个或多个待生产行，下发给 MES 生产流程接口。
- 只允许 `PRODUCTION_REQUIRED` 行进入 MES 生产下发。
- 下发内容包含当前锁库零件表可见的主要字段，以及原始提单数量。
- 下发内容包含组合件父子关系：
  - 普通件：`assemblyRelations = []`
  - 组合件有关系：返回父子关系数组。
  - 组合件父件本身没有原材料 BOM 时：`bom = []`，不因父件缺少材料编码阻断下发。
  - 组合件无关系：`assemblyRelations = []`，不提示、不阻断。
- 下发成功 / 失败状态回写到 allocation 行，便于页面筛选和重试。

### 3.2 技术目标

- `order_delivery_ticket_allocation` 增加原始提单数量快照字段，避免生产下发时为了数量再 join 提单表。
- 生产下发运行时主要从 allocation 快照字段组装请求，只按来源零件查询组合件关系。
- 后端仍作为最终安全边界，校验权限、数据范围、allocation 类型和状态。
- 前端不本地全量过滤，继续使用后端分页。
- 不改变已提零件、WMS 锁库、锁库结果池的数据边界。
- 不自动下发 MES，全部由用户手动触发。

## 4. 非目标

- 不做 MES 生产进度回调。
- 不做 MES 生产撤销、取消、反向回滚。
- 不把全部锁库结果行都下发给 MES。
- 不下发 `IN_STOCK_LOCK` 和 `IN_PRODUCTION_LOCK` 行。
- 不因为组合件关系缺失而阻断生产下发。
- 不因为组合件父件没有自身原材料 BOM 而阻断生产下发。
- 不改变 `order_delivery_ticket.qty`。
- 不删除 `order_delivery_ticket_allocation`，不搬表。
- 不新增 UI 库。

## 5. 核心口径

### 5.1 数量口径

新增字段：

```sql
order_delivery_ticket_allocation.ticket_qty INTEGER
```

字段含义：

- `ticket_qty`：原始提单数量，来自 `order_delivery_ticket.qty`。
- `qty`：当前 allocation 行数量。
- 当 `allocation_type = PRODUCTION_REQUIRED` 时：
  - `qty` = 待生产数量。
  - `ticket_qty` = 未锁库存前的原始提单数量。

公式：

```text
同一 ticket_id 下：
SUM(order_delivery_ticket_allocation.qty) = order_delivery_ticket_allocation.ticket_qty = order_delivery_ticket.qty
```

说明：

- `ticket_qty` 写入所有 allocation 行，不只写待生产行。
- 这样每一行都能独立解释“原始提单数量”和“当前拆分数量”。
- 后续锁库零件导出、MES 生产下发和排查日志都不需要为提单数量再 join `order_delivery_ticket`。

### 5.2 下发范围

只下发：

```text
allocation_type = PRODUCTION_REQUIRED
```

不下发：

```text
allocation_type = IN_STOCK_LOCK
allocation_type = IN_PRODUCTION_LOCK
```

前端可以允许用户勾选多种行，但后端必须对非待生产行做跳过或拒绝。推荐口径：

- 单行下发：选中行不是 `PRODUCTION_REQUIRED` 时直接提示不可下发。
- 批量下发：跳过非 `PRODUCTION_REQUIRED` 行，并在批量结果里展示 `skippedCount` 和明细原因。

### 5.3 组合件父子关系口径

统一返回字段：

```json
"assemblyRelations": []
```

RFQ 来源：

- 父件：`allocation.source_item_id -> item_quote_version.id`
- 父子关系：`item_assembly_relation.parent_item_id = source_item_id`
- 子件详情：`item_assembly_relation.child_item_id -> item_quote_version.id`

销售订单来源：

- 父件：`allocation.source_item_id -> sales_order_item.id`
- 父子关系：`sales_order_item_assembly_relation.parent_item_id = source_item_id`
- 子件详情：`sales_order_item_assembly_relation.child_item_id -> sales_order_item.id`

普通件：

- 不查或查不到关系时返回空数组。

组合件无关系：

- 返回空数组。
- 不提示。
- 不阻断。

## 6. 数据模型设计

### 6.1 新增迁移

新增迁移：

```text
Vxx__locked_item_mes_production_dispatch.sql
```

若实施时编号已被占用，使用实际下一迁移编号。

### 6.2 `order_delivery_ticket_allocation` 字段

```sql
ALTER TABLE order_delivery_ticket_allocation
    ADD COLUMN IF NOT EXISTS ticket_qty INTEGER,
    ADD COLUMN IF NOT EXISTS mes_production_status VARCHAR(30) DEFAULT 'PENDING',
    ADD COLUMN IF NOT EXISTS mes_production_request_id VARCHAR(80),
    ADD COLUMN IF NOT EXISTS mes_production_external_id VARCHAR(120),
    ADD COLUMN IF NOT EXISTS mes_production_synced_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS mes_production_message TEXT;

UPDATE order_delivery_ticket_allocation alloc
SET ticket_qty = ticket.qty
FROM order_delivery_ticket ticket
WHERE alloc.ticket_id = ticket.id
  AND alloc.ticket_qty IS NULL;

UPDATE order_delivery_ticket_allocation
SET mes_production_status = COALESCE(NULLIF(TRIM(mes_production_status), ''), 'PENDING');

CREATE INDEX IF NOT EXISTS idx_delivery_allocation_mes_production_status
    ON order_delivery_ticket_allocation(mes_production_status, allocation_type, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_allocation_mes_production_request
    ON order_delivery_ticket_allocation(mes_production_request_id)
    WHERE mes_production_request_id IS NOT NULL AND TRIM(mes_production_request_id) <> '';
```

### 6.3 字段说明

| 字段 | 说明 |
| --- | --- |
| `ticket_qty` | 原始提单数量快照。 |
| `mes_production_status` | MES 生产下发状态：`PENDING`、`PROCESSING`、`SUCCESS`、`FAILED`。 |
| `mes_production_request_id` | QPO 生成的本次生产下发唯一请求 ID。 |
| `mes_production_external_id` | MES 返回的生产任务号、工单号或流水号。 |
| `mes_production_synced_at` | 下发成功或失败的最终写回时间。 |
| `mes_production_message` | 下发状态摘要，不保存完整请求体、token 或大 JSON。 |

### 6.4 实体和 DTO

修改：

- `OrderDeliveryTicketAllocation`
- `LockedItemDTO`
- `OrderDeliveryTicketAllocationMapper`

补充字段：

```java
private Integer ticketQty;
private String mesProductionStatus;
private String mesProductionRequestId;
private String mesProductionExternalId;
private LocalDateTime mesProductionSyncedAt;
private String mesProductionMessage;
```

## 7. MES 生产下发接口契约

### 7.1 配置

新增配置项：

```env
ERP_MES_PRODUCTION_PATH=/api/mes/production/submit
```

复用现有：

```env
ERP_MES_BASE_URL=
ERP_MES_AUTH_TOKEN=
ERP_MES_TIMEOUT_MS=60000
```

说明：

- 物料号获取继续使用 `ERP_MES_MATERIAL_PATH`。
- 待生产下发使用新的 `ERP_MES_PRODUCTION_PATH`。
- 两者使用同一个 MES baseUrl、authToken 和 timeout。

### 7.2 QPO 请求 MES

HTTP：

```text
POST {ERP_MES_BASE_URL}{ERP_MES_PRODUCTION_PATH}
Content-Type: application/json
Authorization: Bearer {ERP_MES_AUTH_TOKEN}
```

请求体示例：

```json
{
  "requestId": "QPO-MP-20260802-000001",
  "allocationId": 10001,
  "ticketId": 2086,
  "sourceType": "SALES_ORDER",
  "sourceOrderId": 3001,
  "sourceItemId": 9001,
  "sourceOrderNo": "SO-20260802-0001",
  "customerName": "小米",
  "partNo": "L2010000718",
  "version": "A",
  "materialNo": "MAT-001",
  "itemType": "组合件",
  "allocationType": "PRODUCTION_REQUIRED",
  "productionQty": 4,
  "ticketQty": 10,
  "orderType": "SO01",
  "internalProjectNo": "2",
  "customerOrderNo": "22",
  "customerProjectNo": "222",
  "materialName": "6061",
  "engineeringMaterialName": "铝合金6061",
  "materialShape": "板材",
  "surfaceTreatment": "阳极氧化",
  "description": "材料描述",
  "remark": "备注",
  "deliveryDate": "2026-08-15",
  "processRoute": [
    { "processName": "CNC", "inputVal": "2h" }
  ],
  "materialData": {
    "shape": "板材"
  },
  "materialGrossWeight": 1.25,
  "partNetWeight": 0.98,
  "assemblyRelations": [
    {
      "parentItemId": 9001,
      "childItemId": 9002,
      "childPartNo": "L2010000718-01",
      "childVersion": "A",
      "qtyPerParent": 2,
      "sortNo": 1,
      "sourceParentItemId": 5101,
      "sourceChildItemId": 5102
    }
  ]
}
```

### 7.3 请求字段来源

| 请求字段 | 来源 |
| --- | --- |
| `requestId` | QPO 生成，写入 `mes_production_request_id`。 |
| `allocationId` | `order_delivery_ticket_allocation.id`。 |
| `ticketId` | `order_delivery_ticket_allocation.ticket_id`。 |
| `sourceType` | `order_delivery_ticket_allocation.source_type`。 |
| `sourceOrderId` | `order_delivery_ticket_allocation.source_order_id`。 |
| `sourceItemId` | `order_delivery_ticket_allocation.source_item_id`。 |
| `sourceOrderNo` | `order_delivery_ticket_allocation.rfq_no`，前端显示为来源订单号。 |
| `customerName` | `order_delivery_ticket_allocation.customer_name`。 |
| `partNo` | `order_delivery_ticket_allocation.part_no`。 |
| `version` | `order_delivery_ticket_allocation.version`。 |
| `materialNo` | `order_delivery_ticket_allocation.material_no`。 |
| `itemType` | `order_delivery_ticket_allocation.item_type`。 |
| `allocationType` | 必须为 `PRODUCTION_REQUIRED`。 |
| `productionQty` | `order_delivery_ticket_allocation.qty`。 |
| `ticketQty` | `order_delivery_ticket_allocation.ticket_qty`。 |
| `orderType` | `order_delivery_ticket_allocation.order_type`。 |
| `internalProjectNo` | `order_delivery_ticket_allocation.internal_project_no`。 |
| `customerOrderNo` | `order_delivery_ticket_allocation.customer_order_no`。 |
| `customerProjectNo` | `order_delivery_ticket_allocation.customer_project_no`。 |
| `materialName` | `order_delivery_ticket_allocation.material_name`。 |
| `engineeringMaterialName` | `order_delivery_ticket_allocation.engineering_material_name`。 |
| `materialShape` | `order_delivery_ticket_allocation.material_shape`，材料形态显式快照，通常为 `板材 / 棒材 / 管材`，来源于核价材料快照 `materialData.shape`。 |
| `surfaceTreatment` | `order_delivery_ticket_allocation.surface_treatment`。 |
| `description` | `order_delivery_ticket_allocation.description`。 |
| `remark` | `order_delivery_ticket_allocation.remark`。 |
| `deliveryDate` | `order_delivery_ticket_allocation.delivery_date`。 |
| `processRoute` | `order_delivery_ticket_allocation.process_route`。 |
| `materialData` | `order_delivery_ticket_allocation.material_data`。 |
| `materialGrossWeight` | `order_delivery_ticket_allocation.material_gross_weight`。 |
| `partNetWeight` | `order_delivery_ticket_allocation.part_net_weight`。 |
| `assemblyRelations` | 按 `source_type + source_item_id` 统一查询组装。 |

### 7.4 MES 响应

成功：

```json
{
  "success": true,
  "requestId": "QPO-MP-20260802-000001",
  "externalId": "MES-PROD-0001",
  "message": "已接收待生产任务"
}
```

失败：

```json
{
  "success": false,
  "requestId": "QPO-MP-20260802-000001",
  "message": "工艺路线缺失"
}
```

QPO 处理：

- `success=true`：写 `mes_production_status=SUCCESS`。
- `success=false`：写 `mes_production_status=FAILED`。
- HTTP 非 2xx、超时、响应体解析失败：写 `FAILED`。
- 不保存完整请求体和完整响应体，只保存摘要。

## 8. 后端设计

### 8.1 权限

新增权限：

| 权限编码 | 类型 | 父级 | 中文 |
| --- | --- | --- | --- |
| `market:production-item:mes-production-submit` | `BUTTON/API` | `market:locked-item:view` | 待产零件下发MES生产 |

默认角色建议：

| 角色 | 默认授权 |
| --- | --- |
| `SUPER_ADMIN` | 是 |
| `MARKET_MANAGER` | 是 |
| `MARKET_USER` | 是 |
| `READONLY` | 否 |

### 8.2 Controller

扩展 `OrderDeliveryController`：

```java
@PostMapping("/production-items/mes-production")
@RequirePermission("market:production-item:mes-production-submit")
public SystemTask submitMesProduction(@RequestBody LockedItemMesProductionBatchReq req) {
    return systemTaskService.submitMesProductionBatchTask(req == null ? null : req.getAllocationIds());
}
```

请求：

```java
public class LockedItemMesProductionBatchReq {
    private List<Integer> allocationIds;
}
```

### 8.3 Service

新增：

- `MesProductionDispatchService`
- `MesProductionDispatchClient`

扩展：

- `SystemTaskService`
- `LockedItemService`
- `OrderDeliveryTicketAllocationMapper`

任务类型：

```text
MES_PRODUCTION_DISPATCH
```

核心校验：

- allocation ID 不能为空。
- allocation 必须存在。
- 当前用户有数据权限。
- `allocation_type` 必须为 `PRODUCTION_REQUIRED`。
- `qty` 必须大于 0。
- `ticket_qty` 必须大于 0。
- `material_no` 必须非空。
- `mes_production_status=PROCESSING` 时拒绝重复提交。
- `mes_production_status=SUCCESS` 时拒绝重复创建 MES 生产任务。
- `FAILED` 允许重试。

### 8.4 批量处理口径

批量任务内部逐行处理：

```text
用户选择 N 行
  -> 创建一个 system_task
  -> 按 allocationId 顺序处理
  -> 非待生产行跳过
  -> 已成功行跳过
  -> 处理中行跳过
  -> 失败行允许重试
  -> 汇总 successCount / failedCount / skippedCount
```

任务结果摘要：

```json
{
  "totalCount": 5,
  "successCount": 3,
  "failedCount": 1,
  "skippedCount": 1,
  "rows": [
    {
      "allocationId": 10001,
      "status": "SUCCESS",
      "message": "已下发 MES 生产"
    }
  ]
}
```

### 8.5 组合件关系查询

统一 DTO：

```java
public class MesProductionAssemblyRelationDTO {
    private Long parentItemId;
    private Long childItemId;
    private String parentPartNo;
    private String parentVersion;
    private String childPartNo;
    private String childVersion;
    private BigDecimal qtyPerParent;
    private Integer sortNo;
    private Integer sourceParentItemId;
    private Integer sourceChildItemId;
}
```

RFQ 查询：

```text
item_assembly_relation.parent_item_id = allocation.source_item_id
LEFT JOIN item_quote_version parent ON parent.id = relation.parent_item_id
LEFT JOIN item_quote_version child ON child.id = relation.child_item_id
```

销售订单查询：

```text
sales_order_item_assembly_relation.parent_item_id = allocation.source_item_id
LEFT JOIN sales_order_item parent ON parent.id = relation.parent_item_id
LEFT JOIN sales_order_item child ON child.id = relation.child_item_id
```

返回规则：

- 查不到关系返回 `[]`。
- 普通件返回 `[]`。
- 查询异常才标记本行下发失败。

## 9. 前端设计

### 9.1 锁库零件筛选区优化

参考已提零件模块，改为三块：

```text
主筛选区
  客户 / 图号 / 版本 / 分配类型 / 生产下发状态 / 搜索 / 重置 / 高级筛选

批量操作区
  下发MES生产

高级筛选区（默认收起）
  来源订单号 / 物料号 / 内部项目号 / 客户订单号 / 客户项目号 / 交货日期
```

说明：

- `来源订单号` 继续绑定现有 `rfqNo` 参数，兼容 RFQ 和销售订单显示。
- `生产下发状态` 绑定 `mesProductionStatus`。
- 批量操作按钮独立成组，右对齐或独立分隔。
- 小屏下自然换行，按钮不挤压输入框。

### 9.2 表格调整

新增列：

| 列 | 说明 |
| --- | --- |
| 原提单数量 | `ticketQty` |
| MES生产状态 | `mesProductionStatus` |
| MES生产任务号 | `mesProductionExternalId`，可放高级列或详情抽屉 |

保留列：

- 分配ID
- 提单ID
- 图号
- 版本
- 物料号
- 零件类型
- 分配类型
- 在产订单号
- 订单类型
- 数量
- 客户
- 来源订单号
- 内部项目号
- 客户订单号
- 客户项目号
- 材料
- 材料类型
- 毛重
- 净重
- 工艺路线
- 交货日期
- 材料描述
- 表面处理
- 备注

### 9.3 按钮交互

顶部按钮：

```text
下发MES生产
```

权限：

```vue
v-permission="'market:production-item:mes-production-submit'"
```

启用规则：

- 至少选中一行。
- 前端可提示“只会下发待生产数量行，其他分配类型会被跳过”。
- 后端仍最终校验。

确认文案：

```text
确认将选中的待生产锁库零件下发给 MES 生产流程？非待生产行会自动跳过。
```

成功提示：

```text
MES生产下发任务已提交，任务ID：{taskId}
```

批量结果展示：

- 成功数量
- 失败数量
- 跳过数量
- 失败 / 跳过明细

## 10. 日志与审计

### 10.1 操作日志

新增动作：

| 动作 | 模块 | targetType | targetId |
| --- | --- | --- | --- |
| `PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT` | `DELIVERY` | `order_delivery_ticket_allocation` | 批量任务可用 taskId 或首个 allocationId |
| `PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS` | `DELIVERY` | `order_delivery_ticket_allocation` | allocationId |
| `PRODUCTION_ITEM_MES_PRODUCTION_FAILED` | `DELIVERY` | `order_delivery_ticket_allocation` | allocationId |

### 10.2 文件日志

低噪声日志：

```text
MES production dispatch submitted taskId={} allocationCount={}
MES production dispatch started allocationId={} requestId={} sourceType={} sourceOrderId={} sourceItemId={} productionQty={} ticketQty={}
MES production dispatch completed allocationId={} requestId={} externalId={} durationMs={}
MES production dispatch skipped allocationId={} reason={}
MES production dispatch failed allocationId={} requestId={} durationMs={}
```

要求：

- 不记录 token。
- 不记录完整请求体。
- 不记录完整响应体。
- 错误日志必须带异常对象。

## 11. 测试方案

### 11.1 后端测试

新增或扩展：

- `LockedItemMesProductionDispatchTest`
- `LockedItemMesProductionClientTest`
- `LockedItemQueryTest`
- `PermissionModuleTreeTest`
- `AuditActionCoverageTests`

覆盖：

- migration 包含 `ticket_qty` 和 MES 生产状态字段。
- WMS 写 allocation 时保存 `ticket_qty = ticket.qty`。
- 历史 allocation 可回填 `ticket_qty`。
- 只有 `PRODUCTION_REQUIRED` 可下发 MES。
- `IN_STOCK_LOCK` 和 `IN_PRODUCTION_LOCK` 批量时被跳过。
- `SUCCESS` 状态禁止重复创建 MES 生产任务。
- `FAILED` 状态允许重试。
- 普通件 `assemblyRelations=[]`。
- RFQ 组合件查 `item_assembly_relation` 返回父子关系。
- 销售订单组合件查 `sales_order_item_assembly_relation` 返回父子关系。
- 组合件无关系返回空数组且不阻断。
- 数据权限生效。
- 日志不包含 token 或完整请求体。

### 11.2 前端测试

新增或扩展：

- `lockedItemManage.test.js`
- `deliveredItemApi.test.js`
- `permissionStage4.test.js`

覆盖：

- 主筛选区只展示客户、图号、版本、分配类型、生产下发状态、搜索、重置、高级筛选。
- 高级筛选默认收起，包含来源订单号、物料号、内部项目号、客户订单号、客户项目号、交货日期。
- 批量操作区展示 `下发MES生产`。
- 无 `market:production-item:mes-production-submit` 权限时按钮隐藏。
- 选中行后按钮可用。
- 批量提交调用 `/api/order-delivery/production-items/mes-production`。
- 表格展示原提单数量和 MES 生产状态。
- 不本地全量过滤。

## 12. 分阶段实施

### 阶段 1：数据模型与权限骨架

目标：锁库结果行能保存原始提单数量和 MES 生产下发状态。

修改：

- 新增迁移 `Vxx__locked_item_mes_production_dispatch.sql`。
- `order_delivery_ticket_allocation` 新增 `ticket_qty`。
- `order_delivery_ticket_allocation` 新增 MES 生产下发状态字段。
- 回填历史 `ticket_qty`。
- 更新 `OrderDeliveryTicketAllocation`、`LockedItemDTO`、Mapper。
- 新增权限 `market:production-item:mes-production-submit`。
- 更新 `PermissionService.defaultPermissions()`。
- 更新权限清单。

验收：

- 新 allocation 写入时保存 `ticket_qty`。
- 历史 allocation 可回填 `ticket_qty`。
- 页面查询 DTO 能返回 `ticketQty` 和生产状态。
- 无权限用户不能调用生产下发接口。

### 阶段 2：MES 生产下发后端接口

目标：后端可按待生产 allocation 行组装请求并调用 MES。

修改：

- 新增 `MesProductionDispatchClient`。
- 新增 `MesProductionDispatchService`。
- 新增请求 / 响应 DTO。
- 新增批量请求 DTO `LockedItemMesProductionBatchReq`。
- 扩展 `SystemTaskService`，新增 `MES_PRODUCTION_DISPATCH`。
- 扩展 `OrderDeliveryController`，新增 `/api/order-delivery/production-items/mes-production`。
- 组包时带出所有锁库表展示字段和 `ticketQty`。

验收：

- 只有 `PRODUCTION_REQUIRED` 行会实际调用 MES。
- 非待生产行批量时跳过。
- 成功回写 `mes_production_status=SUCCESS`。
- 失败回写 `mes_production_status=FAILED`。
- 成功后禁止重复创建 MES 生产任务。

### 阶段 3：组合件父子关系组包

目标：MES 生产下发请求包含统一父子关系数组。

修改：

- RFQ 来源查询 `item_assembly_relation`。
- 销售订单来源查询 `sales_order_item_assembly_relation`。
- 普通件或无关系时返回空数组。
- 将 `assemblyRelations` 放入 MES 请求体。

验收：

- RFQ 组合件能返回父子关系。
- 销售订单组合件能返回父子关系。
- 普通件返回空数组。
- 组合件无关系返回空数组且继续下发。

### 阶段 4：锁库零件前端筛选区优化

目标：锁库零件页面像已提零件模块一样呈现工作台式筛选和操作区。

修改：

- 改造 `LockedItemManage.vue` 顶部筛选区布局。
- 主筛选保留客户、图号、版本、分配类型、生产下发状态、搜索、重置、高级筛选。
- 批量下发 MES 生产按钮移动到独立操作区。
- 高级筛选默认收起。
- 高级筛选展示来源订单号、物料号、内部项目号、客户订单号、客户项目号、交货日期。
- 小屏自然换行。
- 补前端源码测试。

验收：

- 页面顶部不再是一整排拥挤输入框。
- 主筛选区一眼能看到图号、版本、分配类型和生产状态。
- 批量下发按钮与查询按钮分组。
- 无权限时下发按钮隐藏。

### 阶段 5：前端下发交互和结果展示

目标：用户可手动选择待生产行下发 MES。

修改：

- `deliveredItemApi.js` 增加 MES 生产下发方法。
- `LockedItemManage.vue` 支持多选。
- 增加顶部 `下发MES生产` 按钮。
- 增加确认弹窗。
- 展示批量任务结果摘要。
- 下发完成后刷新列表。

验收：

- 单选一条待生产行可以提交。
- 批量选择多条待生产行可以提交。
- 批量中非待生产行被跳过并展示数量。
- 提交后页面状态刷新。

### 阶段 6：文档与回归

目标：联调口径、运维口径、权限口径和测试完整收口。

修改：

- 更新 `docs/QPO-MES-WMS对接/03-MES物料号同步开发文档.md` 中 MES 配置说明，补充生产下发 path。
- 新增或更新 `docs/QPO-MES-WMS对接/09-提供给MES的待生产下发接口对接说明.md`。
- 更新 `docs/QPO-MES-WMS对接/04-WMS锁库分配与锁库零件模块开发文档.md`，说明 `ticket_qty`。
- 更新 `docs/QPO权限用户体系/02-权限点清单与现有角色映射.md`。
- 更新 `docs/QPO权限用户体系/03-权限体系部署与管理员使用说明.md`。
- 更新 `docs/QPO日志模块/03-日志运维说明.md`。
- 更新 `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md`。
- 补前后端测试。

验收：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

## 13. 风险与处理

### 13.1 组合件关系缺失

风险：

- 历史 RFQ 或销售订单没有建立父子关系。

处理：

- 按本次业务口径返回 `assemblyRelations=[]`。
- 不提示、不阻断。
- 后续如 MES 强制要求组合件必须有关系，再单独增加阻断规则。

### 13.2 重复下发

风险：

- 用户重复点击或失败重试导致 MES 重复建生产任务。

处理：

- QPO 生成 `mes_production_request_id`。
- `PROCESSING` 和 `SUCCESS` 状态不允许重复提交。
- `FAILED` 允许重试，新请求生成新的 requestId。
- MES 侧建议按 requestId 幂等。

### 13.3 字段过多导致日志泄露

风险：

- MES 请求字段较多，如果直接打印完整请求体，会污染日志且可能包含业务敏感信息。

处理：

- 文件日志只打印 allocationId、requestId、sourceType、sourceOrderId、sourceItemId、productionQty、ticketQty。
- 操作日志只写摘要。
- 不记录 token 和完整请求体。

### 13.4 前端筛选与后端参数不一致

风险：

- 前端把筛选区重排后，部分旧筛选字段丢失。

处理：

- 高级筛选保留旧追溯字段。
- 源码测试冻结主筛选和高级筛选字段。
- 后端查询参数继续兼容旧字段。

## 14. 完成定义

- `order_delivery_ticket_allocation` 保存原始提单数量 `ticket_qty`。
- 锁库零件页面能展示原提单数量和 MES 生产下发状态。
- 用户可手动选择待生产行下发 MES 生产流程。
- 非待生产行不会被实际下发 MES。
- RFQ 和销售订单组合件都能带出父子关系；普通件或无关系返回空数组。
- 锁库零件筛选区完成主筛选 / 高级筛选 / 批量操作区分层。
- 权限、日志、文档、前后端测试完整覆盖。
- `mvnw.cmd test`、`npm test`、`npm run build` 通过。
