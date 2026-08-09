# 36-重复匹配复用 MES 已确认物料号并收窄已提零件 MES 去重开发文档

> 文档版本：V1.0

> 2026-08-09 材料主数据阶段 10 收口：本流程前置重复匹配除复用 MES 已确认物料号外，还沿用 39 号文档的工程材料身份口径。材料编码用于真实材料去重和快照识别，材料类型来自字典，材质分类保存材料类别细分，材料形态保存板材、棒材、管材；MES 物料号仍是外部物料身份，不被材料编码替代。
>
> 文档日期：2026-08-07
>
> 文档状态：开发设计基线。本文描述目标链路和分阶段实施计划，不代表代码已经完成。
>
> 2026-08-08 两级工艺主数据阶段 15 收口：前置重复匹配除复用已确认物料号外，也统一复用标准工艺路线字段；RFQ 重复件核价会按 `processDetailId + unitWorkHours` 结合当前工艺大类小时价格重算，销售订单异常治理和 RFQ 派生销售订单复制历史工艺快照不重算。已提零件 MES 去重仍只负责活动主请求和 follower，不查询历史成功提单行。

## 1. 文档目的

本文档用于统一销售订单自动异常治理匹配、RFQ 核价重复件复用和已提零件 MES 物料号获取之间的职责边界。

本次改造的核心不是删除全部 MES 去重，而是将物料号复用职责前移到订单主表匹配阶段：

```text
历史来源存在 MES 已确认物料号
        |
        v
销售订单自动匹配 / RFQ 核价重复件复用
        |
        v
来源主表沉淀物料号确认信息
        |
        v
提单行复制物料号并标记 MES 成功
        |
        v
已提零件不再重复查询历史成功提单行
```

对于没有前置确认物料号的零件，已提零件仍然需要防止并发请求：

```text
已提零件获取 MES
        |
        v
检查活动中的同图号版本主请求
        |
        +-- 已存在：创建 follower，等待主请求回调
        |
        +-- 不存在：创建新的 MES 主请求
```

## 2. 背景与当前问题

### 2.1 当前销售订单导入链路

当前销售订单导入顺序为：

```text
解析 Excel
    -> 绑定图纸
    -> 插入 sales_order_header / sales_order_item
    -> 自动异常治理匹配
    -> 自动生成 order_delivery_ticket
    -> 进入已提零件
```

销售订单导入时：

- `sales_order_item.material_no` 初始为空。
- `sales_order_item.material_no_status` 初始为 `未确认`。
- 自动异常治理匹配会补充生产核价快照。
- 自动异常治理匹配当前不会复制 `material_no` 及其确认来源字段。

当前实现位置：

```text
quote-backend/src/main/java/com/machining/quote/service/SalesOrderQpoImportService.java
quote-backend/src/main/java/com/machining/quote/service/SalesOrderMatchService.java
```

### 2.2 当前 RFQ 重复件核价链路

RFQ 核价页面首次加载时会自动调用重复件复用逻辑：

```text
读取 RFQ 零件
    -> 按 part_no + version 查询已提单历史 RFQ 零件
    -> 复用材料、工艺、费用和价格快照
    -> 保存当前 RFQ 核价结果
```

当前 RFQ 重复件复用会处理：

- `material_name`，当前为空时补充。
- `surface_treatment`，当前为空时补充。
- `material_data`。
- `engineering_material_name`。
- `material_shape`。
- `process_route`。
- `fee_data`。
- 重量、成本、核价模式、组装费和正式价格。
- 组合件父子关系。

当前 RFQ 重复件复用不会复制：

- `material_no`。
- `material_no_status`。
- `material_no_source`。
- `material_no_source_ticket_id`。
- `material_no_synced_at`。
- `material_no_message`。

因此，即使历史来源 RFQ 零件已经由 MES 确认物料号，当前 RFQ 零件仍然可能保持 `material_no = NULL`、`material_no_status = 未确认`。

当前实现位置：

```text
quote-backend/src/main/java/com/machining/quote/service/EngineerService.java
quote-backend/src/main/java/com/machining/quote/mapper/RepeatQuoteSourceSqlProvider.java
```

### 2.3 当前已提零件 MES 重复查看

`MesMaterialDedupService.resolveBeforeMes(...)` 当前先执行历史成功提单行查询：

```text
查询 order_delivery_ticket
    -> mes_sync_status = SUCCESS
    -> material_no 非空
    -> 相同 part_no + version 或 mes_dedup_key
    -> 找到唯一物料号时直接复用
```

