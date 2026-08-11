# 37-RFQ 议价提单派生销售订单并接入 U9 开发文档

> 文档版本：V1.0

> 2026-08-09 材料主数据阶段 10 收口：RFQ 议价提单派生销售订单时，RFQ 零件、派生销售订单零件、已提 ticket 和 U9 预览使用同一套工程材料快照：材料编码、材料名称、材料类型、材质分类、材料形态。U9 发送物料身份仍为 MES 已确认物料号 `materialNo`，材料编码仅作为工程材料主数据身份展示和追溯。
>
> 文档日期：2026-08-07
>
> 文档状态：已完成并验收（2026-08-08）。本文目标链路、阶段实施计划和验收记录已与当前代码一致。
>
> 2026-08-11 U9 同步重构阶段 7 收口：RFQ 议价提单派生销售订单进入 U9 时，最终以 `42-销售订单U9同步模块重构开发文档.md` 的最小已验证契约为准。U9 同步使用已确认物料号和未税单价，`FinallyPriceTC` 取 `sales_order_item.untaxed_unit_price`，不发送客户料号、图号、版本、单位、换算率、税额或金额字段；工艺路线不发送给 U9。

> 2026-08-10 议价提单表格数据替换阶段 7 收口：正式提单前可通过 `/api/market/release/item-data-replacement/preview` 预览 Excel 替换结果，再通过 `/api/market/release/item-data-replacement/confirm` 确认保存。两个接口均复用 `market:release:create` 和 RFQ 数据权限，后端仍是最终边界；预览不写 `audit_log`，确认仅在业务字段落库时写 `RELEASE_ITEM_DATA_REPLACEMENT` 摘要。确认响应的 `affectedRows` 用于立即刷新议价提单列表，`acceptedQtyUpdates` 只回填本次页面草稿和勾选状态；Excel 的通过数量不写入 RFQ 零件主表，也不改变后续派生销售订单的数据库取值规则。日志不得记录完整 Excel、完整 multipart 请求体、完整业务体、Authorization header、token 或外部系统敏感响应。

## 1. 文档目的

本文用于规范 RFQ 询价订单在议价提单后进入 U9 的完整方案。

销售订单导入链路已经是：

```text
销售订单导入
    -> sales_order_header / sales_order_item
    -> 自动异常治理匹配
    -> 自动整单提单
    -> 已提零件获取 MES 物料号
    -> WMS 锁库 / 待产
    -> U9 同步
```

本次将 RFQ 议价提单接入同一套销售订单和 U9 模块：

```text
选择一个客户下的一个或多个 RFQ
    -> 选择本次需要提单的 RFQ 零件
    -> 创建一张派生销售订单
    -> 创建原有 RFQ order_delivery_ticket
    -> 已提零件继续获取 MES 物料号
    -> MES 结果同时回写 RFQ 主表和派生销售订单零件
    -> 未税价格、已确认物料号等条件满足后
    -> 在 U9 同步模块整单发送
```

核心原则是：

1. 一次议价提单提交只生成一张销售订单。
2. 一次提交中的所有 RFQ 必须属于同一个客户。
3. `order_delivery_ticket` 仍然是 RFQ 来源的提单记录，不改成销售订单来源。
4. 派生销售订单只作为 U9 的订单载体和后续销售订单查询载体。
5. MES 物料号确认同时沉淀到 RFQ 零件主表和派生销售订单零件。
6. U9 复用现有销售订单 U9 同步模块，不直接在议价提单接口调用 U9 或数据中心。
7. 业务失败时整批事务回滚，不生成销售订单、提单行或部分关联。
8. RFQ 零件与销售订单零件统一使用六个订单属性，取值固定为“零件级优先、订单级默认兜底”。

## 2. 当前实现与问题

### 2.1 当前 RFQ 议价提单

当前 `DeliveryGenerateReq` 以一个 `rfqId` 为主，RFQ 提单服务校验一个询价订单并为选择的零件生成 `order_delivery_ticket`。现有提单行保存：

- `rfq_id`、`item_id`。
- `source_type = RFQ`。
- `source_order_id = RFQ ID`。
- `source_item_id = item_quote_version.id`。
- 零件、材料、工艺、费用、图纸、重量和物料号快照。

当前前端可以一次选择多个 RFQ，但提交前会按 RFQ 分组，分别调用生成接口，因此一次页面操作可能生成多组独立提单结果。

### 2.2 当前销售订单 U9 同步

`SalesOrderU9SyncService` 只查询 `sales_order_header` 和 `sales_order_item`，不从 `order_delivery_ticket` 临时组装 U9 请求。U9 预览和提交要求：

- 销售订单状态为 `已生成提单`。
- 订单零件完整提单。
- `sales_order_item.material_no` 非空。
- `sales_order_item.material_no_status = 已确认`。
- `sales_order_item.untaxed_unit_price` 不为空；含税单价不能作为 U9 价格兜底。
- `sales_order_header.document_type_code` 不为空。
- 客户编码不为空。

因此，RFQ 提单如果只写 `order_delivery_ticket`，不会出现在 U9 同步页面。

### 2.3 当前 MES 回写边界

当前 `SourceItemMaterialNoSyncService` 根据提单行 `source_type + source_item_id` 回写来源主表：

```text
source_type = RFQ
    -> item_quote_version

source_type = SALES_ORDER
    -> sales_order_item
```

RFQ 派生销售订单后，提单行仍然是 RFQ 来源，因此需要增加提单行到派生销售订单零件的关联，并在同一 MES 成功路径内完成双写。

## 3. 业务范围与不变项

### 3.1 本次范围

- 议价提单一次提交支持一个客户下多个 RFQ 的零件选择。
- 一次提交创建一张 `sales_order_header`。
- 一次提交为每个选中提单零件创建一条 `sales_order_item`。
- 同时创建现有 `order_delivery_ticket`，继续进入已提零件。
- 议价提单左侧维护订单级默认信息：单据类型编码、下单日期、客户订单号、客户项目号、客户下单抬头、订单类型、内部项目号。
- RFQ `item_quote_version` 正式拥有下单日期、客户订单号、客户项目号、客户下单抬头、订单类型、内部项目号六个零件级订单属性。
- 议价提单右侧表格允许逐行编辑并独立保存上述六个零件级订单属性。
- 生成提单时固定使用“RFQ 零件值优先、左侧订单级默认值兜底”的有效值规则。
- 派生销售订单号和单据编号由系统生成，用户不输入。
- MES 物料号成功路径同步回写 RFQ 主表和派生销售订单零件。
- 现有销售订单 U9 列表、预览、整单同步、幂等、失败重试和状态回写复用不变。
- 增加派生销售订单来源追溯、幂等和数据权限校验。

### 3.2 不改变的业务规则

