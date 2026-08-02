# QPO MES 物料号同图号版本去重开发文档

最后更新：2026-08-02

## 1. 背景与结论

当前 **已提零件** 模块已经支持 MES 物料号异步回调：QPO 调 MES 后，如果 MES 能即时返回物料号，则提单行进入 `SUCCESS`；如果 MES 需要人工或长时间处理，则提单行进入 `CALLBACK_PENDING`，后续由 MES 回调 QPO 写回物料号。

新的联调问题是：同一批或不同批次中，可能存在多个已提零件的 **图号 + 版本完全相同**。MES 侧认为这类零件只需要生成一次物料号，不希望 QPO 对每一条相同零件都实际创建 MES 任务或等待 MES 分别回调。

结论：

- 去重键固定为 **图号 + 版本**，不区分客户、订单、图纸文件、内部项目号。
- 同一批量请求内相同 `图号 + 版本` 可以去重。
- 跨批次请求中相同 `图号 + 版本` 也可以去重。
- 并发请求中相同 `图号 + 版本` 也必须通过数据库唯一约束兜底，避免同时打到 MES。
- QPO 对 MES 只发起一次真实请求；其他相同图号版本提单行在 QPO 内部标记为 **待MES回调** 或直接复用已确认物料号。
- MES 仍只需要回调主请求的 `mesRequestId`；QPO 收到回调后负责把物料号分发写回所有等待行。

最终效果：

```text
多个已提零件：partNo=P-1001, version=A
  -> 第 1 行成为主请求，真实调用 MES
  -> MES 返回 CALLBACK_PENDING
  -> 第 1 行显示 待MES回调
  -> 其他相同图号版本行不调用 MES，只关联主请求，也显示 待MES回调
  -> MES 后续只回调第 1 行的 mesRequestId
  -> QPO fan-out 写回所有相同图号版本等待行
```

## 2. 目标

- 避免同一 `图号 + 版本` 重复创建 MES 物料号任务。
- 让用户仍能在每个被选中的已提零件上看到清晰状态：处理中、待MES回调、已获取、失败。
- 支持同一批量任务内去重。
- 支持不同批次、不同时间、不同用户提交时去重。
- 支持并发提交时数据库层面只允许一个活跃主请求。
- MES 回调主请求后，QPO 自动写回所有关联等待行。
- 销售订单来源提单行成功获取物料号后，继续同步回写 `sales_order_item` 物料号确认字段。
- 保持现有 MES 回调接口契约不变，不要求 MES 感知 QPO 内部等待行。
- 不新增用户权限，继续使用 `market:delivered-item:mes-sync`。

## 3. 非目标

- 不改变 U9 同步口径；U9 仍只读取销售订单主表 / 零件表中的已确认物料号。
- 不要求 MES 按客户、订单、图纸或项目号生成不同物料号。
- 不把多个提单行共用同一个 `order_delivery_ticket.mes_request_id`，避免破坏现有唯一索引和旧回调防覆盖逻辑。
- 不让前端承担去重判断；去重必须在后端和数据库层面完成。
- 不新增左侧菜单。
- 不新增 `common:*` 权限。
- 不在日志中记录 MES token、Authorization header、完整请求 JSON、完整响应 JSON、完整回调 JSON 或图纸文件内容。

## 4. 关键概念

### 4.1 去重键

去重键使用：

```text
dedupKey = normalize(part_no) + "|" + normalize(version)
```

规则：

- `part_no` 去除首尾空格。
- `version` 去除首尾空格。
- 空版本统一为 `-` 或空字符串中的一种固定值，建议使用 `-`，避免 `null` / 空串产生不同键。
- 字母大小写是否统一按现有图号版本口径决定；如果现有系统图号大小写不敏感，建议统一转大写。
- 不拼客户、订单、图纸、内部项目号。

示例：

