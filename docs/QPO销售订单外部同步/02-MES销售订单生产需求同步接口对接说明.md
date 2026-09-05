# MES 销售订单生产需求同步接口对接说明

版本日期：2026-08-17

## 1. 接口用途

MES 定时从 QPO 拉取销售订单生产需求所需的最小数据集，用于在 MES 侧识别需要创建或更新生产需求的销售订单明细。

本接口为只读接口：不会触发 MES / WMS / U9 下发，不会修改 QPO 销售订单状态。

## 2. 请求方式

```http
GET /api/integrations/sales-orders
Authorization: Bearer {QPO_OPEN_API_TOKEN}
```

说明：

- `QPO_OPEN_API_TOKEN` 由 QPO 统一入站开放接口 token 管理。
- token 错误或缺失时返回 HTTP `401`。
- token 轮换后旧 token 立即失效。

## 3. 查询参数

| 参数 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `pageSize` | integer | 否 | 每页订单数，只允许 `20` / `50` / `100`，默认 `100`。 |
| `cursor` | string | 否 | 下一页游标。首次同步不传；后续传上次响应的 `data.nextCursor`。 |
| `saleOrderNo` | string | 否 | 对外销售订单号精确查询，固定匹配 QPO `sales_order_header.document_no`，用于补同步某一单。 |
| `materialNo` | string | 否 | 物料号筛选，用于补同步命中物料行。 |
| `partNo` | string | 否 | 图号筛选，仅用于查询命中行，响应中不返回图号。 |
| `version` | string | 否 | 图纸版本筛选，仅用于查询命中行，响应中不返回版本。 |
| `status` | string | 否 | 销售订单状态筛选。 |
| `createdFrom` | datetime | 否 | 销售订单创建时间起。 |
| `createdTo` | datetime | 否 | 销售订单创建时间止。 |
| `updatedFrom` | datetime | 否 | 订单头或任意明细最后变更时间起。 |
| `updatedTo` | datetime | 否 | 订单头或任意明细最后变更时间止。 |

时间格式：`yyyy-MM-dd'T'HH:mm:ss`，例如 `2026-08-17T10:00:00`。

## 4. 响应结构

响应字段集合固定；字段没有业务值时仍返回字段，值为 `null`，不会省略字段。典型场景包括 `status`、`orderType`、`customerCode`、`supplierCode`、明细级 `internalProjectNo`、`customerOrderNo`、`customerProjectNo`、`materialNo`、`description`、`surfaceTreatment`、`remark` 和 `nextCursor`。

### 4.1 成功响应

```json
{
  "success": true,
  "code": 0,
  "message": "OK",
  "data": {
    "records": [
      {
        "saleOrderNo": "DOC-20260817-001",
        "createdAt": "2026-08-17T09:00:00",
        "changedAt": "2026-08-17T10:30:00",
        "status": "APPROVED",
        "orderType": "正式订单",
        "customerCode": "C02058",
        "supplierCode": "SUP-C02058",
        "items": [
          {
            "saleOrderItemNo": "10",
            "materialNo": "MAT-001",
            "releasedQty": 5,
            "description": "零件描述",
            "surfaceTreatment": "阳极氧化",
            "remark": "明细备注",
            "internalProjectNo": "IP-20260817-001-LINE",
            "customerOrderNo": "PO-20260817-001",
            "customerProjectNo": "CP-20260817-001"
          }
        ]
      }
    ],
    "pageSize": 100,
    "hasMore": false,
    "nextCursor": null
  }
}
```

### 4.2 空结果响应

```json
{
  "success": true,
  "code": 0,
  "message": "OK",
  "data": {
    "records": [],
    "pageSize": 100,
    "hasMore": false,
    "nextCursor": null
  }
}
```

## 5. 字段说明

### 5.1 订单字段

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `saleOrderNo` | string | 是 | 对外销售订单唯一编号，业务值固定为 QPO `sales_order_header.document_no`，与 QPO 下发 MES 生产时的销售订单号一致。 |
| `createdAt` | datetime | 是 | 销售订单创建时间。 |
| `changedAt` | datetime | 是 | 订单头或任意明细最后变更时间，用于增量同步水位。 |
| `status` | string | 否 | 销售订单状态，仅用于展示和查询，不限制 MES 创建生产需求。 |
| `orderType` | string | 否 | 销售订单类型，例如备货订单、正式订单、预测订单。 |
| `customerCode` | string | 否 | 客户编码，来自 QPO 销售订单客户编码。 |
| `supplierCode` | string | 否 | 供应商代码，来自 QPO 客户资料本地维护字段；未维护时返回 `null`。 |
| `items` | array | 是 | 当前订单命中的有效明细集合。 |