- RFQ 必须完成报价后才能议价提单。
- RFQ 提单数量不能超过零件剩余可提数量。
- 左侧订单级默认值不能覆盖 RFQ 零件主表已有非空值。
- 零件级字段为空时，正式生成提单事务才使用左侧默认值补齐 `item_quote_version`；独立保存空值仍表示该字段继承订单级默认值。
- 已经生成的历史 ticket 是不可变快照；部分已提单零件修改订单属性只影响后续剩余数量的提单。
- RFQ 的原有提单状态、已提数量和剩余数量继续由 `order_delivery_ticket` 汇总计算。
- `order_delivery_ticket.source_type` 继续使用 `RFQ`。
- 已提零件、WMS、待产、锁库和 MES 生产下发继续以 `order_delivery_ticket` 为业务来源。
- WMS 继续使用提单行的 `material_no`，不直接使用派生销售订单零件的物料号。
- 销售订单 U9 使用正式未税价格字段 `untaxed_unit_price`。
- 未税单价为空不阻断议价提单和进入已提零件，但阻断 U9 预览和提交；含税单价不能作为 U9 价格兜底。
- 未确认物料号不阻断进入已提零件，但阻断 U9 预览和提交。
- 不修改 RFQ 导入、核价、报价和销售订单独立导入的主表隔离规则。
- 直接导入的销售订单仍不写 `order_rfq / item_quote_version`；RFQ 原始订单和零件仍保留在 `order_rfq / item_quote_version`，派生销售订单只是新增的 U9 销售订单载体，不替代或迁移 RFQ 主表。
- 不新增用户权限编码；复用现有议价提单、已提零件和 U9 权限。
- 普通查询、客户选择、订单选择和 U9 预览不写操作日志，不新增高频业务 INFO。

## 4. 目标数据模型

### 4.1 `item_quote_version` 零件级订单属性

RFQ 零件主表与 `sales_order_item` 对齐以下六个字段：

| 业务字段 | 数据库字段 | 当前状态 | 本次处理 |
| --- | --- | --- | --- |
| 下单日期 | `sales_order_date DATE` | 当前缺失 | 新增 |
| 客户订单号 | `customer_order_no VARCHAR(120)` | 已存在但实体标记 `@Deprecated` | 正式恢复使用，移除 `@Deprecated` |
| 客户项目号 | `customer_project_no VARCHAR(120)` | 已存在但实体标记 `@Deprecated` | 正式恢复使用，移除 `@Deprecated` |
| 客户下单抬头 | `customer_order_title VARCHAR(255)` | 当前缺失 | 新增 |
| 订单类型 | `order_type VARCHAR(50)` | 已正常使用 | 保留并纳入统一取值规则 |
| 内部项目号 | `internal_project_no VARCHAR(120)` | 已存在但实体标记 `@Deprecated` | 正式恢复使用，移除 `@Deprecated` |

迁移通过新的 `COMMENT ON COLUMN` 说明这些字段属于 RFQ 零件级订单属性，不修改已经执行过的 `V65__sales_order_independent_tables.sql`。`ItemQuoteVersion` 增加 `salesOrderDate`、`customerOrderTitle`，并取消三个正式恢复字段的 deprecated 标记。

本次不恢复 `OrderRfq` 上已经废弃的销售订单字段。议价提单左侧值属于“本次生成的派生销售订单头默认值”，不是多个来源 RFQ 各自的订单头属性；正式保存位置是新生成的 `sales_order_header`。

`document_type_code` 继续只存在于派生 `sales_order_header`，不进入 `item_quote_version`。`project_no`、`arrange_type` 继续作为现有提单辅助字段，不纳入本次六字段统一模型。

### 4.2 `sales_order_header` 派生来源字段

新增迁移建议使用实际下一 Flyway 版本，例如：

```text
quote-backend/src/main/resources/db/migration/V90__rfq_release_derived_sales_order.sql
```

迁移执行前必须确认数据库当前最高版本；如果版本已超过 89，使用实际下一编号，不覆盖已有迁移。

在 `sales_order_header` 增加：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `source_type` | `VARCHAR(30)` | `DIRECT_IMPORT` 或 `RFQ_RELEASE`；本次派生订单固定为 `RFQ_RELEASE`，非空 |
| `source_release_batch_key` | `VARCHAR(100)` | 本次议价提单批次幂等键，非空且唯一 |
| `source_release_request_hash` | `VARCHAR(64)` | 后端对规范化业务请求计算的 SHA-256，用于识别同 key 参数变化 |

建议索引：

```sql
CREATE UNIQUE INDEX uk_sales_order_header_release_batch
    ON sales_order_header(source_release_batch_key)
    WHERE source_release_batch_key IS NOT NULL;

CREATE INDEX idx_sales_order_header_source_type_created
    ON sales_order_header(source_type, created_at DESC);

ALTER TABLE sales_order_header
    ADD CONSTRAINT chk_sales_order_header_release_source
    CHECK (
        source_type <> 'RFQ_RELEASE'
        OR (
            NULLIF(BTRIM(source_release_batch_key), '') IS NOT NULL
            AND NULLIF(BTRIM(source_release_request_hash), '') IS NOT NULL
        )
    );
```

派生订单直接使用现有 `customer_id` 保存本次选择的客户，不新增重复的来源客户字段。迁移必须将已有 `sales_order_header.source_type` 回填为 `DIRECT_IMPORT`。完成回填后，`source_type` 设置为 `NOT NULL DEFAULT 'DIRECT_IMPORT'`。后续直接导入显式写 `DIRECT_IMPORT`，RFQ 议价提单派生显式写 `RFQ_RELEASE`，运行时不保留空值兼容分支。

### 4.3 `sales_order_source_rfq` 来源订单关联表

一次派生销售订单可以包含多个 RFQ，不能把多个 RFQ ID 压缩到一个主表字段。新增：

```sql
CREATE TABLE sales_order_source_rfq (
    id BIGSERIAL PRIMARY KEY,
    sales_order_id BIGINT NOT NULL REFERENCES sales_order_header(id) ON DELETE CASCADE,
    rfq_id INTEGER NOT NULL REFERENCES order_rfq(id),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_sales_order_source_rfq UNIQUE (sales_order_id, rfq_id)
);
```

索引：

```sql
CREATE INDEX idx_sales_order_source_rfq_rfq
    ON sales_order_source_rfq(rfq_id, sales_order_id);

CREATE INDEX idx_sales_order_source_rfq_sales_order
    ON sales_order_source_rfq(sales_order_id, rfq_id);
```

该表只表示“派生销售订单由哪些 RFQ 产生”，不替代 `sales_order_item` 的来源零件关联。

### 4.4 `sales_order_item` RFQ 来源字段

在 `sales_order_item` 增加：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `source_type` | `VARCHAR(30)` | 本次固定为 `RFQ`；直接导入销售订单固定为 `DIRECT_IMPORT`，非空 |
| `source_order_id` | `BIGINT` | RFQ 的 `order_rfq.id` |
| `source_item_id` | `BIGINT` | RFQ 的 `item_quote_version.id` |
| `source_ticket_id` | `INTEGER` | 生成后关联的 `order_delivery_ticket.id`，类型与 ticket 主键一致 |

索引：

```sql
CREATE INDEX idx_sales_order_item_source_order_item
    ON sales_order_item(source_type, source_order_id, source_item_id, id);

CREATE UNIQUE INDEX uk_sales_order_item_source_ticket
    ON sales_order_item(source_ticket_id)
    WHERE source_ticket_id IS NOT NULL;

ALTER TABLE sales_order_item
    ADD CONSTRAINT fk_sales_order_item_source_ticket
    FOREIGN KEY (source_ticket_id) REFERENCES order_delivery_ticket(id);
```

