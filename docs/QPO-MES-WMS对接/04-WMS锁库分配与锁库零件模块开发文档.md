# QPO WMS 批量锁库与锁库零件模块开发文档

> 现行链路：`QPO -> 数据中心 -> WMS`。QPO 只保留批量任务 `WMS_ALLOCATION_BATCH`，底层只调用数据中心 endpoint `WMS_INVENTORY_ALLOCATE`。
> WMS 业务接口：`POST /api/v1/wms/admin-api/wms/datacenter/sale-lading/reserve`。

## 1. 当前结论

- 已提零件模块不再存在单条 WMS 直连任务；用户单选、多选都提交同一个批量入口。
- 后端按正式销售订单拆组，每个销售订单组生成一个 WMS reserve 请求，同一个销售订单内按 `productCode` 聚合。
- 请求 DTO 为 `WmsSaleLadingReserveReq`，响应 DTO 为 `WmsSaleLadingReserveResp`；adapter 固定为 `WMS_SALE_LADING_RESERVE_V1` / `WMS_SALE_LADING_RESERVE_RESPONSE_V1`。
- `externalBizId` 在本地入库后再发送；网络结果未知时只允许复用同一个 `externalBizId` 和原始 request snapshot 重放。
- `remark` 是 WMS 支持的正式字段，QPO 每个 item 都必须传。
- `inProductionCount` 归入“在产覆盖”，写入 `IN_PRODUCTION_LOCK`，新口径没有在产单号概念。

## 2. 请求契约

```json
{
  "externalBizId": "QPO-WMS-20260814-01KZZ08THK316WKM13FFENVJD0",
  "saleNo": "SO-20260814-001",
  "items": [
    {
      "productCode": "500901000700",
      "reserveCount": 5,
      "remark": "医用防水贴无菌敷贴"
    }
  ]
}
```

| 字段 | 规则 |
| --- | --- |
| `externalBizId` | 全局唯一，同一批重放必须复用原值。 |
| `saleNo` | 优先取销售订单 `documentNo`，兜底 `salesOrderNo`。 |
| `items[].productCode` | 已提零件 MES 物料号 `materialNo`，同一请求内唯一。 |
| `items[].reserveCount` | 同销售订单同物料有效 ticket 数量求和，使用 `BigDecimal`。 |
| `items[].remark` | 按 `sales_order_item.line_no ASC, ticket.id ASC` 取首个非空备注，兜底材料名、描述、图号。 |

## 3. 响应契约

```json
{
  "code": 200,
  "msg": "",
  "data": {
    "saleOrderNo": "SO-20260814-001",
    "externalBizId": "QPO-WMS-20260814-01KZZ08THK316WKM13FFENVJD0",
    "items": [
      {
        "productCode": "500901000700",
        "reserveCount": 5,
        "stockCount": 3,
        "inProductionCount": 1,
        "frozenCount": 4,
        "surplusCount": 1
      }
    ]
  }
}
```

校验规则：

- 根节点 `code` 必须为 200，`data.saleOrderNo` 和 `data.externalBizId` 必须与请求一致。
- 返回物料集合必须与请求完全一致，不允许缺行、增行或重复 `productCode`。
- `reserveCount` 必须与请求一致，所有数量必须是非负整数。
- 恒等式：`frozenCount + surplusCount = reserveCount`。
- 恒等式：`0 <= inProductionCount <= frozenCount`。
- 恒等式：`0 <= frozenCount - inProductionCount <= stockCount`。

## 4. 数量拆分

WMS 返回的是聚合物料结果，QPO 按稳定顺序拆回 ticket：

```text
sales_order_item.line_no ASC, ticket.id ASC
```

拆分桶：

- 库存覆盖：`stockFrozenCount = frozenCount - inProductionCount`，写 `IN_STOCK_LOCK`。
- 在产覆盖：`inProductionCount`，写 `IN_PRODUCTION_LOCK`。
- 待生产：`surplusCount`，写 `PRODUCTION_REQUIRED`。

阶段 31 后，锁库零件模块只展示 `IN_STOCK_LOCK` 和 `IN_PRODUCTION_LOCK`；`PRODUCTION_REQUIRED` 行进入待产零件模块 `/production-items`，由待产零件页面负责后续 MES 生产下发。

`order_delivery_ticket_allocation` 是锁库结果池。`qty` 表示 allocation 拆分后的当前数量，`ticket_qty` 表示未锁库存前的原始提单数量，必须保存原始提单数量快照。WMS 成功后结果进入锁库零件模块，原始 ticket 不删除，通过 `ticketId` 反查原始 `order_delivery_ticket`，完成行从已提零件默认列表隐藏。

