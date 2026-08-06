# 32-RFQ询价订单物料号确认字段与MES回写开发文档

## 1. 背景

当前已提零件模块获取 MES 物料号后，会把物料号写入 `order_delivery_ticket.material_no`。销售订单来源的提单行还会进一步回写 `sales_order_item.material_no`、`material_no_status`、`material_no_source`、`material_no_source_ticket_id` 等确认字段，因此销售订单 U9 同步可以只读取销售订单主表 / 零件表，判断物料号是否为 MES 获取到的真实物料号。

RFQ / 询价订单来源目前只把 MES 物料号沉淀在已提零件表，`item_quote_version` 没有同等的物料号确认字段。后续 RFQ 也需要接入 U9 时，如果仍实时从 `order_delivery_ticket` 拼装物料号，会和销售订单 U9 的稳定主表口径不一致，也会降低溯源和状态判断清晰度。

本次需求目标是：给 RFQ 询价订单零件主表 `item_quote_version` 增加物料号及确认来源字段，并在已提零件获取 MES 物料号成功后，像销售订单一样回写到来源零件主表。

## 2. 已确认口径

1. 数据库目前没有已获取过物料号的 RFQ 提单行，本次不做历史数据兼容回填。
2. 不按 `图号 + 版本` 批量回写所有 RFQ 历史零件，只回写当前提单行的来源零件：`order_delivery_ticket.source_item_id -> item_quote_version.id`。
3. RFQ 和销售订单统一采用“主表已确认物料号”口径，后续 U9 同步只读取各自订单主表 / 零件表中的已确认物料号。
4. MES 去重逻辑继续按 `图号 + 版本` 控制是否重复调用 MES，但去重结果写回主表时仍必须落到每条 follower / reused / master 对应的来源零件行。
5. 不新增用户权限。已提零件获取 MES 物料号仍使用现有 `market:delivered-item:mes-sync`，RFQ U9 后续如独立成模块再单独设计权限。
6. 不新增高频读日志。主表物料号回写属于 MES 物料号同步写操作的一部分，沿用现有 MES 任务 / 回调 / fan-out 审计和文件日志口径。

## 3. 目标

- `item_quote_version` 能保存 MES 确认物料号和来源追溯。
- RFQ 提单行在 MES 即时成功、MES 回调成功、去重复用成功、fan-out follower 成功时，都能同步回写来源 RFQ 零件。
- 销售订单现有回写逻辑不回退。
- 后续 RFQ 接入 U9 时，可以直接判断 `item_quote_version.material_no_status = '已确认'`。
- 冲突回调不覆盖 RFQ 或销售订单主表已确认的不同物料号。

## 4. 非目标

- 不实现 RFQ U9 同步页面和接口。
- 不迁移历史已成功提单行，因为当前库没有 RFQ 物料号历史数据需要兼容。
- 不改变 MES 请求参数、回调接口、token、数据中心配置和 WMS / 待产 / 锁库流程。
- 不改变 RFQ 导入模板，不要求导入阶段提供物料号。
- 不把销售订单数据写入 `order_rfq` 或 `item_quote_version`。

## 5. 数据模型设计

### 5.1 新增迁移

新增迁移，编号使用实际下一编号，例如：

```text
quote-backend/src/main/resources/db/migration/V86__rfq_item_material_no_confirmation.sql
```

### 5.2 `item_quote_version` 新增字段

```sql
ALTER TABLE item_quote_version
    ADD COLUMN IF NOT EXISTS material_no VARCHAR(120),
    ADD COLUMN IF NOT EXISTS material_no_status VARCHAR(30) NOT NULL DEFAULT '未确认',
    ADD COLUMN IF NOT EXISTS material_no_source VARCHAR(30),
    ADD COLUMN IF NOT EXISTS material_no_source_ticket_id BIGINT,
    ADD COLUMN IF NOT EXISTS material_no_synced_at TIMESTAMP,
    ADD COLUMN IF NOT EXISTS material_no_message TEXT;

UPDATE item_quote_version
SET material_no_status = '未确认'
WHERE material_no_status IS NULL OR BTRIM(material_no_status) = '';
```

字段说明：