`source_ticket_id` 增加到 `order_delivery_ticket(id)` 的外键，删除 ticket 的不可逆规则保持不变。同一个 RFQ 零件可以分批提单，因此不能对 `(source_order_id, source_item_id)` 做全局唯一约束；每一条派生销售订单零件通过唯一 `source_ticket_id` 关联一条 RFQ ticket。

`order_delivery_ticket` 不增加反向销售订单外键，避免两张表重复保存同一关系。MES 回写拿到 ticket 后，统一按 `sales_order_item.source_ticket_id = ticket.id` 反查派生销售订单零件。现有 ticket 的 `rfq_id / item_id / source_type / source_order_id / source_item_id` 不删除、不改语义。

迁移必须将已有 `sales_order_item.source_type` 回填为 `DIRECT_IMPORT`，完成后设置为 `NOT NULL DEFAULT 'DIRECT_IMPORT'`。历史直接导入零件的 `source_ticket_id` 可以为空，不做历史关联推断。

### 4.5 派生销售订单状态

派生销售订单创建后直接视为整单已提单：

```text
sales_order_header.status = 已生成提单
sales_order_item.status = 已生成提单
sales_order_item.released_qty = qty
sales_order_item.remaining_qty = 0
```

说明：`已提单` 只用于 RFQ 零件主表 `item_quote_version.status`，销售订单体系的订单头和零件行统一使用 `已生成提单`，避免 U9 同步前的整单提单判断出现口径分裂。

`u9_sync_status` 初始为空或现有未同步状态，不能在议价提单事务中标记为已同步。只有 U9 同步成功后才能写 U9 成功状态和外部单号。

## 5. 请求和响应契约

### 5.1 议价提单请求

沿用现有受保护接口：

```http
POST /api/market/generate-ticket
Permission: market:release:create
```

请求模型调整为客户级批次：

```json
{
  "rfqIds": [101, 102],
  "documentTypeCode": "SO01",
  "salesOrderDate": "2026-08-07",
  "customerOrderTitle": "客户正式订单",
  "orderType": "正式订单",
  "projectNo": "PROJECT-001",
  "internalProjectNo": "INTERNAL-001",
  "customerOrderNo": "CUSTOMER-001",
  "customerProjectNo": "CUSTOMER-PROJECT-001",
  "arrangeType": "计划",
  "releaseBatchKey": "client-generated-idempotency-key",
  "items": [
    {
      "itemId": 1001,
      "sourceType": "RFQ",
      "sourceOrderId": 101,
      "sourceItemId": 1001,
      "acceptedQty": 2
    },
    {
      "itemId": 2001,
      "sourceType": "RFQ",
      "sourceOrderId": 102,
      "sourceItemId": 2001,
      "acceptedQty": 1
    }
  ]
}
```

约束：

- `rfqIds` 至少一个，去重后使用。
- `items` 至少一行，所有 `sourceType` 必须是 `RFQ`。
- `releaseBatchKey` 由前端每次开始一次提交时生成，重试同一次请求必须沿用同一 key。
- `sourceReleaseRequestHash` 不由前端传入；后端以客户、排序后的 RFQ / 零件 / 数量和全部订单头输入计算规范化 SHA-256。
- `documentTypeCode` 必填，默认值和现有销售订单导入页面一致为 `SO01`。
- `salesOrderDate` 必填，用户在议价提单页面选择，不由后端静默改成当前日期。
- `customerOrderTitle` 可为空；填写时进入派生销售订单主表。
- `customerOrderNo`、`orderType`、`internalProjectNo` 是必填订单级默认值；`customerProjectNo` 可为空。
- 生成请求只提交零件 ID 和数量，不接受前端临时草稿覆盖数据库 RFQ 零件值；右侧行级修改必须先调用独立保存接口。
- 订单号和单据编号不从请求读取，由后端现有编号生成器生成。
- `rfqId` 单值字段从请求模型中移除；后端不再以单一 RFQ 作为批次边界。

### 5.2 响应模型

```json
{
  "success": true,
  "message": "已生成销售订单 SO-20260807-001，并生成 3 条提单行。",
  "salesOrderId": 501,
  "salesOrderNo": "SO-20260807-001",
  "documentNo": "DOC-20260807-001",
  "sourceRfqIds": [101, 102],
  "ticketCount": 3,
  "confirmedMaterialCount": 1,
  "pendingMaterialCount": 2,
  "ticketIds": [9001, 9002, 9003]
}
```

新增类型化响应 DTO，例如 `DeliveryBatchResultDTO`。响应中只返回订单、ticket ID 和计数摘要，不直接序列化完整 `OrderDeliveryTicket` 实体；不返回外部请求体、MES/U9 token、Authorization header 或完整外部响应。

### 5.3 幂等响应

当同一 `releaseBatchKey` 重复提交时：

- 不重复创建销售订单、来源关联、销售订单零件或提单行。
- 后端重新计算请求 hash，并与首次保存的 `source_release_request_hash` 比较。
- 返回第一次生成的 `salesOrderId`、`salesOrderNo`、`documentNo` 和摘要。
- 审计日志标记 `idempotentReplay=true`，不再次写入每条零件明细。

如果同一 key 对应的客户、RFQ、零件或订单头字段不一致，拒绝请求并返回“提单批次参数与首次提交不一致”，不覆盖首次数据。

请求 hash 只用于幂等比较，不用于密码、token 或密钥保护。规范化内容不写入文件日志，日志最多记录批次 key 的短摘要。

## 6. 后端服务设计

### 6.1 `DeliveryGenerateReq`

修改：

- 用 `List<Integer> rfqIds` 替换单一 `rfqId`。
- 新增 `documentTypeCode`。
- 新增 `salesOrderDate`。
- 新增 `customerOrderTitle`。
- 新增 `releaseBatchKey`。
- `customerOrderNo`、`customerProjectNo`、`orderType`、`internalProjectNo` 明确为本次派生销售订单头默认值。
- 旧 `startDate` / “开工日期”不再作为独立订单属性；`order_delivery_ticket.start_date` 按零件下单日期优先、订单头下单日期兜底。
- 保留 `projectNo`、`arrangeType` 和零件列表作为现有提单辅助信息。

请求 DTO 不保存密码、token 或外部系统认证信息。

### 6.2 RFQ 零件订单属性保存接口

新增 DTO：

```java
@Data
public class ReleaseItemOrderFieldUpdateReq {
    private Integer rfqId;
    private LocalDate salesOrderDate;
    private String customerOrderNo;
    private String customerProjectNo;
    private String customerOrderTitle;
    private String orderType;
    private String internalProjectNo;
}
```

新增接口：

```http
POST /api/market/release/items/{itemId}/order-fields
Permission: market:release:create
```

`InternalTargetLeadTimeService` 新增：

```java
public Map<String, Object> updateReleaseItemOrderFields(
        Integer itemId,
        ReleaseItemOrderFieldUpdateReq req)
```

保存规则：