## 5. 持久化与恢复

三张 WMS 流水表承载批量请求：

- `wms_reservation_batch`：保存 `external_biz_id`、`sale_order_no`、`request_fingerprint`、`request_payload`、`attempt_no`、`last_error_type` 和状态。
- `wms_reservation_item`：保存聚合后的 `product_code`、`reserve_count`、`remark` 和 WMS 数量结果。
- `wms_reservation_ticket`：保存 batch 与 ticket 的映射、行级快照和拆分状态。

状态机包含：`RESERVED`、`SUBMITTING`、`RESPONSE_RECEIVED`、`SUCCESS`、`FAILED`、`RESULT_UNKNOWN`、`APPLY_FAILED`。

恢复规则：

- `RESULT_UNKNOWN`：选择任一原 ticket 时恢复整个 batch，复用原 request snapshot 和 `externalBizId`。
- `APPLY_FAILED`：只重放本地 `applyResponse()`，不再次调用 WMS。
- `SUCCESS`：跳过并返回已完成结果。
- `FAILED`：数据修正后创建新 batch。

## 6. 锁库零件查询

锁库零件页面继续使用独立结果池：

- DTO：`LockedItemDTO`、`LockedItemPageDTO`、`LockedItemQueryReq`。
- 服务：`LockedItemService.listAllocations`、`LockedItemService.listTicketAllocations`。
- 列表接口：`/api/order-delivery/locked-items`。
- ticket 追溯接口：`/api/order-delivery/tickets/${ticketId}/allocations`。
- 前端页面：`LockedItemManage.vue`，路由 `/locked-items`。
- 权限：`market:locked-item:view`。

查询字段覆盖 `customerName`、`rfqNo`、`partNo`、`version`、`materialNo`、`internalProjectNo`、`allocationType`。锁库零件模块只展示 `IN_STOCK_LOCK` 和 `IN_PRODUCTION_LOCK`，`PRODUCTION_REQUIRED` 行进入待产零件模块。列表排序固定为 `order_delivery_ticket_allocation.id DESC`，查询必须继续应用数据范围。

## 7. 材料身份快照

WMS 锁库和锁库零件展示只记录可审计材料身份摘要，不把完整 `materialData` 作为接口主字段：

- `engineeringMaterialCode`：材料编码。
- `engineeringMaterialTypeCode` / `engineeringMaterialTypeName`：材料类型。
- `engineeringMaterialCategory`：材质分类。
- `engineeringMaterialName`：材料名称。
- `materialShape`：材料形态。

中文口径固定为：材料编码、材料类型、材质分类、材料形态。

## 8. 工艺路线快照

WMS 锁库不重新查询当前工艺字典，必须复制原始提单行的标准工艺快照，确保锁库、待产和后续 MES 生产使用同一条已审计的历史工艺身份。文件日志和审计摘要不得记录完整工艺 JSON，只记录必要的 `processCode / categoryName / detailName / unitWorkHours / sortNo` 摘要字段。

## 9. 权限与审计

批量触发权限仍为 `market:delivered-item:wms-allocate`，页面和后端都必须校验。审计动作：

| 动作 | 含义 |
| --- | --- |
| `DELIVERY_WMS_ALLOCATION_SUBMIT` | 提交 WMS 批量锁库任务。 |
| `DELIVERY_WMS_ALLOCATION_SUCCESS` | WMS 锁库结果成功落地。 |
| `DELIVERY_WMS_ALLOCATION_FAILED` | WMS 明确失败或本地业务校验失败。 |
| `DELIVERY_WMS_ALLOCATION_RESULT_UNKNOWN` | 传输结果未知，等待同 ID 核对或重放。 |
| `DELIVERY_WMS_ALLOCATION_APPLY_FAILED` | WMS 成功响应已保存，但本地写入待修复。 |
| `DELIVERY_WMS_ALLOCATION_RECONCILED` | 运维核对后完成结果确认。 |

文件日志事件保留以下固定英文前缀，方便 grep：

```text
WMS reservation batch started taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={}
WMS reservation batch completed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={}
WMS reservation group completed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={}
WMS reservation group failed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={}
WMS reservation recovery failed taskId={} batchId={} externalBizId={} saleOrderNo={} count={} status={} durationMs={}
```

失败堆栈进入 `qpo-error.log`。日志只能记录 `taskId`、`batchId`、`externalBizId`、`saleOrderNo`、`count`、`status`、`durationMs` 和脱敏摘要，不记录 token、Authorization header 或完整 JSON。