对应实现为 `findReusableMaterial(...)`。这使同一类物料号复用逻辑在两个位置重复存在：

1. 前置订单主表匹配没有复用物料号。
2. 已提零件获取 MES 时再次扫描历史成功提单行。

完成本次改造后，第 2 项历史成功提单行扫描不再存在。

## 3. 目标与范围

### 3.1 本次目标

- 销售订单自动异常治理匹配可以补充历史已确认 MES 物料号。
- RFQ 核价重复件复用可以补充历史已确认 MES 物料号。
- RFQ 和销售订单提单行可以继承来源主表已确认物料号。
- 已确认物料号的提单行直接进入 MES 成功状态，不再重复请求 MES。
- 已提零件不再查询其他成功提单行来复用物料号。
- 没有前置确认物料号的提单行继续使用活动主请求去重和 follower 机制。
- MES 回调成功、fan-out、冲突拒绝和来源主表回写逻辑不回退。

### 3.2 本次不改变的业务规则

- RFQ 核价阶段的重复件复用继续保留。
- 销售订单自动异常治理匹配仍然只补空缺生产快照，不覆盖导入事实。
- `surface_treatment` 和 `drawing_url` 的销售匹配保护规则不变。
- MES 成功仍是新的物料号确认来源。
- WMS 仍使用 `order_delivery_ticket.material_no`。
- 待产、锁库、MES 生产下发和 U9 同步主链路不改变。
- 不新增用户权限，不新增 `common:*` 权限。
- 普通读取、列表查询和状态轮询不新增操作日志。

## 4. 统一物料号确认模型

### 4.1 可复用物料号的判定

来源零件只有同时满足以下条件时，才允许被前置匹配复用：

```text
material_no 非空
material_no_status = 已确认
```

只存在物料号但状态不是 `已确认` 时，不得把它当作真实 MES 物料号传递到后续链路。

### 4.2 需要复制的确认字段

| 字段 | 含义 | 复用规则 |
| --- | --- | --- |
| `material_no` | MES 确认物料号 | 核心字段，按冲突规则处理 |
| `material_no_status` | 确认状态 | 来源为 `已确认` 时同步 |
| `material_no_source` | 确认来源 | 来源有值时补充 |
| `material_no_source_ticket_id` | 原始确认提单行 | 来源有值时补充 |
| `material_no_synced_at` | MES 确认时间 | 来源有值时补充 |
| `material_no_message` | 确认摘要 | 只保留短说明 |

### 4.3 目标字段写入规则

| 目标状态 | 处理方式 |
| --- | --- |
| `material_no` 为空 | 写入来源完整确认信息 |
| 目标物料号与来源相同 | 幂等处理，补齐目标缺失的确认元数据 |
| 目标物料号与来源不同 | 拒绝覆盖，记录物料号冲突，其他字段继续处理 |
| 来源物料号为空 | 不写入物料号字段 |
| 来源状态不是 `已确认` | 不写入确认元组 |

物料号确认字段按一个业务元组处理：不得只写物料号而遗漏状态，也不得只改变状态而没有真实物料号。

## 5. 业务链路设计

### 5.1 销售订单自动异常治理匹配

改造 `SalesOrderMatchService.applyProductionSnapshot(...)`：

1. 按现有 `part_no + version` 查找已提单 RFQ 历史来源。
2. 按现有规则补生产和核价快照。
3. 来源物料号为 `已确认` 时，补销售订单物料号确认元组。
4. 已有不同物料号时不覆盖，并在匹配结果中返回冲突说明。
5. 匹配摘要增加物料号补全、跳过和冲突计数。
6. 不因物料号冲突阻断其他生产快照补全。

销售订单导入流程仍为：

```text
销售订单导入
    -> 自动异常治理匹配
    -> 物料号确认元组补全
    -> 自动整单提单
    -> 提单行继承已确认物料号
```

### 5.2 RFQ 核价重复件复用

改造 `EngineerService.applyRepeatQuotes(...)` 及其普通件、组合件递归路径：

- 普通件复制来源 RFQ 已确认物料号。
- 组合件子件递归复用时复制子件物料号。
- 组合件父件来源已确认时复制父件物料号。
- 来源没有已确认物料号时，仍可继续复用材料、工艺、费用和价格。
- 不修改 `drawing_url`。
- 不改变现有 `material_name` 和 `surface_treatment` 的 RFQ 核价复用规则。

重复件候选查询不增加“必须有已确认物料号”的条件，否则会导致历史零件可以复用核价但不能复用物料号时，整套核价逻辑被错误阻断。