1. 锁定并校验 RFQ、零件归属和当前用户数据权限。
2. 复用现有 `assertReleaseItemMutable(...)`：全部提单完成后不能修改；部分已提单且仍有剩余数量时可以修改，但只影响后续提单。
3. 六个零件字段都允许保存为空；空值表示没有零件级覆盖，后续继承左侧订单默认值。
4. 非空 `orderType` 只能是 `预测订单 / 备货订单 / 正式订单`。
5. 按数据库字段长度统一校验并 trim 字符串；日期使用 `LocalDate`。
6. 一次请求原子保存该行六个字段，不能逐字段产生六次请求。
7. 返回更新后的完整议价提单行 DTO，使页面草稿值与数据库一致。
8. 写操作日志 `RELEASE_ITEM_ORDER_FIELDS_UPDATE`；文件日志只记录 `rfqId / itemId / partNo / changedFields` 摘要，不记录整行请求。

该接口不写 `sales_order_header`、`sales_order_item` 或 `order_delivery_ticket`，也不触发 MES、WMS、U9 或数据中心调用。

### 6.3 `DeliveryService`

建议抽取客户级批次方法：

```java
public DeliveryBatchResult generateRfqReleaseSalesOrder(DeliveryGenerateReq req)
```

处理顺序必须固定：

1. 校验请求非空、权限和幂等 key。
2. 锁定客户及所有 RFQ 主表。
3. 校验所有 RFQ 具有相同客户 ID。
4. 校验所有 RFQ 在当前用户数据权限范围内。
5. 校验所有 RFQ 状态为 `已报价`。
6. 锁定并校验所有选中零件属于对应 RFQ。
7. 校验零件没有重复提单，且数量不超过剩余可提数量。
8. 按“零件值优先、订单默认值兜底”计算每行六个有效订单属性。
9. 对 RFQ 零件空字段补写有效值，不覆盖已有非空字段。
10. 生成系统销售订单号和单据编号。
11. 插入一条 `sales_order_header`，来源类型为 `RFQ_RELEASE`，订单字段使用左侧默认值。
12. 插入 `sales_order_source_rfq` 关联行。
13. 按稳定的 `sourceOrderId + 来源行号 + sourceItemId` 顺序处理每个提单需求。
14. 创建原有 RFQ 来源 `order_delivery_ticket`，订单属性使用有效零件值。
15. 创建对应 `sales_order_item`，使用有效零件值并通过 `source_ticket_id` 唯一关联刚创建的 ticket。
16. 按 RFQ 原有逻辑刷新零件提单数量、RFQ 状态和汇总字段。
17. 写一次批次级操作日志和低噪声文件日志。
18. 返回销售订单和提单摘要。

所有步骤位于同一个数据库事务中。外部 MES、WMS、U9 调用不得在该事务中触发。

### 6.4 订单默认值与零件覆盖规则

生成提单时，后端必须在锁定 RFQ 零件后计算六个有效值：

```text
effectiveSalesOrderDate =
    item.salesOrderDate ?: request.salesOrderDate

effectiveCustomerOrderNo =
    item.customerOrderNo ?: request.customerOrderNo

effectiveCustomerProjectNo =
    item.customerProjectNo ?: request.customerProjectNo

effectiveCustomerOrderTitle =
    item.customerOrderTitle ?: request.customerOrderTitle

effectiveOrderType =
    item.orderType ?: request.orderType

effectiveInternalProjectNo =
    item.internalProjectNo ?: request.internalProjectNo
```

统一字段映射：

| 业务字段 | 左侧订单默认值 | RFQ 零件字段 | 派生销售订单头 | 派生销售订单零件 | ticket 快照 |
| --- | --- | --- | --- | --- | --- |
| 下单日期 | `salesOrderDate` | `item_quote_version.sales_order_date` | 使用左侧值 | 使用有效零件值 | `start_date` 使用有效零件值 |
| 客户订单号 | `customerOrderNo` | `item_quote_version.customer_order_no` | 使用左侧值 | 使用有效零件值 | `customer_order_no` 使用有效零件值 |
| 客户项目号 | `customerProjectNo` | `item_quote_version.customer_project_no` | 使用左侧值 | 使用有效零件值 | `customer_project_no` 使用有效零件值 |
| 客户下单抬头 | `customerOrderTitle` | `item_quote_version.customer_order_title` | 使用左侧值 | 使用有效零件值 | 当前 ticket 无对应字段，不写入 |
| 订单类型 | `orderType` | `item_quote_version.order_type` | 使用左侧值 | 使用有效零件值 | `order_type` 使用有效零件值 |
| 内部项目号 | `internalProjectNo` | `item_quote_version.internal_project_no` | 使用左侧值 | 使用有效零件值 | `internal_project_no` 使用有效零件值 |

事务规则：

- RFQ 零件字段非空时保持原值，左侧默认值不得覆盖。
- RFQ 零件字段为空时，在正式生成提单事务中把有效值补写到 `item_quote_version`。
- `sales_order_item` 始终保存有效零件值，不保存未解析的空值和默认值引用关系。
- `sales_order_header` 始终保存左侧订单级值，即使某些零件存在行级覆盖。
- `salesOrderDate`、`customerOrderNo`、`orderType`、`internalProjectNo` 计算后必须非空；客户项目号和客户下单抬头可以为空。
- 任一选中零件缺少必填有效值时，整个批次回滚。
- 已存在的 ticket 快照永不随 RFQ 零件后续编辑而改变。

### 6.5 销售订单零件映射

RFQ `item_quote_version` 到 `sales_order_item` 使用显式映射方法，不使用整对象反射复制：

| RFQ 字段 | 销售订单字段 | 规则 |
| --- | --- | --- |
| `part_no` | `part_no` | 复制来源事实 |
| `description` | `description` | 复制来源事实 |
| `qty` / 本次 acceptedQty | `qty` | 只保存本次提单数量 |
| `version` | `version` | 复制 |
| 六个零件级订单属性 | 对应同名字段 | 使用第 6.4 节计算后的有效零件值 |
| `material_name` | `material_name` | 复制导入材料 / RFQ 材料事实 |
| `engineering_material_name` | `engineering_material_name` | 复制真实材料快照 |
| `material_shape` | `material_shape` | 复制材料形态 |
| `surface_treatment` | `surface_treatment` | 复制来源事实，销售匹配不覆盖 |
| `tax_included_unit_price` | `tax_included_unit_price` | 复制正式含税单价 |
| `tax_included_total_price` | `tax_included_total_price` | 按本次数量重新计算或按既有价格规则计算 |
| `untaxed_unit_price` | `untaxed_unit_price` | 复制正式未税单价 |
| `untaxed_total_price` | `untaxed_total_price` | 按本次数量重新计算或按既有价格规则计算 |
| `material_data` | `material_data` | 复制完整材料核价快照 |
| `process_route` | `process_route` | 复制工艺路线快照 |
| `fee_data` | `fee_data` | 复制费用快照 |
| `material_gross_weight` | `material_gross_weight` | 复制重量 |
| `part_net_weight` | `part_net_weight` | 复制重量 |
| `item_type` / 组合关系 | `item_type` / 组合关系 | 保留父子关系和排序 |
| `drawing_url` | `drawing_url` | 复制来源事实，但不由销售匹配写入 |
| `material_no` | `material_no` | 只复制已确认来源 |
| `material_no_status` | `material_no_status` | 已确认来源复制为 `已确认`，否则为 `未确认` |
| `material_no_source` 等确认字段 | 同名字段 | 只复制完整确认元组 |

`material_name` 仍表示导入材料，`engineering_material_name` 和 `material_data.name` 表示真实材料。RFQ 派生销售订单不能把真实材料写回 `material_name`。

