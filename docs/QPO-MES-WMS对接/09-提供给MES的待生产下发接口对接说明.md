# 提供给数据中心的待生产下发接口对接说明
> MES 真实批量阶段 7 收口：正式和测试部署统一使用数据中心 endpoint，物料批量为 `MES_MATERIAL_RESOLVE` -> `/api/v1/mes/openapi/material-preparation/create`，生产需求批量为 `MES_PRODUCTION_DISPATCH` -> `/api/v1/mes/openapi/demand/create`，最低超时 `60000ms`。出站链路固定为 `QPO -> 数据中心 -> MES`；物料完成回调固定为 `MES -> 数据中心 -> QPO` 的 `POST /api/integrations/mes/material-callback`。权限不新增，继续复用 `market:delivered-item:mes-sync` 和 `market:production-item:mes-production-submit`；批次核对动作标签保留 `MES_INTEGRATION_BATCH_RECONCILE`。数据中心必须透传 QPO 的 4xx/429/5xx 语义，任何操作日志和文件日志都不得记录完整请求、完整响应、完整回调内容、Authorization、Cookie、任一段 Token 或凭据。

> 数据中心统一对接阶段 8 收口：本接口说明交付对象为数据中心。QPO 生产下发底层调用使用 `MES_PRODUCTION_DISPATCH` endpoint，链路为 `QPO -> 数据中心 -> MES`，数据中心负责转发到 MES 生产接口；MES 具体接收 path 由数据中心和 MES 团队在数据中心侧维护。

> 2026-08-08 两级工艺主数据阶段 15 收口：本接口只发送结构化工艺路线 `processCode / categoryName / detailName / unitWorkHours / sortNo`。`hourlyRate / unitRate / lineCost / cost` 等 QPO 内部核价成本字段只保留在 QPO 快照和内部展示中，不进入 MES 生产下发请求。

> 2026-08-09 材料主数据阶段 10 收口：本接口只发送工程材料身份摘要字段 `engineeringMaterialCode / engineeringMaterialTypeCode / engineeringMaterialTypeName / engineeringMaterialCategory / engineeringMaterialName / materialShape`。材料编码和材料类型用于 MES 生产侧识别或追溯材料摘要，材质分类与材料形态分开；完整 `materialData`、完整材料价格矩阵和 QPO 内部材料成本不进入 MES 生产下发请求。

> 2026-08-13 MES 主数据同步阶段 9 收口：MES 工艺/材料主数据同步后，QPO 生产下发 DTO 字段名不变，payload 仍满足 `processRoute[].processTypeCode = QPO processCode = MES code`、`processRoute[].processName = QPO detailName`、`bom[].itemCode = QPO engineeringMaterialCode = MES material code`、`bom[].quantity = QPO materialGrossWeight kg`。同步只保证字典和报价快照使用 MES 编码体系，不把 QPO 单价、完整材料价格矩阵、完整请求/响应或 token 写入 MES 下发 payload。

## 1. 适用范围

本文档描述 QPO 待产零件模块通过数据中心向 MES 下发待生产零件的接口。该接口只处理 WMS 锁库后仍需生产的待产 allocation 行，不替代 MES 物料号获取接口，也不处理库存锁定结果回传。

- 用户入口：待产零件页面手动选择一条或多条待生产行，点击“下发MES生产”。
- QPO 后端触发接口：`POST /api/order-delivery/production-items/mes-production`。
- 数据中心功能接口：`MES_PRODUCTION_DISPATCH`，由数据中心转发到 MES 生产接口。
- 只下发 `PRODUCTION_REQUIRED`，即 `allocationType = PRODUCTION_REQUIRED` 的待产 allocation 行。
- 普通件或无父子关系时传空数组，组合件存在关系时带 `assemblyRelations`。

## 2. 调用方式

```text
POST {ERP_MES_BASE_URL}{ERP_MES_PRODUCTION_PATH}
Content-Type: application/json
Authorization: Bearer {ERP_MES_AUTH_TOKEN}
```

当 `ERP_MES_AUTH_TOKEN` 为空时，QPO 不发送 Authorization header。

默认配置建议：

```env
ERP_MES_BASE_URL=http://mes.example.local
ERP_MES_AUTH_TOKEN=
ERP_MES_PRODUCTION_PATH=/api/mes/production/submit
ERP_MES_TIMEOUT_MS=60000
```

`ERP_MES_PRODUCTION_PATH` 与物料号获取的 `ERP_MES_MATERIAL_PATH` 独立配置，便于 MES 分别维护物料号生成接口和生产流程下发接口。

## 3. 请求体