| 图号 | 版本 | dedupKey |
| --- | --- | --- |
| `P-1001` | `A` | `P-1001|A` |
| `P-1001 ` | ` A ` | `P-1001|A` |
| `P-1001` | 空 | `P-1001|-` |

### 4.2 主请求行

主请求行是某个 `dedupKey` 当前唯一真实调用 MES 的提单行。

特征：

- `order_delivery_ticket.mes_request_id` 有值。
- `order_delivery_ticket_mes_request` 存在真实请求流水。
- MES 只需要响应或回调该 `mesRequestId`。
- `mes_request_role = MASTER`。

### 4.3 关联等待行

关联等待行是同一个 `dedupKey` 下，不再真实调用 MES、等待主请求结果的提单行。

特征：

- 不向 MES 发起真实请求。
- 不与主请求共用 `order_delivery_ticket.mes_request_id`。
- 保存 `mes_master_request_id` / `mes_master_ticket_id` 追溯主请求。
- 页面状态仍可显示 `PROCESSING` 或 `CALLBACK_PENDING`。
- `mes_request_role = FOLLOWER`。

### 4.4 已确认复用

如果同一个 `dedupKey` 已经有且只有一个成功物料号，后续相同图号版本提单行不需要再调用 MES，也不需要进入待回调，可直接复用物料号并写为 `SUCCESS`。

这能覆盖跨批次、跨订单、跨天重复提交的场景。

如果历史里同一个 `dedupKey` 已经出现多个不同成功物料号，QPO 不做人工阻断，也不自动选择其中一个复用。系统应将该情况视为“不可安全复用”，直接走正常 MES 请求流程：

```text
同图号版本历史成功物料号 = MAT-888, MAT-999
  -> 不复用历史值
  -> 不提示人工处理
  -> 当前 ticket 创建新的 MASTER 请求
  -> 正常调用 MES 获取本次正式物料号
```

这样既避免把历史不一致物料号自动扩散，也不打断用户流程；最终仍以 MES 本次返回或回调的物料号为准。

## 5. 同批量、跨批次、并发如何覆盖

### 5.1 同一批量请求

批量任务循环处理 ticket 时：

1. 第一条相同 `dedupKey` 没有成功历史，也没有活跃主请求。
2. QPO 创建主请求，真实调用 MES。
3. 如果 MES 返回 `CALLBACK_PENDING`，主请求进入待回调。
4. 批量里的第二条、第三条相同 `dedupKey` 查询到活跃主请求，不再调用 MES。
5. 它们写入等待表，提单行状态显示 `CALLBACK_PENDING`，批量结果计入“关联待回调”。

因此同一批量内只会有一条真实 MES 请求。

### 5.2 跨批次请求

后续另一次批量任务或单条获取物料号时：

- 如果同 `dedupKey` 仍有活跃主请求 `PROCESSING` / `CALLBACK_PENDING`，新提交的提单行直接关联该主请求，不调用 MES。
- 如果同 `dedupKey` 已经有且只有一个成功物料号，新提交的提单行直接复用物料号，不调用 MES。
- 如果同 `dedupKey` 已经有多个不同成功物料号，QPO 不复用、不阻断，新的提单行按正常流程创建主请求并调用 MES。
- 如果同 `dedupKey` 的旧请求失败，且没有可唯一复用的成功物料号，新的提单行可以创建新的主请求并调用 MES。

因此跨批次同样可以去重。

### 5.3 并发请求

如果两个用户或两个后台任务几乎同时提交相同 `dedupKey`：

1. 两个请求都尝试创建活跃主请求。
2. 数据库唯一约束只允许一个请求成为主请求。
3. 另一个请求捕获唯一冲突后，重新查询活跃主请求，转为关联等待行。

因此并发下也不会出现两个真实 MES 任务。

## 6. 数据模型设计

### 6.1 `order_delivery_ticket` 扩展字段

新增迁移，例如：

```text
V74__delivery_ticket_mes_dedup.sql
```