### 6.6 组合件处理

- 组合件父件和子件分别创建 `sales_order_item`，保留稳定行号。
- 父子关系使用现有 `item_type`、`assembly_relation_status` 和来源 ID 组合表达。
- 子件 ticket 与子件销售订单零件一一关联。
- 父件如果本身不进入 MES 获取流程，仍须复制其来源物料号确认元组（如存在）。
- 父子任一行失败时整批事务回滚。

### 6.7 编号生成

复用销售订单导入使用的编号生成规则或抽取独立 `SalesOrderNumberService`：

- `salesOrderNo` 必须唯一。
- `documentNo` 必须唯一或遵循现有单据编号约束。
- 编号生成必须在事务内完成，并处理并发重试。
- 编号中可以体现日期或来源批次，但不能使用客户密钥、token 或完整请求内容。
- U9 `OtherID` 继续使用 `salesOrderNo`。

## 7. MES 双主表回写

### 7.1 来源解析

扩展 `SourceItemMaterialNoSyncService`：

```java
public void syncConfirmedMaterialNo(OrderDeliveryTicket ticket, String materialNo, String message)
```

处理顺序：

1. 锁定 ticket。
2. 校验并写回 RFQ `item_quote_version`。
3. 按 `sales_order_item.source_ticket_id = ticket.id` 查询派生销售订单零件；存在时锁定并写回。
4. 两边都使用相同的物料号确认元组：
   - `material_no`
   - `material_no_status`
   - `material_no_source`
   - `material_no_source_ticket_id`
   - `material_no_synced_at`
   - `material_no_message`
5. 任一方检测到不同物料号冲突时，整次回写拒绝覆盖，并记录冲突摘要。

### 7.2 幂等和冲突

| 来源状态 | 回写物料号 | 结果 |
| --- | --- | --- |
| 目标为空 | 写入并标记已确认 | 成功 |
| 目标已有相同物料号 | 不重复改变 | 幂等成功 |
| 目标已有不同物料号且已确认 | 不覆盖 | 冲突失败 |
| RFQ 已确认、销售订单为空 | 两边最终一致 | 补写销售订单 |
| 销售订单已确认、RFQ 为空 | 两边最终一致 | 按关联关系补写 RFQ |

现有 MES 即时成功、回调成功、活动主请求、follower fan-out 和冲突回调都必须经过公共服务，不能保留只更新单边的分支。

### 7.3 ticket 状态

- 前置确认物料号继承时，ticket 初始化为 `SUCCESS`。
- 未确认来源初始化为 `PENDING`。
- MES 即时成功时更新 ticket 并双写两个来源主表。
- 回调成功时更新 ticket 并双写两个来源主表。
- follower fan-out 时每个 follower 通过自身 ticket 关联更新对应销售订单零件。
- WMS 仍读取 `order_delivery_ticket.material_no`，不读取销售订单零件替代。

## 8. U9 同步设计

### 8.1 复用现有模块

不新增 RFQ U9 Controller，不新增 RFQ U9 外部 client。现有接口继续使用：

```text
GET  /api/market/u9-sync/orders
GET  /api/market/u9-sync/orders/{salesOrderId}/preview
POST /api/market/u9-sync/orders/{salesOrderId}/sync
POST /api/market/u9-sync/sync
```

派生销售订单按 `sales_order_header.source_type = RFQ_RELEASE` 标识来源，但仍由现有销售订单 U9 服务统一查询和同步。

### 8.2 U9 字段映射

| U9 字段 | 来源 |
| --- | --- |
| `OtherID` | `sales_order_header.sales_order_no` |
| `DocNo` | `sales_order_header.document_no` |
| `BusinessDate` | `sales_order_header.sales_order_date` |
| `OrderBy` | `sales_order_header.customer_code` |
| `DocumentType` | `sales_order_header.document_type_code` |
| `ItemCode` | `sales_order_item.material_no` |
| `FinallyPriceTC` | `sales_order_item.untaxed_unit_price` |
| 数量 | `sales_order_item.qty` |

U9 同步最终以 42 号文档的最小已验证契约为准：`IsPriceIncludeTax=false`，`FinallyPriceTC` 使用 `sales_order_item.untaxed_unit_price`，税率、税额、未税金额和含税金额由 U9 自动计算；QPO 不发送客户料号、图号、版本、单位、换算率、订单价、税额或金额字段。客户订单号、客户项目号、内部项目号、交期、未税价格和备注通过已确认的 U9 私有字段写入行扩展字段。

`customer_order_no` 和 `customer_order_title` 必须保存到 QPO 销售订单主表并展示在销售订单详情、议价提单结果和 U9 预览中。当前 `U9SalesOrderHeadDTO` 没有可序列化的客户下单抬头字段，`customerOrderNo` 也被标记为 `@JsonIgnore`，因此本阶段不虚构外部字段、不把两者擅自塞入 `Memo`。如果数据中心或 U9 后续提供正式字段，再通过独立接口契约变更接入。

### 8.3 U9 阻断规则

派生销售订单和直接导入销售订单使用同一组预览校验：

- 订单状态不是 `已生成提单`：阻断。
- 零件缺少确认物料号：阻断，提示缺少 MES 物料号。
- `material_no_status` 不是 `已确认`：阻断。
- 未税单价为空：阻断，提示“未税单价为空”。
- 单据类型编码为空：阻断。
- 客户编码为空：阻断。

预览通过后提交仍必须重新锁定订单并重新校验，不能依赖前端之前的预览结果。

## 9. 前端议价提单改造

### 9.1 `MarketRelease.vue`

左侧订单级默认信息调整为：

| 字段 | 必填 | 说明 |
| --- | --- | --- |
| 单据类型编码 | 是 | 默认 `SO01`，只写派生销售订单头 |
| 下单日期 | 是 | 替代旧“开工日期”输入，作为零件空值 fallback |
| 客户订单号 | 是 | 作为零件空值 fallback |
| 客户项目号 | 否 | 作为零件空值 fallback |
| 客户下单抬头 | 否 | 作为零件空值 fallback |
| 订单类型 | 是 | `预测订单 / 备货订单 / 正式订单` |
| 内部项目号 | 是 | 作为零件空值 fallback |

输入区沿用销售订单导入页面的字段样式，放在议价提单左侧订单信息区域。删除旧 `releaseForm.startDate` 独立语义，统一使用 `releaseForm.salesOrderDate`。

提交逻辑调整为：

```text
选择客户
    -> 选择多个 RFQ
    -> 加载多个 RFQ 的可提零件
    -> 勾选本次要提单的零件
    -> 前端直接组装一个批次 payload
    -> 调用一次 /api/market/generate-ticket
    -> 展示一个销售订单结果和多个提单行结果
```

删除或停止使用：

- 按 RFQ 分组后循环调用生成接口。
- “按 N 个询价订单分别生成提单”的成功提示。
- 只展示 RFQ 提单结果、不展示派生销售订单号的旧摘要。

保留：

- 一个客户下多选 RFQ。
- 零件级选择和数量校验。
- 议价提单前的版本、图纸和内部目标交期相关能力。
- `market:release:create` 权限控制。

### 9.2 右侧 RFQ 零件订单属性维护

六个维护列放在当前表格已有业务列的末尾、`状态` 列之前，顺序固定为：