```json
{
  "requestId": "QPO-PROD-20260803-000001",
  "allocationId": 101,
  "ticketId": 88,
  "sourceType": "SALES_ORDER",
  "sourceOrderId": 12,
  "sourceItemId": 230,
  "sourceOrderNo": "SO-20260803-001",
  "customerName": "示例客户",
  "partNo": "P-1001",
  "version": "A",
  "materialNo": "MAT-000123",
  "itemType": "组合件",
  "allocationType": "PRODUCTION_REQUIRED",
  "productionQty": 4,
  "ticketQty": 10,
  "orderType": "SO01",
  "internalProjectNo": "IP-20260803-001",
  "customerOrderNo": "CO-001",
  "customerProjectNo": "CP-001",
  "materialName": "铝板",
  "engineeringMaterialCode": "MAT-AL6061",
  "engineeringMaterialName": "AL6061",
  "engineeringMaterialTypeCode": "RAW_MATERIAL",
  "engineeringMaterialTypeName": "原材料",
  "engineeringMaterialCategory": "铝",
  "materialShape": "板材",
  "surfaceTreatment": "阳极氧化",
  "description": "生产备注",
  "remark": "QPO锁库待生产下发",
  "deliveryDate": "2026-08-20",
  "processRoute": [
    {
      "processCode": "PACKAGING",
      "categoryName": "包装",
      "detailName": "精细包装",
      "unitWorkHours": 1.5,
      "sortNo": 10
    }
  ],
  "materialGrossWeight": 1.25,
  "partNetWeight": 0.98,
  "assemblyRelations": [
    {
      "parentItemId": 230,
      "childItemId": 231,
      "parentPartNo": "ASM-100",
      "parentVersion": "A",
      "childPartNo": "P-1001-01",
      "childVersion": "A",
      "qtyPerSet": 2,
      "sortOrder": 1,
      "historySourceItemId": 998
    }
  ]
}
```

## 4. 字段说明

### 4.1 MES 主数据同步后的字段合同

| MES 下发字段 | QPO 来源 | 合同口径 |
| --- | --- | --- |
| `processRoute[].processTypeCode` | `processCode` | `processRoute[].processTypeCode = QPO processCode = MES code`。MES 工艺类型同步时把 MES `code` 写入 QPO 工艺大类 `processCode`。 |
| `processRoute[].processName` | `detailName` | `processRoute[].processName = QPO detailName`。QPO 默认具体工艺由 MES 工艺名称生成，用户也可在该 MES 大类下维护更细的具体工艺。 |
| `bom[].itemCode` | `engineeringMaterialCode` | `bom[].itemCode = QPO engineeringMaterialCode = MES material code`。普通件报价完成时从材料字典快照材料编码；组合件父件本身没有原材料编码时传 `bom: []`，仍通过 `assemblyRelations` 下发父子关系。 |
| `bom[].quantity` | `materialGrossWeight` | `bom[].quantity = QPO materialGrossWeight kg`。这里是材料毛重 kg，不是件数；组合件父件 `bom: []` 时不要求父件材料毛重。 |

真实数据中心读取 MES 工艺/材料可以在普通回归中手动执行同步；真实写 MES 仍使用独立显式开关，不在普通回归中自动执行。

### 4.2 基础字段