### 5.3 提单行物料号初始化

修改 `DeliveryService` 的 RFQ 和销售订单提单构造：

当来源主表零件满足 `material_no 非空且 material_no_status = 已确认` 时：

```text
ticket.material_no = source.material_no
ticket.mes_sync_status = SUCCESS
ticket.mes_sync_message = 前置匹配复用已确认 MES 物料号
ticket.mes_synced_at = source.material_no_synced_at
```

当来源主表没有已确认物料号时：

```text
ticket.material_no = NULL
ticket.mes_sync_status = PENDING
```

RFQ 提单当前没有复制 `item_quote_version.material_no` 的逻辑，需要补齐。销售订单提单已有物料号复制逻辑，需要补充来源确认状态对应的 MES 状态初始化。

### 5.4 已提零件 MES 获取

调整 `MesMaterialDedupService.resolveBeforeMes(...)`：

删除：

- `findReusableMaterial(...)`。
- `DedupResolveResult.reuse(...)`。
- `completeDedupReuse(...)`。
- 新流程产生 `ROLE_REUSED` 和 `REUSE_SUCCESS`。

保留：

- `findActiveMaster(...)`。
- `createFollower(...)`。
- `followActiveMaster(...)`。
- 活动主请求唯一约束冲突后的 follower 兜底。
- 主请求成功、回调成功后的 fan-out。

新的 `resolveBeforeMes(...)` 只返回两类结果：

```text
FOLLOW_EXISTING_ACTIVE
MASTER_CREATED
```

已确认物料号的提单行在生成时已经标记 `SUCCESS`，不会进入 `syncMaterialNo(...)`。没有确认物料号的行则直接进入活动主请求去重流程，不再扫描历史成功提单行。

## 6. 批量任务和前端展示

### 6.1 后端批量结果

修改 `DeliveryIntegrationBatchService`：

- 删除新流程中的 `REUSE_SUCCESS` 分支。
- 删除 `reusedMaterialCount` 汇总字段。
- `successCount` 只统计本次实际成功或正常完成的 MES 处理。
- 保留 `dedupFollowerCount`、`submittedToMesCount`、`callbackPendingCount`、`failedCount` 和 `skippedCount`。

历史数据库中已经存在的 `REUSED` 记录可以保留用于审计查询，但新流程不得继续产生该状态。

### 6.2 已提零件页面

修改 `DeliveredItemManage.vue`：

- 删除“复用已确认物料号”批量结果标签。
- 删除 `reusedMaterialCount` 读取。
- 保留“关联待回调”和“实际提交 MES”统计。
- follower 行继续隐藏重复提交按钮。
- follower 提示统一为“已关联同图号版本 MES 主请求，等待主请求结果”。
- 已确认且 `mes_sync_status = SUCCESS` 的行不展示获取 MES 操作。

## 7. 后端公共能力设计

建议在现有 `SourceItemMaterialNoSyncService` 基础上抽取物料号确认元组复用方法，或者新增职责清晰的 `ConfirmedMaterialNoReuseService`，提供：

```java
MaterialNoReuseResult copyToRfqItem(
        ItemQuoteVersion target,
        ItemQuoteVersion source
);

MaterialNoReuseResult copyToSalesOrderItem(
        SalesOrderItem target,
        ItemQuoteVersion source
);

boolean isConfirmedMaterialNo(ItemQuoteVersion source);
```

服务只负责字段规则和冲突判断，事务仍由销售匹配、核价和提单服务控制。MES 成功回写继续由 `SourceItemMaterialNoSyncService` 负责，避免把“历史来源复用”和“MES 实时确认”混成一个入口。

## 8. 数据库和索引

本次不新增业务表和物料号字段，继续使用现有物料号确认字段以及 MES 请求、waiter 表。

保留以下并发去重索引：

- `uk_delivery_mes_request_active_dedup`。
- `idx_delivery_mes_request_dedup_status`。
- `idx_delivery_mes_waiter_master_request`。
- `idx_delivery_mes_waiter_dedup`。

删除历史成功提单行复用后，通过 `EXPLAIN` 检查 `idx_delivery_ticket_mes_dedup_key` 是否仍有查询用途。只有确认该索引仅服务已删除的历史复用查询时，才在实际下一编号迁移中删除；不得删除活动主请求和 follower 所需索引。

本次不修改已执行的 `V73`、`V74` 等历史迁移文件。

## 9. 权限与日志

### 9.1 权限

本次不新增权限，继续复用：