实际编号以实施时下一 Flyway 迁移编号为准；当前仓库最后迁移为 `V73__delivery_ticket_mes_callback.sql`，因此本阶段建议使用 `V74__delivery_ticket_mes_dedup.sql`，若其他会话已占用则顺延。

给 `order_delivery_ticket` 增加以下字段，便于列表、详情和排查：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `mes_dedup_key` | `VARCHAR(220)` | 图号版本去重键。 |
| `mes_request_role` | `VARCHAR(20)` | `MASTER` / `FOLLOWER` / `REUSED`。 |
| `mes_master_ticket_id` | `INTEGER` | 关联等待行指向的主提单行 ID。 |
| `mes_master_request_id` | `VARCHAR(80)` | 关联等待行指向的主请求 ID。 |

建议索引：

```sql
CREATE INDEX IF NOT EXISTS idx_delivery_ticket_mes_dedup_key
    ON order_delivery_ticket(mes_dedup_key, mes_sync_status, id);

CREATE INDEX IF NOT EXISTS idx_delivery_ticket_mes_master_request
    ON order_delivery_ticket(mes_master_request_id, id);
```

注意：

- `mes_request_id` 仍只表示该 ticket 自己真实发起的 MES 请求。
- `FOLLOWER` 行不能写入主请求的 `mes_request_id`，否则会违反现有唯一索引，也会破坏旧回调防覆盖。
- 前端详情需要展示关联主请求时，使用 `mes_master_request_id`。

### 6.2 `order_delivery_ticket_mes_request` 扩展字段

给现有请求流水表增加：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `dedup_key` | `VARCHAR(220)` | 本次请求对应的图号版本去重键。 |
| `request_role` | `VARCHAR(20)` | 固定为 `MASTER`，保留扩展。 |
| `follower_count` | `INTEGER` | 当前关联等待行数量，可异步维护或查询统计。 |

建议约束和索引：

```sql
CREATE INDEX IF NOT EXISTS idx_delivery_mes_request_dedup_status
    ON order_delivery_ticket_mes_request(dedup_key, request_status, id DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_mes_request_active_dedup
    ON order_delivery_ticket_mes_request(dedup_key)
    WHERE request_status IN ('PROCESSING', 'CALLBACK_PENDING');
```

该唯一索引用于保证同一 `dedupKey` 同一时间只有一个活跃主请求。

### 6.3 新增 `order_delivery_ticket_mes_waiter`

新增等待关联表：

```sql
CREATE TABLE IF NOT EXISTS order_delivery_ticket_mes_waiter (
    id BIGSERIAL PRIMARY KEY,
    dedup_key VARCHAR(220) NOT NULL,
    master_ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    master_mes_request_id VARCHAR(80) NOT NULL,
    waiter_ticket_id INTEGER NOT NULL REFERENCES order_delivery_ticket(id),
    wait_status VARCHAR(30) NOT NULL DEFAULT 'WAITING',
    material_no VARCHAR(100),
    wait_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_delivery_mes_waiter_active_ticket
    ON order_delivery_ticket_mes_waiter(waiter_ticket_id)
    WHERE wait_status IN ('WAITING', 'CALLBACK_PENDING');

CREATE INDEX IF NOT EXISTS idx_delivery_mes_waiter_master_request
    ON order_delivery_ticket_mes_waiter(master_mes_request_id, wait_status, id);

CREATE INDEX IF NOT EXISTS idx_delivery_mes_waiter_dedup
    ON order_delivery_ticket_mes_waiter(dedup_key, wait_status, id);
```

等待状态：

| 状态 | 含义 |
| --- | --- |
| `WAITING` | 主请求仍在调用 MES，等待主请求结果。 |
| `CALLBACK_PENDING` | 主请求已被 MES 受理，等待 MES 回调。 |
| `SUCCESS` | 已随主请求成功写回物料号。 |
| `FAILED` | 主请求失败，等待行同步失败。 |
| `STALE` | 该等待关系已过期，不再使用。 |
| `IGNORED` | 重复或无效等待关系已忽略。 |