| 字段 | 必填 | 来源 / 含义 |
| --- | --- | --- |
| `requestId` | 是 | QPO 生成的本次生产下发唯一请求号，用于日志和幂等排查。 |
| `allocationId` | 是 | `order_delivery_ticket_allocation.id`。 |
| `ticketId` | 是 | `order_delivery_ticket.id`，原始提单行 ID。 |
| `sourceType` | 是 | `RFQ` 或 `SALES_ORDER`。 |
| `sourceOrderId` | 是 | 来源订单主表 ID。 |
| `sourceItemId` | 是 | 来源零件主表 ID；RFQ 对应 `item_quote_version.id`，销售订单对应 `sales_order_item.id`。 |
| `sourceOrderNo` | 是 | RFQ 号或销售订单号。 |
| `customerName` | 是 | 来源订单客户名称。 |
| `partNo` | 是 | 图号。 |
| `version` | 否 | 版本，空版本传空字符串。 |
| `materialNo` | 是 | 已确认物料号。 |
| `itemType` | 否 | 普通件 / 组合件等零件类型。 |
| `allocationType` | 是 | 只允许 `PRODUCTION_REQUIRED`。 |
| `productionQty` | 是 | 本次待生产数量，即 allocation 当前 `qty`。 |
| `ticketQty` | 是 | 原始提单数量快照，即 `order_delivery_ticket_allocation.ticket_qty`。 |
| `orderType` | 否 | 来源订单类型 / 单据类型编码。 |
| `internalProjectNo` | 否 | 内部项目号。 |
| `customerOrderNo` | 否 | 客户订单号。 |
| `customerProjectNo` | 否 | 客户项目号。 |
| `materialName` | 否 | 生产侧有效材料名称。QPO 按 `engineeringMaterialName -> materialData.name -> 导入材料 material_name` 降级得到，用于 MES 生产侧识别材料。 |
| `engineeringMaterialCode` | 否 | 工程材料编码，来自 `order_delivery_ticket_allocation.engineering_material_code` 快照。 |
| `engineeringMaterialName` | 否 | 真实材料 / 核价材料名称，来源于 RFQ 核价或销售匹配补全的 `engineering_material_name`；为空时可结合 `materialData.name` 理解。 |
| `engineeringMaterialTypeCode` | 否 | 工程材料类型编码，来自材料类型字典快照，例如 `RAW_MATERIAL`。 |
| `engineeringMaterialTypeName` | 否 | 工程材料类型名称，来自材料类型字典快照，例如 `原材料`。 |
| `engineeringMaterialCategory` | 否 | 材质分类，保存钢铁、铝、塑料等分类快照。 |
| `materialShape` | 否 | 材料形态，来自显式快照字段；通常为 `板材 / 棒材 / 管材`，来源于核价材料快照里的 `materialData.shape`。 |
| `surfaceTreatment` | 否 | 表面处理。 |
| `description` | 否 | 说明。 |
| `remark` | 否 | 备注。 |
| `deliveryDate` | 否 | 交货日期。 |
| `processRoute` | 否 | 结构化工艺路线数组。每行只传 `processCode`、`categoryName`、`detailName`、`unitWorkHours`、`sortNo`；不传 QPO 内部工艺成本字段，如 `hourlyRate`、`unitRate`、`lineCost`、`cost`。 |
| `materialGrossWeight` | 否 | 材料毛重。 |
| `partNetWeight` | 否 | 零件净重。 |
| `assemblyRelations` | 是 | 组合件父子关系数组；普通件或无父子关系时传空数组。 |

数量口径：

```text
已入库库存锁定数量 + 在产库存锁定数量 + productionQty = ticketQty
```

其中 `productionQty` 只表示本次需要 MES 生产的剩余数量，`ticketQty` 表示锁库前的原始提单数量。

## 5. BOM 与组合件父子关系

- RFQ 来源从 `item_assembly_relation` 查询。
- 销售订单来源从 `sales_order_item_assembly_relation` 查询。
- 普通件返回 `assemblyRelations: []`。
- 普通件或组合件缺少 BOM 来源字段时返回 `bom: []`，不因缺少 `engineeringMaterialCode` 或毛重阻断下发。
- 组合件无关系、关系未补全或关系表无记录时也返回 `assemblyRelations: []`，不阻断下发。
- QPO 不在本接口里补建父子关系；父子关系由前置 RFQ 核价 / 销售匹配流程维护。

## 6. MES 响应体

成功：

```json
{
  "success": true,
  "requestId": "QPO-PROD-20260803-000001",
  "externalId": "MES-PROD-00001",
  "message": "accepted"
}
```

失败：

```json
{
  "success": false,
  "requestId": "QPO-PROD-20260803-000001",
  "message": "materialNo is invalid"
}
```

QPO 处理规则：

- `success=true` 判定下发成功，回写 `mes_production_status = SUCCESS`。
- `success=false` 或 HTTP 非 2xx 判定下发失败，回写 `mes_production_status = FAILED` 和失败摘要。
- 已成功下发的 allocation 不允许重复创建 MES 生产任务。
- 批量提交时，非 `PRODUCTION_REQUIRED` 行会被 QPO 跳过，不会调用 MES。

## 7. 安全与日志

- MES 生产下发使用权限 `market:production-item:mes-production-submit` 控制页面按钮和后端接口。
- 操作日志动作：`PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT`、`PRODUCTION_ITEM_MES_PRODUCTION_SUCCESS`、`PRODUCTION_ITEM_MES_PRODUCTION_FAILED`。
- 文件日志事件名使用 `MES production dispatch submitted/started/completed/skipped/failed`。
- 不得记录 token、Authorization header。
- 不得记录完整请求体或完整响应体。
- 日志只记录 `allocationId`、`ticketId`、`requestId`、`sourceType`、`sourceOrderId`、`sourceItemId`、`productionQty`、`ticketQty`、`externalId`、`durationMs` 等摘要字段。
