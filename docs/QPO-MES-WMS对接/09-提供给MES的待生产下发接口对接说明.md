# 提供给 MES 的待生产下发接口对接说明

## 1. 适用范围

本文档描述 QPO 锁库零件模块向 MES 下发待生产零件的接口。该接口只处理 WMS 锁库后仍需生产的 allocation 行，不替代 MES 物料号获取接口，也不处理库存锁定结果回传。

- 用户入口：锁库零件页面手动选择一条或多条待生产行，点击“下发MES生产”。
- QPO 后端触发接口：`POST /api/order-delivery/allocations/mes-production`。
- MES 接收接口：`POST {ERP_MES_BASE_URL}{ERP_MES_PRODUCTION_PATH}`。
- 只下发 `PRODUCTION_REQUIRED`，即 `allocationType = PRODUCTION_REQUIRED` 的锁库 allocation 行。
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
ERP_MES_TIMEOUT_MS=10000
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
  "engineeringMaterialName": "AL6061",
  "materialShape": "板材",
  "surfaceTreatment": "阳极氧化",
  "description": "生产备注",
  "remark": "QPO锁库待生产下发",
  "deliveryDate": "2026-08-20",
  "processRoute": "CNC; 清洗; 检验",
  "materialData": "材料快照摘要",
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
| `materialName` | 否 | 业务材料名称。 |
| `engineeringMaterialName` | 否 | 工程材料名称。 |
| `materialShape` | 否 | 材料形态，来自显式快照字段；通常为 `板材 / 棒材 / 管材`，来源于核价材料快照里的 `materialData.shape`。 |
| `surfaceTreatment` | 否 | 表面处理。 |
| `description` | 否 | 说明。 |
| `remark` | 否 | 备注。 |
| `deliveryDate` | 否 | 交货日期。 |
| `processRoute` | 否 | 工艺路线快照。 |
| `materialData` | 否 | 材料数据快照。 |
| `materialGrossWeight` | 否 | 材料毛重。 |
| `partNetWeight` | 否 | 零件净重。 |
| `assemblyRelations` | 是 | 组合件父子关系数组；普通件或无父子关系时传空数组。 |

数量口径：

```text
已入库库存锁定数量 + 在产库存锁定数量 + productionQty = ticketQty
```

其中 `productionQty` 只表示本次需要 MES 生产的剩余数量，`ticketQty` 表示锁库前的原始提单数量。

## 5. 组合件父子关系

- RFQ 来源从 `item_assembly_relation` 查询。
- 销售订单来源从 `sales_order_item_assembly_relation` 查询。
- 普通件返回 `assemblyRelations: []`。
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

- MES 生产下发使用权限 `market:locked-item:mes-production-submit` 控制页面按钮和后端接口。
- 操作日志动作：`LOCKED_ITEM_MES_PRODUCTION_SUBMIT`、`LOCKED_ITEM_MES_PRODUCTION_SUCCESS`、`LOCKED_ITEM_MES_PRODUCTION_FAILED`。
- 文件日志事件名使用 `MES production dispatch submitted/started/completed/skipped/failed`。
- 不得记录 token、Authorization header。
- 不得记录完整请求体或完整响应体。
- 日志只记录 `allocationId`、`ticketId`、`requestId`、`sourceType`、`sourceOrderId`、`sourceItemId`、`productionQty`、`ticketQty`、`externalId`、`durationMs` 等摘要字段。