## 7. 后端服务设计

### 7.1 新增 `MesMaterialDedupService`

职责：

- 生成并规范化 `dedupKey`。
- 查询可唯一复用的成功物料号；如果同 `dedupKey` 存在多个不同成功物料号，则返回不可复用并继续正常 MES 请求。
- 查询活跃主请求。
- 创建主请求，处理唯一冲突。
- 创建等待行。
- 主请求成功 / 待回调 / 失败时分发更新等待行。

建议核心方法：

```java
String buildDedupKey(OrderDeliveryTicket ticket);

DedupResolveResult resolveBeforeMes(OrderDeliveryTicket ticket, Long taskId);

void markMasterCallbackPending(String masterMesRequestId, String externalTraceId, String message);

void completeMasterSuccess(String masterMesRequestId, String materialNo, String message, String externalTraceId);

void completeMasterFailed(String masterMesRequestId, String message);
```

`DedupResolveResult` 类型：

| 类型 | 含义 | 是否调用 MES |
| --- | --- | --- |
| `MASTER_CREATED` | 当前 ticket 成为主请求 | 是 |
| `FOLLOW_EXISTING_ACTIVE` | 已关联活跃主请求 | 否 |
| `REUSE_SUCCESS` | 复用唯一历史成功物料号 | 否 |

### 7.2 单条获取物料号流程

`MesMaterialSyncService.syncMaterialNo(ticketId, taskId)` 调整为：

```text
1. 加载 ticket 并校验基础状态、数据权限、图纸、内部项目号等。
2. 如果 ticket 已 SUCCESS 且 material_no 非空，拒绝重复获取。
3. 如果 ticket 已 PROCESSING / CALLBACK_PENDING，拒绝重复提交。
4. 计算 dedupKey = 图号 + 版本。
5. 调 MesMaterialDedupService.resolveBeforeMes(ticket, taskId)。
6. 如果返回 REUSE_SUCCESS：
   - 直接写 ticket.material_no、mes_sync_status=SUCCESS。
   - 销售订单来源同步写 sales_order_item。
   - system_task 成功完成。
   - 不调用 MES。
7. 如果同 dedupKey 存在多个不同历史成功物料号：
   - 不返回 REUSE_SUCCESS。
   - 不提示人工处理。
   - 继续尝试创建 MASTER_CREATED，按正常流程调用 MES。
8. 如果返回 FOLLOW_EXISTING_ACTIVE：
   - 写 ticket.mes_sync_status = 主请求当前状态对应的 PROCESSING 或 CALLBACK_PENDING。
   - 写 mes_request_role=FOLLOWER、mes_master_request_id、mes_master_ticket_id。
   - system_task 成功完成。
   - 不调用 MES。
9. 如果返回 MASTER_CREATED：
   - 写 ticket.mes_sync_status=PROCESSING、mes_request_role=MASTER、mes_request_id。
   - 调 MES。
   - MES 即时成功：写主请求成功并分发等待行。
   - MES 待回调：写主请求 CALLBACK_PENDING，并把等待行同步改为 CALLBACK_PENDING。
   - MES 失败：写主请求失败，并把等待行同步改为 FAILED。
```

### 7.3 批量获取物料号流程

`DeliveryIntegrationBatchService.syncMesMaterialBatch(...)` 保持后端分页 / 异步任务口径，但批量行结果增加去重维度：

```json
{
  "successCount": 3,
  "failedCount": 1,
  "callbackPendingCount": 2,
  "dedupFollowerCount": 4,
  "reusedMaterialCount": 2,
  "submittedToMesCount": 1
}
```

行结果建议：