| 字段 | 中文含义 | 写入来源 | 说明 |
| --- | --- | --- | --- |
| `material_no` | MES 真实物料号 | MES 物料号同步成功 | 后续 RFQ U9 可作为 `ItemCode` 来源，但必须状态已确认。 |
| `material_no_status` | 物料号确认状态 | MES 物料号同步 | 第一版使用 `未确认`、`已确认`、`获取失败`。本次成功链路写 `已确认`，失败优先留在提单行，不强制写主表失败。 |
| `material_no_source` | 物料号来源 | MES 物料号同步 | 成功写 `MES_TICKET`。后续如接主数据确认可扩展。 |
| `material_no_source_ticket_id` | 来源已提零件行 ID | MES 物料号同步 | 指向 `order_delivery_ticket.id`，用于追溯哪条提单行确认了物料号。 |
| `material_no_synced_at` | 物料号回写时间 | MES 物料号同步 | 成功写当前时间。 |
| `material_no_message` | 简短说明 | MES 返回 / QPO 处理摘要 | 只保存短摘要，不保存完整外部响应。 |

### 5.3 索引

```sql
CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_status
    ON item_quote_version(material_no_status, rfq_id, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_no_id
    ON item_quote_version(material_no, id);

CREATE INDEX IF NOT EXISTS idx_item_quote_version_material_source_ticket
    ON item_quote_version(material_no_source_ticket_id)
    WHERE material_no_source_ticket_id IS NOT NULL;
```

说明：

- `idx_item_quote_version_material_status`：服务后续 RFQ U9 按订单判断物料号是否全部已确认。
- `idx_item_quote_version_material_no_id`：服务物料号展示 / 查询 / 运维排查。
- `idx_item_quote_version_material_source_ticket`：服务从提单行追溯回 RFQ 零件物料号确认来源。

### 5.4 实体字段

更新：

```text
quote-backend/src/main/java/com/machining/quote/entity/ItemQuoteVersion.java
```

新增字段：

```java
private String materialNo;
private String materialNoStatus;
private String materialNoSource;
private Long materialNoSourceTicketId;
private LocalDateTime materialNoSyncedAt;
private String materialNoMessage;
```

需要补 `java.time.LocalDateTime` import。

## 6. 后端服务设计

### 6.1 抽取来源零件物料号回写服务

新增服务：

```text
quote-backend/src/main/java/com/machining/quote/service/SourceItemMaterialNoSyncService.java
```

职责：统一处理 `order_delivery_ticket` 成功获得 MES 物料号后，对来源主表零件进行回写。

建议方法：

```java
public void assertMaterialWritable(OrderDeliveryTicket ticket, String materialNo);

public void syncConfirmedMaterialNo(OrderDeliveryTicket ticket, String materialNo, String message);
```

内部按 `ticket.sourceType` 分支：

| `sourceType` | 来源主表 | 来源行 ID | 回写目标 |
| --- | --- | --- | --- |
| `RFQ` | `item_quote_version` | 优先 `ticket.sourceItemId`，兜底 `ticket.itemId` | `item_quote_version.material_no*` |
| `SALES_ORDER` | `sales_order_item` | `ticket.sourceItemId` | `sales_order_item.material_no*` |

### 6.2 写入规则

RFQ 和销售订单使用同一套保护规则：

1. 来源行不存在：抛出业务异常，不能静默成功。
2. 主表 `material_no` 为空：允许写入 MES 物料号，状态置为 `已确认`。
3. 主表 `material_no` 等于本次 MES 物料号：幂等通过，可刷新来源 ticket 和说明。
4. 主表 `material_no` 不同，且状态为 `已确认`：拒绝覆盖，抛出冲突异常。
5. 主表 `material_no` 不同，即使状态不是 `已确认`：第一版也拒绝覆盖，避免导入值或人工值被 MES 流程无提示覆盖。
6. `materialNo` 入库前去首尾空格，长度超过 120 拒绝。
7. `material_no_message` 最多保留 500 字符。

### 6.3 常量建议

避免散落魔法字符串：

```java
private static final String MATERIAL_CONFIRMED = "已确认";
private static final String MATERIAL_UNCONFIRMED = "未确认";
private static final String MATERIAL_SOURCE_MES_TICKET = "MES_TICKET";
```

如后续已有公共常量类，可改为统一放入公共常量。

### 6.4 事务边界

不要把外部 MES HTTP 调用放进长事务。

建议拆为两个阶段：

1. 调用 MES 前后仍按现有逻辑执行。
2. 拿到成功物料号后，进入短事务完成：
   - 校验来源主表可写。
   - 写 `order_delivery_ticket.material_no` / `mes_sync_status`。
   - 写 `order_delivery_ticket_mes_request`。
   - 写来源主表物料号确认字段。

对于回调和 fan-out，本身已经是入站 DB 写流程，也应保证单条 ticket / waiter 的 ticket 更新和来源主表更新在同一个短事务内完成。

## 7. MES 物料号同步改造

涉及：

```text
quote-backend/src/main/java/com/machining/quote/service/mes/MesMaterialSyncService.java
quote-backend/src/main/java/com/machining/quote/service/mes/MesMaterialDedupService.java
```