```text
...
下单日期
客户订单号
客户项目号
客户下单抬头
订单类型
内部项目号
状态
```

控件：

- 下单日期：`el-date-picker`。
- 客户订单号、客户项目号、客户下单抬头、内部项目号：`el-input`。
- 订单类型：`el-select`，复用三种订单类型。
- 每个订单属性单元格内放置独立 `保存` 按钮，交互与备注、物料描述等行内字段一致。

前端仍复用后端 `POST /api/market/release/items/{itemId}/order-fields` 原子接口。用户点击某一列保存时，前端只使用当前列草稿值，其余五列使用该行已保存值组包，避免误保存其他未确认草稿。按钮拥有独立 loading、重复点击保护、成功/失败提示和后端返回值回填。

页面草稿字段建议使用：

```text
salesOrderDateDraft
customerOrderNoDraft
customerProjectNoDraft
customerOrderTitleDraft
orderTypeDraft
internalProjectNoDraft
```

展示规则：

- 输入框展示 RFQ 零件主表实际值。
- 实际值为空时，输入框保持空，不把左侧默认值伪装成已保存的零件值。
- 可通过 placeholder 或 tooltip 显示当前会继承的订单默认值。
- 点击保存时，空输入写为数据库空值，继续表示继承默认值。
- 正式生成提单成功后，后端已经把 fallback 有效值补写到 RFQ 零件，页面刷新后显示实际值。
- 选中零件存在未保存订单属性草稿时，禁用“生成正式提单”并提示先保存；生成接口不接收未保存草稿。
- 全部已提单行禁用输入和保存；部分已提单且仍有剩余数量的行允许编辑，变更只影响后续 ticket。
- 表格继续使用固定宽度列和横向滚动，不压缩图号、数量、状态等现有列。

保存按钮沿用 `market:release:create`；无权限时不显示按钮，后端接口仍执行最终权限校验。

`marketApi.js` 新增：

```js
export const updateReleaseItemOrderFields = (itemId, payload) =>
  postJson(`/api/market/release/items/${itemId}/order-fields`, payload)
```

`MarketRelease.vue` 新增 `saveReleaseItemOrderField(row, field)`，payload 包含 `rfqId`、当前列草稿值和其他五列已保存值。保存成功后同时更新当前分页行和 `selectedItemIdMap` 中的已选行快照；未保存的其他列草稿继续保留，避免生成请求使用旧页面状态，也避免单列保存误覆盖其他草稿。生成接口仍以数据库锁定后的值为最终依据。

### 9.3 提单完成结果

成功后展示：

- 销售订单号。
- 单据编号。
- 生成零件数量。
- 生成提单行数量。
- 已确认物料号数量。
- 待获取 MES 物料号数量。
- 来源 RFQ 数量和 RFQ 订单号。

提供只读跳转：

- 查看销售订单。
- 进入 U9 同步页面查看该销售订单。
- 查看已提零件。

这些按钮只做页面跳转，不在议价提单成功后自动调用 MES、WMS 或 U9。

## 10. 权限和数据范围

本次不新增用户权限，复用：

| 能力 | 权限 |
| --- | --- |
| 查看议价提单工作台 | `market:release:view` |
| 选择客户 / RFQ / 零件 | `market:release:view`、现有 release 选择权限 |
| 保存 RFQ 零件订单属性 | `market:release:create` |
| 生成派生销售订单和 RFQ 提单 | `market:release:create` |
| 查看派生销售订单 | `market:sales-order:view` |
| 查看 U9 同步页面 | `market:u9-sync:view` |
| 提交 U9 同步 | `market:u9-sync:submit` |
| 查看已提零件 | `market:delivered-item:view` |
| MES 物料号获取 | `market:delivered-item:mes-sync` |

后端要求：

- 议价提单生成前，对每个 RFQ 和零件分别应用数据权限。
- 同客户多 RFQ 选择不能绕过任一 RFQ 的数据范围。
- 派生销售订单继承当前用户的 `created_by / owner_user_id / owner_dept_id`。
- U9 列表、预览和提交继续应用销售订单数据范围。
- 直接通过销售订单 ID 访问派生订单时仍必须通过销售订单权限和数据范围校验。
- 无权限时返回空列表或 403，不能泄露客户、RFQ 号或零件信息。

不新增 `common:*` 权限，不新增独立的 RFQ-U9 权限。

## 11. 操作日志和文件日志

### 11.1 操作日志

RFQ 零件订单属性独立保存记录：

```text
action = RELEASE_ITEM_ORDER_FIELDS_UPDATE
module = MARKET
targetType = item_quote_version
targetId = itemId
```

摘要只记录 `rfqId / itemId / partNo / changedFields`，before/after 使用六个订单属性的结构化值；不记录完整页面 payload。普通表格加载不写操作日志。

沿用 `DELIVERY_TICKET_GENERATE`，增加批次摘要：

```json
{
  "trigger": "RFQ_RELEASE_TO_SALES_ORDER",
  "salesOrderId": 501,
  "salesOrderNo": "SO-20260807-001",
  "documentNo": "DOC-20260807-001",
  "sourceRfqIds": [101, 102],
  "ticketCount": 3,
  "salesOrderItemCount": 3,
  "confirmedMaterialCount": 1,
  "pendingMaterialCount": 2,
  "idempotentReplay": false
}
```

日志 target 建议使用：

```text
module = MARKET
targetType = sales_order_header
targetId = salesOrderId
action = DELIVERY_TICKET_GENERATE
```

不在操作日志中记录：

- 完整请求体。
- 完整零件列表。
- 图纸内容或文件字节。
- MES / U9 token。
- `client_secret`。
- `Authorization` header。
- 完整外部请求或响应。

### 11.2 文件日志

只记录批次级低噪声摘要：

```text
RFQ release sales order completed salesOrderId={} salesOrderNo={} sourceRfqCount={} itemCount={} ticketCount={} pendingMaterialCount={} durationMs={}
```

失败日志记录批次 ID、销售订单 ID（如已生成）、RFQ 数量、零件数量、耗时和异常堆栈。不能在循环内逐行打印零件请求或外部响应。

普通页面读取、U9 列表、U9 预览、已提零件查询不写操作日志，也不新增业务 INFO。

## 12. 分阶段开发计划

### 阶段 1：RFQ 零件订单属性和派生来源数据模型

目标：RFQ 零件具备与销售订单零件一致的订单属性，并建立派生销售订单来源关系。

修改：

- 新增实际下一编号迁移，例如 `V90__rfq_release_derived_sales_order.sql`。
- `item_quote_version` 新增 `sales_order_date`、`customer_order_title`。
- 正式恢复 `customer_order_no / customer_project_no / internal_project_no`，保留现有 `order_type`。
- 更新 `ItemQuoteVersion`，移除三个正式字段的 `@Deprecated`。
- `sales_order_header` 新增派生来源和批次幂等字段。
- 新增 `sales_order_source_rfq`。
- `sales_order_item` 新增 RFQ 来源和 ticket 关联字段。
- `sales_order_item.source_ticket_id` 建立到 RFQ ticket 的唯一关联；`order_delivery_ticket` 不增加重复反向外键。
- 更新实体、Mapper、`schema.sql` 和索引。
- 议价提单批量查询行补齐六个 RFQ 零件订单属性。
- 历史销售订单和零件只回填明确的来源类型，不推断历史 ticket 关联，不修改既有 RFQ ticket 的来源字段。