### 5.2 明细字段

| 字段 | 类型 | 必返 | 说明 |
| --- | --- | --- | --- |
| `saleOrderItemNo` | string | 是 | 订单内稳定且唯一的明细编号，来自 QPO 销售订单行号。 |
| `materialNo` | string | 否 | 销售物料号 / MES 物料编码，用于 MES 匹配物料；QPO 销售订单可能先于 MES 物料号生成，未获取物料号时返回空值 / `null`。 |
| `releasedQty` | decimal | 是 | 已提数量，固定来自 QPO `sales_order_item.released_qty`。 |
| `description` | string | 否 | 明细描述。 |
| `surfaceTreatment` | string | 否 | 表面处理要求。 |
| `remark` | string | 否 | 明细级备注。 |
| `internalProjectNo` | string | 否 | 零件级内部项目号，来自 QPO 销售订单明细。 |
| `customerOrderNo` | string | 否 | 零件级客户订单号，来自 QPO 销售订单明细。 |
| `customerProjectNo` | string | 否 | 零件级客户项目号，来自 QPO 销售订单明细。 |

## 6. 幂等键

MES 建议按以下业务键幂等落库：

```text
订单键：saleOrderNo
明细键：saleOrderNo + saleOrderItemNo
```

说明：

- `saleOrderNo` 是对外销售订单唯一编号，取 QPO `sales_order_header.document_no`。
- `saleOrderItemNo` 是订单内稳定明细编号。
- 同一订单多次同步时，MES 可按 `saleOrderNo + saleOrderItemNo` 做插入或更新。
- `changedAt` 可作为增量同步水位，不建议作为业务主键。

## 7. 分页与增量同步

### 7.1 首次全量同步

```http
GET /api/integrations/sales-orders?pageSize=100
Authorization: Bearer {QPO_OPEN_API_TOKEN}
```

如果响应 `data.hasMore=true`，继续使用 `data.nextCursor` 拉下一页。

### 7.2 游标翻页

```http
GET /api/integrations/sales-orders?pageSize=100&cursor={nextCursor}
Authorization: Bearer {QPO_OPEN_API_TOKEN}
```

### 7.3 增量同步

```http
GET /api/integrations/sales-orders?pageSize=100&updatedFrom=2026-08-17T00:00:00
Authorization: Bearer {QPO_OPEN_API_TOKEN}
```

建议 MES 保存上次成功同步的最大 `changedAt`，下一轮使用 `updatedFrom` 做增量同步；如果结果分页，仍按 `cursor` 连续拉完。

### 7.4 条件补同步

按销售订单号补同步：

```http
GET /api/integrations/sales-orders?saleOrderNo=DOC-20260817-001
Authorization: Bearer {QPO_OPEN_API_TOKEN}
```

按物料号补同步：

```http
GET /api/integrations/sales-orders?materialNo=MAT-001
Authorization: Bearer {QPO_OPEN_API_TOKEN}
```

按图号和版本补同步：

```http
GET /api/integrations/sales-orders?partNo=P-001&version=A
Authorization: Bearer {QPO_OPEN_API_TOKEN}
```

## 8. 错误响应

### 8.1 token 错误

```http
HTTP/1.1 401 Unauthorized
```

```json
{
  "success": false,
  "code": 401,
  "message": "销售订单同步接口 token 无效",
  "data": null
}
```

### 8.2 参数错误

```http
HTTP/1.1 400 Bad Request
```

```json
{
  "success": false,
  "code": 400,
  "message": "pageSize 只允许 20、50、100",
  "data": null
}
```

可能原因：

- `pageSize` 不是 `20` / `50` / `100`。
- 时间参数格式错误。
- `cursor` 格式错误。
- QPO 数据治理未通过，例如明细缺少 `saleOrderItemNo`。`materialNo` 不是必填字段，缺失时不会导致 `400`。

## 9. 不返回字段

本接口不返回以下类型字段：

- 价格、成本、费用字段。
- 图纸 URL、工艺路线、材料 JSON、费用 JSON。
- 客户详细字段，但订单级 `customerCode` 和 `supplierCode` 作为 MES 对接业务字段返回。
- QPO 内部 ID、治理字段、U9 内部追踪字段。
- 明细图号、版本、原始销售数量 `qty`。