### 7.1 `MesMaterialSyncService`

替换现有销售订单专用方法：

```java
assertSalesOrderItemMaterialWritable(...)
syncSalesOrderItemMaterialNo(...)
```

改为调用公共服务：

```java
sourceItemMaterialNoSyncService.assertMaterialWritable(ticket, materialNo);
sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(ticket, materialNo, message);
```

接入路径：

1. MES 即时成功：`markSuccess(...)` 内同步回写来源主表。
2. MES 回调成功：`handleMaterialCallback(...)` 成功路径同步回写来源主表。
3. 去重复用成功：`completeDedupReuse(...)` 同步回写来源主表。

要求：

- 销售订单现有行为保持不变。
- RFQ 来源新增回写 `item_quote_version`。
- 冲突回调仍拒绝覆盖，不污染 ticket 和来源主表。

### 7.2 `MesMaterialDedupService`

替换 fan-out 中的销售订单专用回写：

```java
syncSalesOrderItemMaterialNo(follower, materialNo, message)
```

改为统一回写：

```java
sourceItemMaterialNoSyncService.syncConfirmedMaterialNo(follower, materialNo, message)
```

fan-out 单个 waiter 成功时必须保证：

1. follower ticket 写 `material_no`、`mes_sync_status=SUCCESS`。
2. follower 来源主表写已确认物料号。
3. waiter 写完成状态。

如果来源主表冲突或更新失败：

- 当前 waiter 视为 fan-out 失败。
- 不覆盖来源主表。
- 保持错误日志和 `DELIVERY_MES_MATERIAL_DEDUP_FANOUT` 审计可追踪。

## 8. 前端展示建议

本次核心是后端主表沉淀，前端可做轻量展示，不改变主流程操作。

### 8.1 询价订单 / RFQ 零件详情

若当前询价订单详情或零件列表已有字段展示区域，建议增加：

- `物料号`
- `物料号状态`
- `物料号来源`

展示口径：

| 状态 | 展示 |
| --- | --- |
| `已确认` | 绿色标签，表示 MES 已确认。 |
| `未确认` / 空 | 灰色标签。 |
| `获取失败` | 红色标签；第一版通常不会主动写主表失败，只作为后续扩展兼容。 |

### 8.2 已提零件页面

已提零件页面现有 MES 状态和物料号展示保持不变。必要时在详情抽屉中增加来源主表回写状态，例如：

- 来源类型：RFQ / 销售订单
- 来源零件 ID
- 主表物料号状态

该展示不是本次必须项，优先保证后端链路和测试。

## 9. 文档更新

需要更新：

```text
docs/QPO-MES-WMS对接/03-MES物料号同步开发文档.md
docs/QPO权限用户体系/02-权限点清单与现有角色映射.md
docs/QPO日志模块/03-日志运维说明.md
docs/QPO日志模块/04-主流程文件日志规范化开发文档.md
```

更新要点：

- MES 物料号成功后，不仅写 `order_delivery_ticket.material_no`，还会按来源回写对应主表零件。
- RFQ 来源回写 `item_quote_version.material_no` 和确认字段。
- 销售订单来源继续回写 `sales_order_item.material_no` 和确认字段。
- 后续 U9 同步不得实时从 `order_delivery_ticket.material_no` 组装正式物料号，应优先读取订单主表 / 零件表的已确认物料号。
- 本次不新增权限。
- 不新增普通读日志，回写动作归属 MES 物料号同步主流程日志。

## 10. 测试方案

### 10.1 后端测试

新增或扩展测试：

1. 数据模型测试：
   - 迁移包含 `item_quote_version.material_no`。
   - 迁移包含 `material_no_status`、`material_no_source`、`material_no_source_ticket_id`、`material_no_synced_at`、`material_no_message`。
   - 索引存在。
   - `ItemQuoteVersion` 实体存在对应字段。

2. 即时成功回写测试：
   - RFQ ticket MES 即时成功后，`order_delivery_ticket.material_no` 写入。
   - 对应 `item_quote_version.material_no` 写入。
   - `item_quote_version.material_no_status = 已确认`。
   - `material_no_source = MES_TICKET`。
   - `material_no_source_ticket_id = ticket.id`。

3. 回调成功回写测试：
   - RFQ master ticket 回调成功后，RFQ 来源零件主表已确认。
   - 重复相同物料号回调幂等。
   - 不同物料号冲突回调不覆盖 ticket 和 `item_quote_version`。

4. 去重复用测试：
   - RFQ ticket 复用同图号版本唯一成功物料号时，ticket 成功，`item_quote_version` 同步已确认。