验收：

- RFQ 零件主表存在六个正式订单属性字段。
- RFQ 零件查询可以返回六个字段的原始值。
- 新字段和索引存在。
- 多个 RFQ 可以关联一张销售订单。
- 一个销售订单零件可以通过 `source_ticket_id` 精确关联一个派生 ticket。
- 历史数据查询和迁移不受影响。

### 阶段 2：RFQ 零件订单属性独立保存

目标：议价提单右侧每个 RFQ 零件可以独立保存六个订单属性。

修改：

- 新增 `ReleaseItemOrderFieldUpdateReq`。
- 新增 `POST /api/market/release/items/{itemId}/order-fields`。
- `InternalTargetLeadTimeService` 新增 `updateReleaseItemOrderFields(...)`。
- 复用 `market:release:create` 和 `assertReleaseItemMutable(...)`。
- 保存空值表示继承订单默认，不主动写入左侧默认值。
- 新增 `RELEASE_ITEM_ORDER_FIELDS_UPDATE` 操作日志。
- 补数据权限、字段长度、订单类型、部分提单和全部提单测试。

验收：

- 六个字段一次请求原子保存。
- 保存只修改指定 RFQ 零件。
- 部分已提单且仍有剩余数量时可保存，历史 ticket 不变化。
- 全部已提单后不能保存。
- 无 `market:release:create` 不能调用接口。
- 无 RFQ 数据权限不能通过 item ID 越权保存。

### 阶段 3：请求契约和客户级批次校验

目标：议价提单后端从源头执行“同客户、多 RFQ、一批次”。

修改：

- 修改 `DeliveryGenerateReq`，支持 `rfqIds / documentTypeCode / salesOrderDate / customerOrderTitle / releaseBatchKey`，现有客户订单号等字段改为订单级默认值，移除旧 `startDate` 独立语义。
- 新增 `DeliveryBatchResultDTO`，修改 `MarketController` 使用类型化响应。
- 抽取客户级 RFQ 批次校验服务。
- 校验 RFQ 状态、数据权限、客户一致性、零件归属、提单数量和重复提单。
- 停止前端按 RFQ 分组循环调用接口。

验收：

- 同客户多个 RFQ 可以一次提交。
- 跨客户 RFQ 无法提交。
- 非已报价 RFQ 无法提交。
- 无数据权限的 RFQ 不进入错误信息或结果。
- 同一 `releaseBatchKey` 重试不重复写入。

### 阶段 4：订单默认值补齐、派生销售订单和 RFQ 提单原子生成

目标：按“零件值优先、订单默认值兜底”一次生成一张销售订单和全部 RFQ 提单行。

修改：

- 新增或抽取 `RfqReleaseSalesOrderService`。
- 抽取销售订单编号生成逻辑。
- 锁定 RFQ 零件后计算六个有效订单属性。
- 只对 RFQ 零件空字段补写订单默认值，已有值不覆盖。
- 显式映射 RFQ 零件到销售订单零件。
- 保留组合件父子关系和稳定行号。
- 创建 `sales_order_source_rfq` 关联。
- 创建 RFQ 来源 `order_delivery_ticket`。
- 使用 `sales_order_item.source_ticket_id` 建立唯一关联。
- 更新 RFQ 零件提单数量和 RFQ 状态。
- 全流程使用单一数据库事务。

验收：

- 一次请求生成一张销售订单。
- 一个销售订单包含全部选中零件。
- 销售订单头使用左侧订单默认值，销售订单零件使用逐行有效值。
- ticket 使用逐行有效值，客户下单抬头因无 ticket 字段而不写入。
- RFQ 零件已有订单属性不被左侧默认值覆盖。
- RFQ 零件空字段在正式提单事务中补齐。
- 已提零件可以看到全部 RFQ 来源提单行。
- 销售订单状态为 `已生成提单`。
- 自动生成的数量和剩余数量正确。
- 中途异常时销售订单、关联表、零件和 ticket 全部回滚。

### 阶段 5：MES 双向物料号回写

目标：RFQ 主表和派生销售订单主表始终保持一致的确认物料号。

修改：

- 扩展 `SourceItemMaterialNoSyncService`。
- 即时成功、回调成功、活动主请求完成和 follower fan-out 统一通过关联 ticket 更新两边。
- 已确认来源初始化 ticket 为 `SUCCESS`，未确认来源初始化为 `PENDING`。
- 增加双边锁定、幂等和冲突保护。
- 更新 MES 结果摘要和操作日志。

验收：

- RFQ 物料号确认后，`item_quote_version` 和 `sales_order_item` 均为已确认。
- 同物料号重复回写幂等。
- 不同物料号不覆盖任一主表。
- 回调一次可以 fan-out 更新所有关联等待行。
- 销售订单直接导入链路仍只回写 `sales_order_item`，不受影响。

### 阶段 6：U9 同步模块接入派生订单

目标：派生销售订单可通过现有 U9 页面预览和整单同步。

修改：

- 检查 `SalesOrderU9SyncService` 不按导入来源错误过滤派生订单。
- U9 订单列表增加来源展示：`RFQ 议价提单派生` / `销售订单导入`。
- 预览继续读取 `sales_order_header / sales_order_item`。
- 预览严格校验物料号确认、未税单价、客户编码和单据类型编码。
- 提交继续使用现有数据中心统一 client、幂等、失败重试和状态回写。
- 增加从议价提单结果跳转 U9 同步页面的 query 参数。

验收：

- 派生销售订单出现在 U9 同步订单列表。
- 物料号未确认时 U9 不调用外部系统。
- 未税单价为空时 U9 不调用外部系统。
- 条件满足后整单同步，U9 请求使用销售订单表字段。
- U9 成功或失败状态只回写派生销售订单，不修改 RFQ 报价状态。

### 阶段 7：前端议价提单工作台收口

目标：用户能理解“议价提单同时生成提单和 U9 销售订单”。

修改：

- `MarketRelease.vue` 左侧增加并统一七个订单级字段，旧“开工日期”改为“下单日期”。
- 右侧表格在状态列前新增六个 RFQ 零件订单属性维护列。
- 六个订单属性列各自提供行内 `保存` 按钮，不再额外增加“订单信息操作”列。
- 输入为空时展示继承默认值提示，但不伪装为已保存零件值。
- 同一客户下支持多 RFQ 选择和零件选择。
- 提交只发送一次批次请求。
- 成功结果展示派生销售订单号、单据编号、来源 RFQ 数量、提单行数量和 MES 状态摘要。
- 增加查看销售订单、进入 U9 同步和查看已提零件的只读跳转。
- 无 `market:release:create` 时隐藏提交按钮。
- 保留前端字段校验，但后端校验作为最终边界。

验收：

- 页面不再提示按 RFQ 分别生成提单。
- 页面明确显示一批生成一张销售订单。
- 六个维护列位于现有业务列末尾、状态列之前。
- 每个订单属性列都可以单独编辑并保存，保存某一列不误保存其他列草稿。
- 保存后刷新仍能看到 RFQ 零件实际值。
- 选中行存在未保存草稿时不能生成正式提单。
- 全部已提单行不能编辑，部分已提单行修改不影响历史 ticket。
- 小屏输入区和结果区自然换行。
- 重试使用同一幂等 key，不产生重复订单。
- 跳转按钮不触发 MES、WMS 或 U9 写操作。