- `market:delivered-item:view`。
- `market:delivered-item:mes-sync`。
- `market:sales-match:apply`。
- 现有 RFQ 核价保存权限。
- 现有提单权限。

不得新增 `common:*` 权限。

### 9.2 操作日志

继续记录：

- 销售订单自动匹配补全摘要。
- RFQ 重复件核价复用摘要。
- MES 主请求提交。
- MES follower 关联。
- MES fan-out。
- MES 回调成功或冲突。

普通列表查询、历史候选读取和已提零件状态刷新不新增操作日志。

日志不得包含：

- access token。
- Authorization header。
- client secret。
- 完整外部请求体或响应体。

物料号确认信息只记录业务摘要，不记录完整外部响应。

## 10. 阶段开发计划

### 阶段 1：统一物料号确认复用规则

目标：建立 RFQ、销售订单共用的物料号确认元组复制规则。

修改：

- 抽取或新增物料号确认复用服务。
- 定义来源已确认、目标为空、同值幂等和异值冲突规则。
- 补来源字段全集复制逻辑。
- 不修改 MES 实时回写规则。

验收：

- 来源确认元组可以复制到 RFQ 目标零件。
- 来源确认元组可以复制到销售订单目标零件。
- 不同物料号不能覆盖。
- 来源未确认时不影响原有快照复用。

### 阶段 2：销售订单自动匹配补充物料号

目标：销售订单导入自动匹配完成后，主表具备可复用的 MES 物料号确认信息。

修改：

- 修改 `SalesOrderMatchService.applyProductionSnapshot(...)`。
- 将来源 RFQ 已确认物料号复制到 `sales_order_item`。
- 匹配结果增加物料号补全、跳过和冲突摘要。
- 保持销售订单导入事实字段和已有生产快照不被覆盖。

验收：

- 销售订单命中历史确认来源后，`material_no_status = 已确认`。
- 未命中或来源未确认时，导入仍成功。
- 已有不同物料号时不覆盖。
- 自动异常治理其他字段不回退。

### 阶段 3：RFQ 核价重复件补充物料号

目标：RFQ 重复件核价复用完成后，目标 RFQ 主表沉淀已确认物料号。

修改：

- 修改 `EngineerService.applyRepeatQuotes(...)`。
- 普通件复用复制物料号确认元组。
- 组合件父件和子件递归复制物料号确认元组。
- 不要求候选必须有物料号。
- 补普通件、组合件、无物料号来源和冲突测试。

验收：

- RFQ 普通件重复核价后可以获得已确认物料号。
- RFQ 组合件及子件重复核价后可以获得已确认物料号。
- 来源没有确认物料号时仍可以复用核价结果。
- 核价价格、材料、工艺和费用快照不回退。

### 阶段 4：提单行继承确认物料号

目标：前置匹配得到的确认物料号进入已提零件后，不再显示为待获取 MES。

修改：

- 修改 `DeliveryService` 的 RFQ 提单构造。
- 修改销售订单提单构造的 MES 状态初始化。
- 已确认来源写入 `ticket.material_no` 和 `mes_sync_status = SUCCESS`。
- 未确认来源保持 `PENDING`。
- 补 RFQ、销售订单提单可见性和状态测试。

验收：

- 前置确认物料号进入提单后不再进入 MES 待处理池。
- WMS 可以直接使用提单物料号。
- 未确认物料号仍可正常进入 MES 获取流程。
- 待产、锁库和生产下发链路不受影响。

### 阶段 5：删除已提零件历史成功行复用

目标：已提零件 MES 去重只负责并发主请求和 follower。

修改：

- 删除 `findReusableMaterial(...)` 历史成功行查询。
- 删除 `REUSE_SUCCESS`、`ROLE_REUSED` 新流程分支。
- 保留活动主请求、唯一约束和 follower。
- 更新 `MesMaterialSyncService`、`MesMaterialDedupService` 和批量任务结果。
- 更新已提零件页面，删除历史物料号复用文案和统计。

验收：

- 已提零件获取 MES 不再查询其他成功提单行。
- 没有活动主请求时只创建一个 MES 主请求。
- 并发请求中的后续行成为 follower。
- 主请求回调后 follower 正确 fan-out。
- 新流程不再产生 `REUSE_SUCCESS`。

### 阶段 6：文档、测试与全量回归

目标：前置匹配、提单、MES、WMS 和页面口径完整收口。

修改：

- 更新 05 号重复件匹配文档。
- 更新 18、19 号 MES 物料号和去重文档。
- 更新 32 号 RFQ 主表物料号回写文档。
- 更新 33 号销售订单导入和异常治理文档。
- 更新权限清单和日志运维说明。
- 完成后端、前端和数据库回归测试。