| 状态 | 中文 | 说明 |
| --- | --- | --- |
| `SUCCESS` | 已获取 | 即时成功或复用成功物料号。 |
| `CALLBACK_PENDING` | 待MES回调 | 当前行是主请求或关联等待行，等待 MES 回调。 |
| `DEDUP_FOLLOWING` | 已关联待回调 | 可以作为批量结果行状态；落库仍用 `CALLBACK_PENDING`。 |
| `FAILED` | 失败 | 校验失败或主请求失败。 |
| `SKIPPED` | 跳过 | 已成功、处理中或不可重复提交。 |

前端落表仍使用 `mesSyncStatus`，不强制新增状态枚举；`DEDUP_FOLLOWING` 主要用于批量任务结果解释。

## 8. MES 回调分发设计

### 8.1 主请求回调

MES 回调仍按第 18 号文档：

```text
POST /api/integrations/mes/material-callback
```

MES 只需要传：

- `mesRequestId`
- `ticketId`
- `materialNo`
- `externalTraceId`
- `message`

QPO 处理：

1. 按 `mesRequestId` 定位主请求流水。
2. 校验 `ticketId` 与主请求 ticket 匹配。
3. 校验当前主请求仍是该 ticket 的最新请求。
4. 写主请求 ticket 为 `SUCCESS`。
5. 如果主请求来源是销售订单，回写 `sales_order_item` 物料号确认字段。
6. 查询 `order_delivery_ticket_mes_waiter.master_mes_request_id = mesRequestId` 且等待状态为 `WAITING` / `CALLBACK_PENDING` 的行。
7. 批量写回等待行对应的 `order_delivery_ticket.material_no` 和 `mes_sync_status=SUCCESS`。
8. 等待行来源是销售订单时，同步回写对应 `sales_order_item`。
9. 更新等待表为 `SUCCESS`。
10. 记录低噪声日志和操作日志。

### 8.2 主请求即时成功分发

如果 MES 第一次响应就返回物料号，也执行同一套 `completeMasterSuccess(...)` 分发逻辑。

这保证并发中已经挂到主请求下面的等待行不会卡在 `PROCESSING`。

### 8.3 主请求待回调分发

如果 MES 返回 `CALLBACK_PENDING`：

- 主请求 ticket 写 `CALLBACK_PENDING`。
- 主请求流水写 `CALLBACK_PENDING`。
- 所有关联等待行 ticket 从 `PROCESSING` 更新为 `CALLBACK_PENDING`。
- 等待表状态从 `WAITING` 更新为 `CALLBACK_PENDING`。
- 页面显示“待MES回调”，详情提示“已关联同图号版本主请求，未重复发送MES”。

### 8.4 主请求失败分发

如果主请求真实调用 MES 失败：

- 主请求 ticket 写 `FAILED`。
- 主请求流水写 `FAILED`。
- 等待行 ticket 写 `FAILED`。
- 等待表状态写 `FAILED`。
- 用户后续可对这些失败行重试；重试时因为没有活跃主请求，可以创建新的主请求。

### 8.5 冲突回调

如果主请求已经成功写入物料号，后续收到相同 `mesRequestId` 但不同 `materialNo`：

- 不覆盖主请求。
- 不覆盖任何等待行。
- 记录 `DELIVERY_MES_MATERIAL_CALLBACK_CONFLICT`。
- 回调接口返回冲突错误。

## 9. 查询和页面展示

### 9.1 已提零件列表

列表保留现有 `mesSyncStatus`：

| 状态 | 页面标签 |
| --- | --- |
| `PROCESSING` | 处理中 |
| `CALLBACK_PENDING` | 待MES回调 |
| `SUCCESS` | 已获取 |
| `FAILED` | 获取失败 |

新增展示字段：

| 字段 | 展示建议 |
| --- | --- |
| `mesRequestRole` | 主请求 / 关联等待 / 复用成功 |
| `mesMasterTicketId` | 详情抽屉展示 |
| `mesMasterRequestId` | 详情抽屉展示 |
| `mesDedupKey` | 详情抽屉展示，便于排查 |

