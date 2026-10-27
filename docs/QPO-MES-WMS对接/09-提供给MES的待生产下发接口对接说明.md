# 提供给数据中心的待生产下发接口对接说明
> MES 真实批量阶段 7 收口：正式和测试部署统一使用数据中心 endpoint，物料批量为 `MES_MATERIAL_RESOLVE` -> `/api/v1/mes/openapi/material-preparation/create`，生产需求批量为 `MES_PRODUCTION_DISPATCH` -> `/api/v1/mes/openapi/demand/create`，最低超时 `60000ms`。出站链路固定为 `QPO -> 数据中心 -> MES`；物料完成回调固定为 `MES -> 数据中心 -> QPO` 的 `POST /api/integrations/mes/material-callback`。权限不新增，继续复用 `market:delivered-item:mes-sync` 和 `market:production-item:mes-production-submit`；批次核对动作标签保留 `MES_INTEGRATION_BATCH_RECONCILE`。数据中心必须透传 QPO 的 4xx/429/5xx 语义，任何操作日志和文件日志都不得记录完整请求、完整响应、完整回调内容、Authorization、Cookie、任一段 Token 或凭据。

> 数据中心统一对接阶段 8 收口：本接口说明交付对象为数据中心。QPO 生产下发底层调用使用 `MES_PRODUCTION_DISPATCH` endpoint，链路为 `QPO -> 数据中心 -> MES`，数据中心负责转发到 MES 生产接口；MES 具体接收 path 由数据中心和 MES 团队在数据中心侧维护。

> 2026-08-08 两级工艺主数据阶段 15 收口：本接口只发送结构化工艺路线 `processCode / categoryName / detailName / unitWorkHours / sortNo`。不传 QPO 内部工艺成本字段，具体包括 `hourlyRate / unitRate / lineCost / cost`；这些字段只保留在 QPO 快照和内部展示中，不进入 MES 生产下发请求。

> 2026-08-09 材料主数据阶段 10 收口：本接口只发送工程材料身份摘要字段 `engineeringMaterialCode / engineeringMaterialTypeCode / engineeringMaterialTypeName / engineeringMaterialCategory / engineeringMaterialName / materialShape`。单独字段 `materialShape` 表示材料形态，来源于 `materialData.shape`，示例为 `板材 / 棒材 / 管材`。材料编码和材料类型用于 MES 生产侧识别或追溯材料摘要，材质分类与材料形态分开；完整 `materialData`、完整材料价格矩阵和 QPO 内部材料成本不进入 MES 生产下发请求。

> 2026-08-13 MES 主数据同步阶段 9 收口：MES 工艺/材料主数据同步后，QPO 生产下发 DTO 字段名不变，payload 仍满足 `processRoute[].processTypeCode = QPO processCode = MES code`、`processRoute[].processName = QPO detailName`、`bom[].itemCode = QPO engineeringMaterialCode = MES material code`、`bom[].quantity = QPO materialGrossWeight kg`。同步只保证字典和报价快照使用 MES 编码体系，不把 QPO 单价、完整材料价格矩阵、完整请求/响应或 token 写入 MES 下发 payload。

## 1. 适用范围

本文档描述 QPO 待产零件模块通过数据中心向 MES 下发待生产零件的接口。该接口只处理 WMS 锁库后仍需生产的待产 allocation 行，不替代 MES 物料号获取接口，也不处理库存锁定结果回传。

- 用户入口：待产零件页面手动选择一条或多条待生产行，点击“下发MES生产”；或由“定时下发策略”配置的每日定时任务在系统级全量下发（见第 8 节）。
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

QPO 向数据中心发送的生产需求请求使用批量外层结构。Allocation、提单和内部关联字段只用于 QPO 内部认领、状态回写和审计，不会序列化到请求体。