验收：

- 文档不再把已提零件历史成功行复用描述为现行流程。
- 文档明确前置匹配负责已确认物料号复用。
- 文档明确 MES 去重只负责活动主请求和 follower。
- 不新增用户权限。
- 操作日志和文件日志不泄露敏感信息。

## 11. 测试方案

### 11.1 后端测试

- 销售订单自动匹配复制已确认物料号。
- 销售订单匹配物料号同值幂等和异值冲突。
- RFQ 普通件重复核价复制已确认物料号。
- RFQ 组合件及子件复制已确认物料号。
- 来源未确认时核价快照仍可复用。
- RFQ 提单复制物料号并初始化 `SUCCESS`。
- 销售订单提单复制物料号并初始化 `SUCCESS`。
- 已确认提单行不进入 MES 默认待处理池。
- `resolveBeforeMes(...)` 不再查询历史成功提单行。
- 活动主请求仍能创建 follower。
- 活动主请求唯一约束冲突仍转 follower。
- 主请求即时成功、回调成功和 fan-out 不回退。
- MES 冲突回调不覆盖来源主表。
- WMS 仍使用 `order_delivery_ticket.material_no`。

### 11.2 前端测试

- 已提零件页面不展示“复用已确认物料号”批量统计。
- 页面保留 follower 和待回调状态展示。
- follower 行不显示重复提交 MES 操作。
- 已确认成功行不显示获取 MES 操作。
- 现有 `market:delivered-item:mes-sync` 权限仍有效。
- 不新增 `market:delivered-item:mes-dedup` 或其他权限。

### 11.3 全量验收命令

```powershell
cd D:\QPO-Operations-Management-System\quote-backend
.\mvnw.cmd test

cd D:\QPO-Operations-Management-System\quote-frontend
npm test
npm run build
```

## 12. 风险与处理

### 12.1 旧提单历史记录

历史已经存在的 `REUSED` 提单行不删除，只作为历史审计数据保留。新流程不再生成该状态，也不再依赖历史成功提单行完成物料号复用。

### 12.2 前置匹配未命中

未命中历史来源或来源未确认物料号时，提单保持 `PENDING`，继续由 MES 主请求和 follower 机制处理，不得因为本次改造阻断 MES 获取。

### 12.3 物料号冲突

目标主表已有不同物料号时，前置匹配只记录冲突，不覆盖目标物料号。MES 回写继续沿用现有冲突拒绝规则。

### 12.4 数据库索引

只有确认 `idx_delivery_ticket_mes_dedup_key` 仅服务已删除的历史成功行查询后，才允许通过新迁移删除。活动主请求和 waiter 索引必须保留。

## 13. 最终验收标准

### 前置匹配

- 销售订单自动匹配可以补充已确认 MES 物料号。
- RFQ 核价重复件复用可以补充已确认 MES 物料号。
- 物料号、确认状态和来源追溯信息不被拆散写入。
- 已有不同物料号不被覆盖。

### 已提零件和 MES

- 已确认物料号的提单行直接为 `SUCCESS`。
- 已提零件不再查询其他成功提单行来复用物料号。
- 无确认物料号时仍能创建 MES 主请求。
- 并发请求仍只有一个主请求，其余为 follower。
- 主请求成功、回调、fan-out、失败重试和冲突拒绝不回退。

### 下游链路

- WMS 获取库存仍使用提单行物料号。
- 锁库结果、待产、锁库和 MES 生产下发不回退。
- U9 继续使用来源主表确认物料号。
- RFQ 和销售订单主链路均可串通。

### 安全与治理

- 本次不新增权限。
- 普通读取不新增操作日志。
- 操作日志和文件日志不包含 token、Authorization header、client secret、完整外部请求体或响应体。
- 新代码、前端文案和业务文档不再把已提零件历史成功提单行复用作为现行功能。

### 阶段 8 收口补充：RFQ 派生销售订单

RFQ 议价提单生成派生销售订单后，`sales_order_item.source_ticket_id` 是 RFQ ticket 与销售订单零件的精确关联；MES 双写使用此关联更新两边确认物料号。前置匹配仍负责确认物料号复用，已提零件不回到历史成功行查询。

派生销售订单进入现有 U9 页面，未确认物料号或含税单价只阻断 U9，不影响 RFQ 提单、已提零件、WMS、待产和锁库链路。本次不新增权限，操作和文件日志遵循敏感信息脱敏规则。