5. fan-out follower 测试：
   - 主请求回调一次，RFQ follower ticket 写回物料号。
   - RFQ follower 对应 `item_quote_version` 写回已确认。
   - 销售订单 follower 现有回写不回退。

6. 冲突保护测试：
   - RFQ 主表已有不同 `material_no` 时，MES 成功链路拒绝覆盖。
   - 销售订单已有不同已确认物料号时，现有冲突保护仍有效。

7. 失败路径测试：
   - MES 失败只更新提单行失败状态，不把 RFQ 主表错误置为已确认。
   - source item 不存在时，任务失败可追踪。

### 10.2 前端测试

如增加 RFQ 页面展示，则补充源码测试：

- RFQ 零件展示包含 `materialNo` / `materialNoStatus`。
- 已提零件页面原有 `待MES回调`、`MES失败`、`获取库存` 逻辑不回退。
- 不新增行内获取按钮。

### 10.3 回归命令

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

## 11. 分阶段实施

### 阶段 1：RFQ 零件物料号确认字段

目标：`item_quote_version` 具备和销售订单一致的物料号确认能力。

修改：

- 新增迁移 `V86__rfq_item_material_no_confirmation.sql`，或实际下一编号。
- `item_quote_version` 新增 `material_no` 和确认来源字段。
- 新增索引：`idx_item_quote_version_material_status`、`idx_item_quote_version_material_no_id`、`idx_item_quote_version_material_source_ticket`。
- 更新 `ItemQuoteVersion` 实体。
- 不做历史回填。

验收：

- 数据库字段存在。
- 默认 `material_no_status = 未确认`。
- 新增索引存在。
- 后端编译通过。

### 阶段 2：来源零件物料号回写服务抽取

目标：统一 RFQ 和销售订单来源主表回写逻辑。

修改：

- 新增 `SourceItemMaterialNoSyncService`。
- 支持 `RFQ -> item_quote_version` 回写。
- 支持 `SALES_ORDER -> sales_order_item` 回写。
- 将销售订单现有回写逻辑迁入公共服务。
- 增加冲突保护和幂等处理。

验收：

- RFQ 来源行空物料号时可写入。
- RFQ 来源行已有相同物料号时幂等。
- RFQ 来源行已有不同物料号时拒绝覆盖。
- 销售订单现有回写测试通过。

### 阶段 3：MES 成功路径接入 RFQ 主表回写

目标：MES 所有成功入口都能回写 RFQ 来源零件。

修改：

- 改造 `MesMaterialSyncService` 即时成功路径。
- 改造 `MesMaterialSyncService` 回调成功路径。
- 改造 `MesMaterialSyncService` 去重复用成功路径。
- 改造 `MesMaterialDedupService` fan-out follower 成功路径。
- 保证冲突回调不覆盖来源主表。

验收：

- RFQ MES 即时成功后，`item_quote_version.material_no_status = 已确认`。
- RFQ MES 回调成功后，`item_quote_version.material_no_status = 已确认`。
- RFQ 去重复用成功后，`item_quote_version.material_no_status = 已确认`。
- RFQ follower fan-out 成功后，`item_quote_version.material_no_status = 已确认`。
- 销售订单来源仍回写 `sales_order_item`。

### 阶段 4：页面展示与文档收口

目标：用户和运维能理解 RFQ 主表已沉淀 MES 确认物料号。

修改：

- 视当前页面结构，在询价订单零件详情 / 列表增加物料号状态展示。
- 更新 MES 物料号同步文档。
- 更新权限清单，说明本次不新增权限。
- 更新日志运维说明和主流程文件日志规范。

验收：

- 用户可看到 RFQ 零件是否已有 MES 已确认物料号。
- 已提零件页面现有功能不回退。
- 文档明确 RFQ 和销售订单均以主表已确认物料号作为后续 U9 判断基础。

### 阶段 5：全量回归

目标：RFQ 主链路、销售订单主链路和 MES/WMS 后续链路均不回退。

验收：

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

额外验收：

- RFQ 提单行获取 MES 物料号后，`order_delivery_ticket` 和 `item_quote_version` 均写入物料号。
- 销售订单提单行获取 MES 物料号后，`order_delivery_ticket` 和 `sales_order_item` 均写入物料号。
- 主请求回调一次可 fan-out 写回所有 RFQ / 销售订单等待行来源主表。
- 冲突回调不覆盖主请求、follower、RFQ 主表或销售订单主表。
- WMS 获取库存仍使用已提零件表中的 `order_delivery_ticket.material_no`，不受主表回写影响。
- 后续待产 / 锁库 / MES 生产下发链路不回退。
- 不新增用户权限。
- 操作日志和文件日志不记录完整外部响应、token 或 Authorization header。