### 9.2 详情抽屉

当 `mes_request_role = FOLLOWER`：

```text
MES状态：待MES回调
处理方式：已关联同图号版本主请求，未重复发送MES
主提单行：{mesMasterTicketId}
主请求ID：{mesMasterRequestId}
去重键：{mesDedupKey}
```

当 `mes_request_role = REUSED`：

```text
MES状态：已获取
处理方式：复用同图号版本已确认物料号
去重键：{mesDedupKey}
```

### 9.3 操作按钮

- `SUCCESS`：不显示“获取物料号”。
- `PROCESSING`：按钮禁用，显示“处理中”。
- `CALLBACK_PENDING`：按钮禁用，显示“待回调”。
- `FAILED`：显示“重试”。
- `FOLLOWER` 行不允许单独重复提交，必须等待主请求或在失败后重试。

## 10. 权限与数据权限

权限不新增：

| 权限编码 | 用途 |
| --- | --- |
| `market:delivered-item:mes-sync` | 用户单条 / 批量触发获取 MES 物料号。 |

数据权限：

- 用户只能对自己有权限的已提零件提交获取物料号。
- 如果用户提交的 follower 行可以关联到另一个用户发起的主请求，只要 follower 行本身在当前用户数据范围内，就允许关联。
- 页面不需要暴露主请求所属客户 / 订单的敏感业务信息，只展示主 ticketId、主 mesRequestId 和去重说明。
- 回调接口仍使用机器 token，不进入用户权限树。

## 11. 日志与审计

新增或补充操作日志动作：

| 动作 | 模块 | 说明 |
| --- | --- | --- |
| `DELIVERY_MES_MATERIAL_DEDUP_REUSE` | `DELIVERY` | 复用同图号版本已确认物料号。 |
| `DELIVERY_MES_MATERIAL_DEDUP_FOLLOW` | `DELIVERY` | 当前 ticket 关联同图号版本活跃主请求，未调用 MES。 |
| `DELIVERY_MES_MATERIAL_DEDUP_FANOUT` | `DELIVERY` | 主请求成功后，物料号分发写回等待行。 |

文件日志建议：

```text
MES material dedup master created ticketId={} mesRequestId={} dedupKey={}
MES material dedup follow ticketId={} masterTicketId={} masterMesRequestId={} dedupKey={}
MES material dedup reuse ticketId={} materialNo={} dedupKey={}
MES material dedup fanout completed masterTicketId={} mesRequestId={} waiterCount={} materialNo={}
MES material dedup fanout failed masterTicketId={} mesRequestId={} waiterCount={} reason={}
```

日志要求：

- 不记录完整 MES 请求 / 响应 / 回调 JSON。
- 不记录 MES token、回调 token、Authorization header。
- `dedupKey` 可记录，但必须避免带完整图纸内容或文件内容。
- fan-out 失败必须进入错误日志，并带 `mesRequestId`、`masterTicketId`。

## 12. 测试计划

### 12.1 后端单元测试

- `buildDedupKey` 去除空格、空版本归一。
- 同图号不同版本不去重。
- 同版本不同图号不去重。
- 同图号版本不同客户 / 订单 / 图纸 / 内部项目号仍去重。
- 已有唯一成功物料号时，新 ticket 直接复用成功，不调用 MES。
- 已有多个不同成功物料号时，新 ticket 不复用、不阻断，按正常流程调用 MES。
- 存在 `CALLBACK_PENDING` 主请求时，新 ticket 关联等待，不调用 MES。
- 存在 `PROCESSING` 主请求时，新 ticket 关联等待，不调用 MES。
- 主请求即时成功后，等待行全部写回 `SUCCESS`。
- 主请求待回调后，等待行全部写为 `CALLBACK_PENDING`。
- 主请求回调成功后，等待行全部写回 `SUCCESS`。
- 主请求失败后，等待行全部写回 `FAILED`。
- 销售订单来源等待行成功后，同步回写 `sales_order_item` 物料号确认字段。
- 回调冲突不覆盖主请求和等待行。

