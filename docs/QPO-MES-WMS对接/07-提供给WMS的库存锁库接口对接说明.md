# QPO 经数据中心调用 WMS 库存锁库接口对接说明

本文是现行 WMS 库存锁库接口说明。QPO 不再直连 WMS，也不再维护单条 WMS 请求协议；所有库存锁库都通过数据中心转发到 WMS：`QPO -> 数据中心 -> WMS`。

## 1. 接口信息

| 项目 | 内容 |
| --- | --- |
| QPO 功能编码 | `WMS_INVENTORY_ALLOCATE` |
| QPO 后台任务 | `WMS_ALLOCATION_BATCH` |
| 调用方向 | QPO -> 数据中心 -> WMS |
| HTTP Method | `POST` |
| 数据中心路径 | `/api/v1/wms/admin-api/wms/datacenter/sale-lading/reserve` |
| requestAdapter | `WMS_SALE_LADING_RESERVE_V1` |
| responseAdapter | `WMS_SALE_LADING_RESERVE_RESPONSE_V1` |
| 最低 timeout | `60000ms` |

部署只保留 `ERP_DATA_CENTER_WMS_ALLOCATION_PATH` 作为数据中心 endpoint path fallback；正式配置优先在 **系统 > 系统配置 > 出站接口** 的功能接口配置中维护。

## 2. 请求体

```json
{
  "externalBizId": "QPO-WMS-20260814-01KZZ08THK316WKM13FFENVJD0",
  "saleNo": "CO-0814-001",
  "items": [
    {
      "productCode": "500901000700",
      "reserveCount": 5,
      "remark": "医用防水贴无菌敷贴",
      "customerProjectNo": "CP-0814-001",
      "customerOrderNo": "CO-0814-001"
    },
    {
      "productCode": "500901000699",
      "reserveCount": 5,
      "remark": "可孚医用纱布块",
      "customerProjectNo": "CP-0814-001",
      "customerOrderNo": "CO-0814-001"
    }
  ]
}
```

字段规则：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| `externalBizId` | 是 | QPO 生成的外部业务 ID；同一批次重放必须复用。 |
| `saleNo` | 是 | 客户订单号；字段名沿用 WMS 现有接口，QPO 不再传自生成销售订单号。 |
| `items` | 是 | 同一 `saleNo` 下的聚合物料数组，单个零件也必须发送数组。 |
| `items[].productCode` | 是 | MES 物料号。 |
| `items[].reserveCount` | 是 | 同客户订单号同物料需求数量合计。 |
| `items[].remark` | 是 | WMS 支持的正式备注字段，QPO 必须传递。 |
| `items[].customerProjectNo` | 否 | 客户项目号，QPO 按销售订单行、销售订单头、提单行快照依次兜底。 |
| `items[].customerOrderNo` | 是 | 客户订单号，与外层 `saleNo` 使用同一解析结果。 |

同一请求中 `productCode` 不允许重复；QPO 在发送前已经按 `productCode ASC` 规范化排序并生成 SHA-256 指纹。若同一客户订单号、同一物料被多个 ticket 聚合，但解析出的 `customerProjectNo` 或 `customerOrderNo` 不一致，QPO 不发送该物料，按行级失败返回给页面。

## 3. 响应体

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

数量口径：

- `stockCount`：冻结前可用库存总量。
- `inProductionCount`：在产覆盖数量，QPO 映射为“在产覆盖”。
- `frozenCount`：库存覆盖 + 在产覆盖。
- `surplusCount`：仍需生产的数量。
- QPO 实际库存覆盖数量：`frozenCount - inProductionCount`。

校验恒等式：

```text
frozenCount + surplusCount = reserveCount
0 <= inProductionCount <= frozenCount
0 <= frozenCount - inProductionCount <= stockCount
```

## 4. QPO 落地规则

- 库存覆盖写入 `IN_STOCK_LOCK`。
- 在产覆盖写入 `IN_PRODUCTION_LOCK`。
- 缺口写入 `PRODUCTION_REQUIRED`。
- 写入 `order_delivery_ticket_allocation` 时保存 `ticket_qty` 原始提单数量快照。
- WMS 成功后结果进入锁库零件模块，原始 ticket 不删除。
- 通过 `ticketId` 可反查原始 `order_delivery_ticket`。

## 5. 材料身份字段

QPO 在请求快照、锁库结果和排查资料中使用材料身份摘要，不传递完整材料核价 JSON：

| 字段 | 中文 |
| --- | --- |
| `engineeringMaterialCode` | 材料编码 |
| `engineeringMaterialTypeCode` | 材料类型编码 |
| `engineeringMaterialTypeName` | 材料类型 |
| `engineeringMaterialCategory` | 材质分类 |
| `engineeringMaterialName` | 材料名称 |
| `materialShape` | 材料形态 |

## 6. 幂等与排查

WMS 已确认相同 `externalBizId` 返回原结果，不重复冻结。QPO 传输未知时只复用同一个请求快照重放，不换业务 ID。

排查字段：`taskId`、`batchId`、`externalBizId`、`saleOrderNo`、`productCode`、`reserveCount`、`status`、`durationMs`。不得记录 token、Authorization header 或完整请求/响应 JSON。