```json
{
  "bizId": "QPO-DEM-20261018-A1B2C3D4E5F6",
  "customerCode": "CUST-001",
  "customerName": "示例客户",
  "internalProjectNo": "项目-2026-01",
  "items": [
    {
      "saleOrderNo": "SO-2026-001",
      "saleOrderItemNo": "10",
      "productCode": "MAT-001",
      "partNo": "P-1001",
      "version": "A",
      "materialShape": "板材",
      "manufacturingProcessType": "CNC",
      "demandQty": 5,
      "makeQty": 5,
      "buyQty": 0,
      "stockQty": 0,
      "requiredDate": "2026-11-20T23:59:59",
      "remark": "零件备注",
      "customerRequirements": "材料名称：铝合金\n表面处理：阳极氧化",
      "bom": [
        {
          "itemCode": "ENG-MAT-01",
          "quantity": 2.5
        }
      ],
      "processRoute": [
        {
          "programNum": "1",
          "processName": "数控加工",
          "processTypeCode": "CNC",
          "workHour": 1.5,
          "processDescription": "加工说明"
        }
      ]
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
| `bizId` | 是 | QPO 生成的本次批量生产下发唯一业务号。 |
| `customerCode` | 否 | 销售订单客户编码。 |
| `customerName` | 否 | 销售订单客户名称。 |
| `internalProjectNo` | 否 | 本批次销售订单零件的内部项目号。 |
| `items[].saleOrderNo` | 是 | `sales_order_header.document_no`，为空时使用销售订单号。 |
| `items[].saleOrderItemNo` | 是 | `sales_order_item.line_no`，即销售订单内的系统零件行号，不是数据库主键 `id`。 |
| `items[].productCode` | 是 | 关联销售订单零件行当前物料号。 |
| `items[].partNo` | 否 | 关联销售订单零件行当前图号。 |
| `items[].version` | 否 | 关联销售订单零件行当前版本。 |
| `items[].materialShape` | 否 | 材料形态，来源于材料快照 `materialData.shape`；示例为 `板材 / 棒材 / 管材`。 |
| `items[].manufacturingProcessType` | 否 | 关联销售订单零件行当前制造加工类型。 |
| `items[].demandQty` | 是 | 关联销售订单零件行当前数量。 |
| `items[].makeQty` | 是 | 本次 MES 生产数量，与 `demandQty` 相同。 |
| `items[].buyQty` | 是 | 外购数量，当前固定为 `0`。 |
| `items[].stockQty` | 是 | 库存数量，当前固定为 `0`。 |
| `items[].requiredDate` | 是 | 只取 `sales_order_item.internal_delivery_date`；有值时补为当天 `23:59:59`，没有内部交期则不使用其他日期兜底并阻止下发。 |
| `items[].remark` | 否 | 关联销售订单零件行当前备注。 |
| `items[].customerRequirements` | 否 | 由当前材料名称和表面处理拼接。 |
| `items[].bom` | 否 | 工程材料编码和毛重生成的 BOM 摘要。 |
| `items[].processRoute` | 否 | 当前销售订单零件行的结构化工艺路线。 |

数量口径：

```text
已入库库存锁定数量 + 在产库存锁定数量 + demandQty = 销售订单零件当前数量
```

其中 `demandQty` 和 `makeQty` 表示本次需要 MES 生产的销售订单零件当前数量。提单和 Allocation 的数量快照只用于 QPO 内部兼容与追溯，不作为该请求体的业务来源。

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

## 8. 每日定时自动下发（对数据中心的影响）

待产零件模块提供每日定时自动下发能力，配置入口为待产零件页面顶部“定时下发策略”按钮，权限 `market:production-item:mes-production-schedule`。

**对数据中心的唯一影响是：调用时点由“人点按钮”变成“每天固定时间自动发起”，请求体、字段合同、鉴权方式、响应体语义和失败判定全部不变。** 数据中心不需要新增接口、字段或环境变量。

调度口径：

- 默认每天 17:00 触发（页面可改时间，精度到分钟），一天最多自动执行一次，跨实例通过配置表 `production_item_auto_dispatch_config.last_run_date` 的条件更新认领，不会重复触发。
- 下发范围是执行时点上所有满足条件的待下发 `PRODUCTION_REQUIRED` 行，含历史积压；**不设每日总量上限，不把未处理完的行顺延到第二天**。
- 只控制单批瞬时负载：默认单批 200 行、批间隔 3000ms，串行执行且跑在独立线程池（`productionAutoDispatchExecutor`，core=1/max=1），不挤占人工手动下发和 MES 物料号同步的线程。
- 每个批次内部仍按 `customerCode | customerName | internalProjectNo | saleOrderNo` 分组，一个分组一次 HTTP 请求，因此**对数据中心的请求数由销售订单数决定，不由行数决定**。调小单批行数不会减少请求数，只是把请求摊开在更长时间里。
- 候选行排序为 `sourceOrderId ASC, deliveryDate ASC NULLS LAST, id ASC`，让同一销售订单的行连续落进同一批次，避免切批把同一订单拆成多次请求。
- `mes_production_status = FAILED` 的行不自动重发，需人工在列表筛选“下发失败”后手动处理；`include_failed` 默认关闭。
- 连续 3 个批次全部失败（成功数和跳过数都为 0）会熔断中止当天任务并标记 `FAILED`，避免在数据中心或 MES 故障时反复打满下游。
- 执行结果（总行数 / 成功 / 失败 / 跳过 / 轮次 / 耗时 / 任务ID）回写配置表并在页面展示；实际下发任务仍按原方式记录为 `system_task`，`createdBy = SYSTEM`。
- 断点续跑：逐行回写 `SUCCESS / FAILED`，进程重启后再次触发只会捞剩余的 `PENDING` 行，无需游标。

运维排查入口：

- 接口：`GET/PUT /api/order-delivery/production-items/auto-dispatch/config`、`POST /api/order-delivery/production-items/auto-dispatch/run-now`。
- 环境变量：`ERP_PRODUCTION_AUTO_DISPATCH_CHECK_MS`（轮询间隔，默认 60000）、`ERP_PRODUCTION_AUTO_DISPATCH_QUEUE_CAPACITY`（独立线程池队列，默认 4）。
- 操作日志动作：`PRODUCTION_ITEM_MES_PRODUCTION_AUTO_SUBMIT`（提交定时下发任务）、`PRODUCTION_ITEM_AUTO_DISPATCH_CONFIG_UPDATE`（修改策略）。