### 12.2 后端集成测试

- 同一批量任务内两个相同 `图号 + 版本`，MES client 只被调用一次。
- 同一批量任务内三个相同 `图号 + 版本`，一个主请求、两个等待行。
- 跨批次：第一次进入 `CALLBACK_PENDING`，第二次相同 `图号 + 版本` 不调用 MES，直接关联等待。
- 跨批次：第一次回调成功后，第二次相同 `图号 + 版本` 直接复用物料号。
- 并发提交相同 `图号 + 版本`，数据库唯一约束保证只产生一个活跃主请求。
- 失败后重试可产生新的主请求。
- `mvnw.cmd test` 通过。

### 12.3 前端测试

- `FOLLOWER` 行显示 `待MES回调`。
- `FOLLOWER` 行详情展示“已关联同图号版本主请求，未重复发送MES”。
- `REUSED` 行显示 `已获取`。
- `CALLBACK_PENDING` 行按钮禁用，文案 `待回调`。
- 批量结果展示 `关联待回调` 数量。
- 原成功 / 失败 / 处理中按钮逻辑不回退。
- `npm test` 通过。
- `npm run build` 通过。

## 13. 实施阶段

### 阶段 1：数据模型与文档口径

目标：建立同图号版本去重所需的持久化结构。

修改：

- 新增迁移 `V74__delivery_ticket_mes_dedup.sql`；若实施时编号已被其他会话占用，则使用实际下一迁移编号。
- `order_delivery_ticket` 新增 `mes_dedup_key`、`mes_request_role`、`mes_master_ticket_id`、`mes_master_request_id`。
- `order_delivery_ticket_mes_request` 新增 `dedup_key`、`request_role`、`follower_count`。
- 新增 `order_delivery_ticket_mes_waiter`。
- 新增同 `dedupKey` 活跃主请求唯一索引。
- 更新实体、Mapper、DTO。
- 更新第 18 号 MES 回调文档，补充同图号版本去重引用。

验收：

- 数据库能按 `dedupKey` 查询活跃主请求。
- 数据库能记录 follower 等待关系。
- 同一 `dedupKey` 同时只能存在一个活跃主请求。
- 现有 `mes_request_id` 唯一索引不被破坏。

### 阶段 2：后端去重服务

目标：单条获取物料号时，先进行后端去重判断。

修改：

- 新增 `MesMaterialDedupService`。
- `MesMaterialSyncService.syncMaterialNo(...)` 调用去重服务。
- 有唯一成功物料号时直接复用。
- 有多个不同成功物料号时不复用、不阻断，继续创建主请求并调用 MES。
- 有活跃主请求时创建等待行，不调用 MES。
- 无可复用结果时创建主请求并调用 MES。
- 捕获活跃主请求唯一冲突，转为 follower。
- 写操作日志和低噪声文件日志。

验收：

- 相同 `图号 + 版本` 已有待回调主请求时，后续 ticket 不调用 MES。
- 相同 `图号 + 版本` 已有唯一成功物料号时，后续 ticket 直接成功。
- 相同 `图号 + 版本` 已有多个不同成功物料号时，后续 ticket 不复用、不阻断，正常调用 MES。
- 不同版本仍会创建独立主请求。
- 失败状态可重试。

### 阶段 3：批量任务去重与统计

目标：同一批量任务内相同图号版本只打 MES 一次，结果解释清晰。

修改：

- 改造 `DeliveryIntegrationBatchService.syncMesMaterialBatch(...)`。
- 批量结果增加 `dedupFollowerCount`、`reusedMaterialCount`、`submittedToMesCount`。
- 行结果区分 `CALLBACK_PENDING` / `DEDUP_FOLLOWING` / `REUSE_SUCCESS`。
- 批量任务中 follower 不计入失败。