### 阶段 8：文档、权限日志和全量回归（已完成）

目标：RFQ、销售订单、MES、WMS、待产、锁库和 U9 口径完整收口。

修改：

- 更新 02、07、08、10、13、14、16、18、19、32、33、35、36 号相关文档。
- 更新权限清单，明确本次不新增权限。
- 更新日志运维说明和主流程文件日志规范。
- 更新销售订单 U9 接口说明，补充 RFQ 议价提单派生来源。
- 增加迁移、事务、MES 双写、U9 阻断、权限和数据范围测试。
- 执行后端、前端测试和构建。

验收：

- 文档明确 RFQ 议价提单可以派生销售订单进入 U9。
- 文档不包含真实 token、密钥或外部接口敏感响应。
- 操作日志和文件日志不包含敏感认证信息或完整业务体。
- RFQ 主流程和销售订单直接导入主流程均不回退。

阶段 8 已完成。V90 迁移测试同时验证批次幂等、RFQ 来源、销售订单来源和 ticket 关联查询的 `EXPLAIN` 计划命中对应索引；后端、前端测试与前端构建均已通过。

## 13. 测试设计

### 13.1 数据库迁移测试

- 检查 `item_quote_version.sales_order_date / customer_order_title` 存在。
- 检查 RFQ 零件六个订单属性的实体映射和数据库类型正确。
- 检查 `customer_order_no / customer_project_no / internal_project_no` 不再作为 deprecated 运行时字段。
- 检查 `sales_order_header` 派生来源和幂等字段存在。
- 检查 `sales_order_source_rfq` 外键、唯一约束和索引存在。
- 检查 `sales_order_item` 来源字段和唯一 ticket 关联索引存在。
- 使用 `EXPLAIN` 验证来源查询、批次幂等查询和 ticket 关联查询命中索引。

### 13.2 后端提单测试

- RFQ 零件六个订单属性可以独立保存和重新读取。
- 保存空值后仍保持零件字段为空，不提前复制订单默认值。
- 全部已提单行不能修改，部分已提单行只影响后续提单。
- 一个客户选择一个 RFQ，生成一张销售订单和多条提单。
- 一个客户选择多个 RFQ，生成一张销售订单和全部对应提单。
- 跨客户 RFQ 混选被拒绝。
- RFQ 非 `已报价` 被拒绝。
- 零件不属于 RFQ 被拒绝。
- 数量超过剩余可提数量被拒绝。
- 任一零件失败时整批回滚。
- 同一幂等 key 重试不重复生成。
- 同 key 参数变化被拒绝。
- RFQ 零件非空值优先于订单默认值。
- RFQ 零件空值使用订单默认值并在事务中补写主表。
- 派生销售订单头保存订单默认值，销售订单零件保存逐行有效值。
- ticket 的五个现有订单属性使用逐行有效值，客户下单抬头不写入 ticket。
- RFQ 状态、提单数量和销售订单状态正确写回。

### 13.3 物料号测试

- RFQ 已确认物料号创建 ticket 为 `SUCCESS`。
- RFQ 已确认物料号复制到销售订单零件。
- RFQ 未确认时 ticket 和销售订单零件均为待处理状态。
- MES 即时成功双写 RFQ 和销售订单零件。
- MES 回调成功双写 RFQ 和销售订单零件。
- follower fan-out 双写对应的 RFQ 和销售订单零件。
- 相同物料号回写幂等。
- 不同物料号回写冲突且两边都不被覆盖。
- 销售订单直接导入的 MES 回写仍不写 RFQ 表。

### 13.4 U9 测试

- 派生销售订单出现在 U9 列表。
- `ItemCode` 等于销售订单零件已确认物料号。
- `FinallyPriceTC` 等于销售订单零件未税单价，不发送 `OrderPriceTC`。
- 未税单价为空时预览阻断且不调用数据中心。
- 物料号未确认时预览阻断且不调用数据中心。
- 预览通过后提交再次校验。
- 整单同步、幂等、失败重试和状态回写不回退。
- U9 失败只更新派生销售订单 U9 状态，不改变 RFQ 报价和提单状态。

### 13.5 前端测试

- 左侧包含七个订单级字段，旧“开工日期”不再作为独立输入。
- 右侧状态列前依次包含六个 RFQ 零件订单属性列，不再包含独立订单信息操作列。
- 六个订单属性列各自提供行内保存按钮；保存单列时只提交该列草稿，其余字段沿用已保存值。
- 无 `market:release:create` 时保存按钮隐藏。
- 全部已提单行不能编辑订单属性。
- 选中行存在未保存草稿时不能生成正式提单。
- 多 RFQ 同客户选择后只调用一次生成接口。
- 请求包含 `rfqIds` 和 `releaseBatchKey`。
- 页面不再按 RFQ 循环调用生成接口。
- 成功结果展示销售订单号和提单摘要。
- 可以跳转销售订单和 U9 同步页面。
- 无生成权限时提交按钮隐藏。
- 错误时显示后端脱敏错误，不显示完整请求体。

## 14. 全量验收

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
$env:DB_USERNAME = ((Select-String -Path '..\erp.env' -Pattern '^POSTGRES_USER=').Line -split '=',2)[1]
$env:DB_PASSWORD = ((Select-String -Path '..\erp.env' -Pattern '^POSTGRES_PASSWORD=').Line -split '=',2)[1]
$env:DB_URL = 'jdbc:postgresql://localhost:5432/postgres'
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

额外验收：

- RFQ 零件和销售订单零件采用同一套六个订单属性字段。
- 左侧订单属性只作为订单头值和零件空字段 fallback，不覆盖零件已有值。
- 右侧六个维护列位于状态列之前，可以逐行独立保存。
- 一个客户下多个 RFQ 零件一次提交只产生一张销售订单。
- RFQ 提单行仍以 RFQ 为来源并进入已提零件。
- 派生销售订单可在 U9 模块选择、预览和同步。
- MES 物料号成功后 RFQ 主表和销售订单零件均显示已确认。
- WMS 使用提单物料号，和派生销售订单解耦。
- 待产、锁库和 MES 生产下发链路不回退。
- 销售订单直接导入、自动补全和自动提单链路不回退。
- RFQ 导入、核价、报价和原有议价提单校验不回退。
- 议价提单接口不调用 U9、MES、WMS 或数据中心外部写接口。
- U9 不发送未确认物料号、客户料号、图号、版本、税额、金额或完整外部敏感信息。
- 操作日志和文件日志不记录 token、Authorization header、密钥、完整请求体或完整响应体。
- 不新增用户权限，不新增 `common:*` 权限。

## 15. 交付结果

完成后，业务口径统一为：

```text
RFQ 核价 / 报价
    -> 同客户多 RFQ 零件议价提单
    -> 一次生成一张系统销售订单
    -> 原有提单行进入已提零件
    -> MES 物料号双向回写
    -> U9 同步模块按销售订单整单同步
```

该方案将 RFQ 的报价来源、提单执行、MES 物料号确认和 U9 销售订单传输连接起来，同时保留 RFQ 和销售订单各自的主表边界，避免把两种业务订单混写到同一主流程表。