验收：

- 同一批量内两个相同 `图号 + 版本`，MES client 只调用一次。
- follower 行在已提零件列表显示 `待MES回调`。
- 批量任务成功完成，不因 follower 行失败。

### 阶段 4：回调 fan-out

目标：主请求成功后自动写回所有关联等待行。

修改：

- 主请求即时成功时调用 fan-out。
- 主请求回调成功时调用 fan-out。
- 主请求待回调时，将等待行从 `PROCESSING` 更新为 `CALLBACK_PENDING`。
- 主请求失败时，将等待行更新为 `FAILED`。
- 销售订单来源等待行成功后，回写 `sales_order_item`。
- 冲突回调不覆盖等待行。

验收：

- MES 只回调主请求一次，所有等待行都写回物料号。
- 销售订单来源等待行物料号状态为 `已确认`。
- 冲突回调不污染主请求和 follower。
- fan-out 失败进入错误日志并可追踪。

### 阶段 5：前端展示与回归

目标：用户能理解哪些行没有重复发送 MES，但仍在等 MES 回调。

修改：

- `DeliveredItemManage.vue` 详情抽屉展示 `mesRequestRole`、`mesMasterTicketId`、`mesMasterRequestId`、`mesDedupKey`。
- follower 行展示说明：`已关联同图号版本主请求，未重复发送MES`。
- 批量结果展示 `关联待回调`、`复用已确认物料号`、`实际提交MES` 数量。
- 保留现有 `待MES回调` 标签和禁用按钮逻辑。
- 补前端测试。

验收：

- 客户 / 用户能看到每个被选中零件都处于待 MES 回调或已获取状态。
- follower 行不会出现可重复提交按钮。
- 页面不暴露无权限订单详情。
- `npm test`、`npm run build` 通过。

### 阶段 6：文档、运维与全量回归

目标：联调、运维和回归口径完整收口。

修改：

- 更新 `docs/QPO-MES-WMS对接/03-MES物料号同步开发文档.md`。
- 更新 `docs/QPO-MES-WMS对接/06-提供给MES的物料号接口对接说明.md`，说明 MES 仍只回调主 `mesRequestId`。
- 更新 `docs/QPO日志模块/03-日志运维说明.md`。
- 更新 `docs/QPO日志模块/04-主流程文件日志规范化开发文档.md`。
- 权限清单说明不新增用户权限，继续使用 `market:delivered-item:mes-sync`。
- 补全后端和前端回归测试。

验收：

- `mvnw.cmd test` 通过。
- `npm test` 通过。
- `npm run build` 通过。
- 同批量相同 `图号 + 版本` 只打 MES 一次。
- 跨批次待回调时相同 `图号 + 版本` 不再打 MES。
- 跨批次已成功且只有一个成功物料号时，相同 `图号 + 版本` 直接复用物料号。
- 跨批次存在多个不同成功物料号时，相同 `图号 + 版本` 不自动复用，按正常请求调用 MES。
- MES 回调一次可写回所有等待行。
- 销售订单 U9 同步前能看到等待行物料号已确认。

## 14. 完成定义

- QPO 对同一 `图号 + 版本` 同一时间只保留一个活跃 MES 主请求。
- 同批量、跨批次、并发场景都不会重复向 MES 创建相同零件任务。
- 所有关联等待行都能在页面显示 `待MES回调`，用户能理解该行没有重复发送 MES。
- MES 回调主请求后，所有等待行都能自动写回物料号。
- 销售订单来源等待行成功后，`sales_order_item.material_no` 和确认状态同步更新。
- 不新增用户权限。
- 不改变 RFQ / 销售订单提单主链路。
- 不影响 U9 同步读取已确认物料号的口径。
