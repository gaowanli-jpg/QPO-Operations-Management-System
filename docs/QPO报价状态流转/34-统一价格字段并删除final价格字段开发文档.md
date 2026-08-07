# 34-统一价格字段并删除 final 价格字段开发文档

## 1. 文档目的

本文档用于清理 QPO 中重复的最终价格字段，统一 RFQ 询价零件和销售订单零件的价格数据模型、核价判断、提单校验、U9 同步和前端展示口径。

本次不是简单删除实体属性，而是一次全链路价格字段替换：

```text
旧字段 final_* 与 tax_included_* 并存
        |
        v
历史数据校验与回填 tax_included_*
        |
        v
后端、SQL、DTO、前端和测试统一使用 tax_included_* / untaxed_*
        |
        v
删除数据库中的 final_* 四个字段
```

## 2. 背景与当前问题

### 2.1 当前字段状态

`item_quote_version` 和 `sales_order_item` 目前都存在两套含义重复的最终价格字段：

| 表 | 旧字段 | 正式含税字段 | 现状 |
| --- | --- | --- | --- |
| `item_quote_version` | `final_unit_price`、`final_total_price` | `tax_included_unit_price`、`tax_included_total_price` | 核价保存时两套字段同时写入 |
| `sales_order_item` | `final_unit_price`、`final_total_price` | `tax_included_unit_price`、`tax_included_total_price` | 销售订单导入时 `final_*` 复制含税价格 |

历史原因是：`final_*` 在基线表中先存在，后续为了表达含税 / 未税价格又增加了 `tax_included_*` 和 `untaxed_*`。当前大量流程仍读取 `final_*`，导致同一业务价格需要维护两套字段。

### 2.2 当前代码依赖

`final_*` 当前参与以下业务行为，必须全部替换：

- RFQ 核价保存和组合件核价保存。
- RFQ 已核价 / 已报价状态判断。
- RFQ 议价提单的价格校验。
- 已提单历史零件重复件候选筛选和索引。
- 销售订单导入时的兼容写入。
- 销售订单异常治理预览中的价格字段。
- 销售订单 U9 预览、U9 请求组包和缺价校验。
- 前端核价、订单管理、议价提单、异常治理、U9 同步页面。
- 操作日志字段名称和源码测试断言。

### 2.3 本次目标

完成后，系统只保留以下价格字段作为业务来源：

| 业务含义 | 正式字段 | 说明 |
| --- | --- | --- |
| 零件含税单价 | `tax_included_unit_price` | 含税价格，是 RFQ 报价完成和 U9 可同步价格的主判断字段 |
| 零件含税金额 | `tax_included_total_price` | 含税单价乘数量 |
| 零件未税单价 | `untaxed_unit_price` | 未税价格，可为空 |
| 零件未税金额 | `untaxed_total_price` | 未税单价乘数量，可为空 |

除不可修改的历史迁移和本文档的迁移说明外，`final_unit_price` 和 `final_total_price` 不再存在于数据库结构、实体、DTO、运行时 SQL、前端源码或现行业务口径中。

## 3. 范围与不变项

### 3.1 本次范围

- 删除 `item_quote_version.final_unit_price`。
- 删除 `item_quote_version.final_total_price`。
- 删除 `sales_order_item.final_unit_price`。
- 删除 `sales_order_item.final_total_price`。
- 将 RFQ 和销售订单的所有价格读取统一切换为四个正式价格字段。
- 将 U9 价格口径固定为含税单价。
- 更新数据库迁移、实体、服务、Mapper / SQL provider、DTO、前端、测试和相关文档。

### 3.2 不改变的业务规则

- 核价成本计算、费用明细、税费计算公式不在本次重新设计；只替换结果字段的存储位置和读取来源。
- `base_cost`、`material_data`、`process_route`、`fee_data`、重量和组合件关系不改变。
- 销售订单导入仍允许价格为空。
- 销售订单仍自动补全历史生产快照并自动整单提单。
- 价格为空不阻断销售订单导入、自动补全、自动提单和进入已提零件。
- U9 同步仍要求物料号已确认、订单已提单，并增加严格的含税单价校验。
- RFQ、销售订单、MES、WMS、待产、锁库和 U9 的非价格业务逻辑不改变。
- 不新增用户权限，不新增 `common:*` 权限。
- 不新增普通读取操作日志；已有核价、导入、提单、U9 写操作日志继续保留，但日志字段改用正式价格名称。

## 4. 目标价格模型

### 4.1 RFQ 零件主表

`item_quote_version` 的价格口径为：

| 字段 | 写入时机 | 空值规则 | 下游用途 |
| --- | --- | --- | --- |
| `tax_included_unit_price` | 工程核价完成 | 未核价时为空 | RFQ 核价完成判断、提单校验、报价展示 |
| `tax_included_total_price` | 工程核价完成 | 含税单价和数量完整时计算 | 报价展示、导出 |
| `untaxed_unit_price` | 工程核价完成 | 按现有税费计算结果写入 | 未税展示和对接 |
| `untaxed_total_price` | 工程核价完成 | 未税单价和数量完整时计算 | 未税展示和对接 |

RFQ 是否已核价、是否可以报价和是否可以生成议价提单，统一判断：

```text
tax_included_unit_price IS NOT NULL
```

本次不再使用 `final_unit_price` 作为状态判断或历史候选条件。

### 4.2 销售订单零件主表

`sales_order_item` 的价格口径为：

| 字段 | 导入规则 | 下游用途 |
| --- | --- | --- |
| `tax_included_unit_price` | 从模板 `VAT单价-含税` 读取，可为空 | U9 请求单价和缺价校验 |
| `tax_included_total_price` | 含税单价存在时按数量计算，可为空 | 销售订单管理和 U9 预览 |
| `untaxed_unit_price` | 从模板 `未税单价(CNY)` 读取，可为空 | 未税展示 |
| `untaxed_total_price` | 模板有值时读取，否则按未税单价乘数量计算 | 未税展示 |

销售订单导入不再写入 `final_*`。自动历史匹配不覆盖销售订单导入价格事实；销售订单价格只能来自导入、明确的价格维护功能或后续批准的价格编辑接口。

### 4.3 U9 价格口径

QPO 发送 U9 的请求头继续设置价格含税标志：

```text
isPriceIncludeTax = true
```

因此 U9 明细价格只能取：

```text
sales_order_item.tax_included_unit_price
```

不允许以下降级：

```text
tax_included_unit_price -> untaxed_unit_price
```

规则如下：

- 含税单价有值：允许预览和提交。
- 含税单价为空、未税单价有值：预览标记“含税单价为空”，禁止提交。
- 两种单价都为空：预览标记“含税单价为空”，禁止提交。
- 价格缺失时不调用 U9 或数据中心，不创建 U9 同步任务。
- U9 仍整单同步，不改幂等、失败重试和状态写回逻辑。

## 5. 数据库迁移设计

### 5.1 迁移文件

新增：

```text
quote-backend/src/main/resources/db/migration/V87__remove_final_price_fields.sql
```

当前迁移编号为 `V86`，因此使用 `V87`。不修改已经执行过的 `V1`、`V55`、`V63` 或 `V65` 历史迁移文件。

同时更新：

```text
quote-backend/src/main/resources/schema.sql
```

新建数据库结构中不再创建四个 `final_*` 字段，重复件索引的价格条件改为 `tax_included_unit_price IS NOT NULL`。

### 5.2 迁移执行顺序

迁移必须在同一事务中完成：

1. 检查 `item_quote_version` 的单价冲突。
2. 检查 `item_quote_version` 的金额冲突。
3. 检查 `sales_order_item` 的单价冲突。
4. 检查 `sales_order_item` 的金额冲突。
5. 将 `final_*` 的非空值回填到对应的 `tax_included_*` 空字段。
6. 对含税单价存在、含税金额为空、数量存在的记录按“单价 × 数量”补齐含税金额。
7. 删除旧的重复件索引并按含税单价条件重建。
8. 删除四个 `final_*` 字段。

迁移失败时必须整体回滚，不能出现只回填部分字段或只删除其中一张表字段的状态。

### 5.3 冲突判定

以下条件都视为冲突并终止迁移：

```text
tax_included_unit_price IS NOT NULL
AND final_unit_price IS NOT NULL
AND tax_included_unit_price <> final_unit_price
```

以及：

```text
tax_included_total_price IS NOT NULL
AND final_total_price IS NOT NULL
AND tax_included_total_price <> final_total_price
```

上述检查分别在 `item_quote_version` 和 `sales_order_item` 执行。冲突异常必须包含表名、字段类别和冲突数量，但不把完整业务数据写入文件日志或操作日志。

### 5.4 历史回填规则

回填只在目标含税字段为空时执行：

```text
item_quote_version.tax_included_unit_price <- item_quote_version.final_unit_price
item_quote_version.tax_included_total_price <- item_quote_version.final_total_price
sales_order_item.tax_included_unit_price <- sales_order_item.final_unit_price
sales_order_item.tax_included_total_price <- sales_order_item.final_total_price
```

如果含税金额仍为空，但含税单价和数量都存在，则按数据库金额精度计算：

```text
tax_included_total_price = ROUND(tax_included_unit_price * qty, 2)
```

未税字段不从含税字段反推，不改变现有未税核算结果。

### 5.5 索引替换

现有重复件索引 `idx_item_quote_version_repeat_released_key` 的条件使用了 `final_unit_price`。迁移中先删除该索引，再重建为：

```sql
CREATE INDEX idx_item_quote_version_repeat_released_key
    ON item_quote_version(part_no, version, id DESC)
    WHERE status = '已提单'
      AND tax_included_unit_price IS NOT NULL
      AND material_data IS NOT NULL
      AND process_route IS NOT NULL;
```

索引名称保持不变，避免运维监控和性能检查口径产生额外变化。

### 5.6 破坏性迁移要求

- 迁移前必须完成 PostgreSQL 数据库备份。
- 生产部署时先停止后端实例，避免旧版本代码在删除字段后继续查询 `final_*`。
- 执行迁移成功后再启动新版本后端。
- 不提供“新代码继续兼容旧字段”的运行时降级。
- 回滚依赖数据库备份和对应旧版本应用，不通过新增回滚 SQL 恢复字段。

## 6. 后端改造清单

### 6.1 实体与 DTO

删除以下实体字段：

```text
ItemQuoteVersion.finalUnitPrice
ItemQuoteVersion.finalTotalPrice
SalesOrderItem.finalUnitPrice
SalesOrderItem.finalTotalPrice
```

同步修改以下 DTO：

- `SalesOrderMatchItemRow` 删除 `finalUnitPrice`，保留 `taxIncludedUnitPrice`。
- `SalesOrderU9SyncPreviewLine` 删除 `finalUnitPrice`，使用 `taxIncludedUnitPrice`。
- 其他对外返回对象不再暴露 `finalUnitPrice` / `finalTotalPrice`。
- 需要展示含税金额的接口统一返回 `taxIncludedTotalPrice`。

字段命名规则：

```text
taxIncludedUnitPrice
taxIncludedTotalPrice
untaxedUnitPrice
untaxedTotalPrice
```

### 6.2 RFQ 核价

修改：

```text
quote-backend/src/main/java/com/machining/quote/service/EngineerService.java
```

普通件和组合件核价保存均改为：

```text
item.setTaxIncludedUnitPrice(taxIncludedUnitPrice)
item.setTaxIncludedTotalPrice(taxIncludedTotalPrice)
item.setUntaxedUnitPrice(untaxedUnitPrice)
item.setUntaxedTotalPrice(untaxedTotalPrice)
```

删除 `setFinalUnitPrice` 和 `setFinalTotalPrice`。局部变量和日志字段由 `finalUnitPrice` 改为 `taxIncludedUnitPrice`，避免代码和日志继续传播旧语义。

RFQ 状态计算和提单校验统一替换为含税单价：

```text
item.getTaxIncludedUnitPrice() != null
```

### 6.3 RFQ 历史重复件

修改：

```text
quote-backend/src/main/java/com/machining/quote/mapper/RepeatQuoteSourceSqlProvider.java
quote-backend/src/main/java/com/machining/quote/service/OrderManagementService.java
quote-backend/src/main/java/com/machining/quote/service/OrderItemPageService.java
quote-backend/src/main/java/com/machining/quote/service/InternalTargetLeadTimeService.java
```

所有以下 SQL 条件由：

```sql
final_unit_price IS NOT NULL
```

替换为：

```sql
tax_included_unit_price IS NOT NULL
```

返回对象中的 `finalUnitPrice` / `finalTotalPrice` 改为正式字段名称。历史候选的“完整核价结果”定义改为：

```text
tax_included_unit_price
material_data
process_route
```

### 6.4 RFQ 提单

修改：

```text
quote-backend/src/main/java/com/machining/quote/service/DeliveryService.java
```

RFQ 手动提单和状态刷新均只读取 `taxIncludedUnitPrice`。价格为空时仍按既有规则禁止 RFQ 议价提单；销售订单自动提单的价格豁免规则不改变。

### 6.5 销售订单导入

修改：

```text
quote-backend/src/main/java/com/machining/quote/service/SalesOrderQpoImportService.java
```

导入时只写四个正式价格字段：

```text
item.taxIncludedUnitPrice = line.taxIncludedUnitPrice
item.taxIncludedTotalPrice = line.taxIncludedTotalPrice
item.untaxedUnitPrice = line.untaxedUnitPrice
item.untaxedTotalPrice = line.untaxedTotalPrice
```

删除导入时对 `final_*` 的赋值。价格为空仍不阻断导入、自动补全、自动提单和已提零件可见性。

### 6.6 销售匹配 / 异常治理

修改：

```text
quote-backend/src/main/java/com/machining/quote/service/SalesOrderMatchService.java
```

销售匹配只补生产快照，不覆盖销售订单导入价格事实。预览和保护字段摘要使用 `taxIncludedUnitPrice`，删除 `finalUnitPrice` 字段及旧覆盖语义。

历史 RFQ 快照补全仍只写空缺的生产快照，不修改销售订单的：

```text
taxIncludedUnitPrice
taxIncludedTotalPrice
untaxedUnitPrice
untaxedTotalPrice
```

### 6.7 U9 同步

修改：

```text
quote-backend/src/main/java/com/machining/quote/service/SalesOrderU9SyncService.java
quote-backend/src/main/java/com/machining/quote/dto/SalesOrderU9SyncPreviewLine.java
```

删除 `preferredPrice()` 的多字段降级逻辑，改为：

```java
private BigDecimal requiredTaxIncludedPrice(SalesOrderItem item) {
    BigDecimal price = item.getTaxIncludedUnitPrice();
    if (price == null) {
        throw new RuntimeException("销售订单零件 " + item.getPartNo()
                + " 含税单价不能为空，不能同步 U9");
    }
    return price;
}
```

预览阶段使用同一判断，返回：

```text
blockedReason = 含税单价为空
```

不允许预览显示可同步后在提交时使用未税价。U9 请求中的 `OrderPriceTC` 和 `FinallyPriceTC` 都使用含税单价。

### 6.8 其他后端引用

逐项替换以下文件中的旧字段引用：

```text
quote-backend/src/main/java/com/machining/quote/service/QuoteProcessService.java
quote-backend/src/main/java/com/machining/quote/service/InternalTargetLeadTimeService.java
quote-backend/src/main/java/com/machining/quote/service/OrderManagementService.java
quote-backend/src/main/java/com/machining/quote/service/OrderItemPageService.java
quote-backend/src/main/java/com/machining/quote/mapper/RepeatQuoteSourceSqlProvider.java
quote-backend/src/main/java/com/machining/quote/dto/SalesOrderMatchItemRow.java
quote-backend/src/main/java/com/machining/quote/dto/SalesOrderU9SyncPreviewLine.java
```

任何“已核价 / 已报价 / 可以提单 / 可以同步 U9”的判断都必须使用 `taxIncludedUnitPrice`，不能用 `untaxedUnitPrice` 代替。
### 6.9 日志与审计字段

修改后端日志和前端审计字段摘要中的名称：

```text
finalUnitPrice  -> taxIncludedUnitPrice
finalTotalPrice -> taxIncludedTotalPrice
```

日志只记录价格字段名、是否为空、缺价数量等必要摘要，不记录完整 Excel 行、完整外部请求或响应。普通列表、预览和首页读取不新增操作日志。

## 7. 前端改造清单

### 7.1 核价页面

修改：

```text
quote-frontend/src/components/ItemPricing.vue
```

将原来绑定 `finalUnitPrice` / `finalTotalPrice` 的展示和组合件子件判断改为：

```text
taxIncludedUnitPrice
taxIncludedTotalPrice
```

页面文案使用明确的“含税单价”和“含税金额”，避免继续使用“最终价格”这种含义不清的名称。

### 7.2 业务列表和提单页面

修改：

```text
quote-frontend/src/components/MarketRelease.vue
quote-frontend/src/components/OrderManage.vue
quote-frontend/src/components/QuoteProcess.vue
quote-frontend/src/components/SalesMatch.vue
```

所有核价完成判断、价格展示和保护字段摘要改用正式字段。销售匹配页面不增加价格覆盖入口，仍只执行生产快照空缺补全。

### 7.3 U9 同步页面

修改：

```text
quote-frontend/src/components/SalesOrderU9Sync.vue
```

- 价格列绑定 `taxIncludedUnitPrice`。
- 缺价判断只判断含税单价。
- 含税单价为空时显示“含税单价缺失”。
- 不再显示或判断 `finalUnitPrice`。
- 未税单价和未税金额继续作为辅助信息展示，但不作为 U9 可同步价格。

### 7.4 审计字段文案

修改：

```text
quote-frontend/src/utils/auditLogFormat.js
```

删除 `finalUnitPrice` / `finalTotalPrice` 映射，增加或保留：

```text
taxIncludedUnitPrice: 含税单价
taxIncludedTotalPrice: 含税金额
untaxedUnitPrice: 未税单价
untaxedTotalPrice: 未税金额
```

日志和审计摘要不得因为字段迁移记录完整 Excel 内容、完整外部请求体、token 或 Authorization header。

## 8. 文档同步清单

更新以下文档中的旧字段口径：

```text
docs/QPO报价状态流转/02-零件状态与分批提单不可逆优化开发文档.md
docs/QPO报价状态流转/04-零件核价预览与重复件复用职责拆分开发文档.md
docs/QPO报价状态流转/05-重复件匹配仅使用已提单历史零件开发文档.md
docs/QPO报价状态流转/10-销售订单直提流程方案讨论文档.md
docs/QPO报价状态流转/13-销售订单导入进入QPO主流程开发文档.md
docs/QPO报价状态流转/14-销售订单独立主表开发文档.md
docs/QPO报价状态流转/15-销售匹配模块开发文档.md
docs/QPO报价状态流转/16-销售订单U9同步模块开发文档.md
docs/QPO报价状态流转/26-销售匹配材料字段口径重构开发文档.md
docs/QPO报价状态流转/33-销售订单导入即提单与异常治理重构开发文档.md
docs/QPO权限用户体系/02-权限点清单与现有角色映射.md
docs/QPO日志模块/03-日志运维说明.md
docs/QPO日志模块/04-主流程文件日志规范化开发文档.md
```

文档统一说明：

- 含税单价是核价完成和 U9 可同步的价格判断字段。
- 未税价格只能用于未税展示和后续明确的未税业务。
- U9 不使用未税单价降级替代含税单价。
- RFQ 和销售订单不再存在 `final_*` 字段。
- 本次不新增权限。
- 正常读取不新增操作日志；既有写操作日志保留但字段名称统一。

## 9. 测试方案

### 9.1 数据库迁移测试

至少覆盖：

1. 两张表旧含税字段为空、`final_*` 有值时，迁移正确回填。
2. 两套字段数值一致时，迁移通过并删除 `final_*`。
3. 单价不一致时，迁移失败且事务回滚。
4. 金额不一致时，迁移失败且事务回滚。
5. 含税单价存在、含税金额为空时，按数量补齐含税金额。
6. 迁移后四个 `final_*` 字段不存在。
7. `idx_item_quote_version_repeat_released_key` 使用 `tax_included_unit_price IS NOT NULL`。

### 9.2 后端测试

补充或更新：

```text
quote-backend/src/test/java/com/machining/quote/LargeOrderItemPagingTest.java
quote-backend/src/test/java/com/machining/quote/SalesOrderQpoImportStage1Test.java
```

覆盖以下行为：

- RFQ 核价写入四个正式价格字段，不写 `final_*`。
- RFQ 未核价、已核价、已报价和议价提单状态判断使用含税单价。
- 历史重复件候选有含税单价时可命中，无含税单价时不可作为完整核价历史。
- 销售订单导入只写四个正式价格字段。
- 销售订单价格为空仍可以导入和自动提单。
- U9 含税单价有值时预览可同步。
- 只有未税单价时，U9 预览阻断并返回“含税单价为空”。
- U9 提交前再次校验含税单价，不能绕过预览直接发送。
- U9 组包的 `OrderPriceTC` 和 `FinallyPriceTC` 等于含税单价。
- 销售匹配不覆盖销售订单四个价格事实字段。
- RFQ、销售订单、MES 物料号回写、WMS、待产、锁库和 U9 主链路均不回退。

### 9.3 前端测试

更新现有测试并增加断言：

- 核价页面不存在 `finalUnitPrice` / `finalTotalPrice`。
- 核价页面绑定 `taxIncludedUnitPrice` / `taxIncludedTotalPrice`。
- U9 页面显示含税单价并使用含税单价判断缺价。
- U9 页面源码不存在未税价格降级为可同步价格的逻辑。
- 销售订单管理、议价提单、异常治理页面不引用旧价格字段。
- 审计字段映射使用正式价格字段。

### 9.4 全量回归

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

回归还必须执行全仓库旧字段扫描：

```powershell
rg -n "final_unit_price|final_total_price|finalUnitPrice|finalTotalPrice" `
  quote-backend quote-frontend docs
```

结果只允许出现在本开发文档的迁移说明、历史变更记录或测试中的“禁止旧字段”断言中，不能出现在业务 SQL、实体、DTO、服务和前端运行时代码中。

## 10. 部署与验收流程

### 10.1 部署前

- 确认当前后端和前端版本已包含所有代码替换。
- 执行数据库全量备份，并验证备份可读取。
- 执行只读冲突统计，确认两张表单价和金额冲突数量为 0。
- 确认不存在仍依赖 `final_*` 的自定义 SQL、报表或运维脚本。
- 通知使用方在迁移窗口内暂停核价、提单、导入和 U9 操作。

### 10.2 部署中

1. 停止后端服务。
2. 执行 Flyway `V87__remove_final_price_fields.sql`。
3. 检查迁移成功、四个字段已删除、重复件索引已重建。
4. 启动新版本后端。
5. 检查 `/actuator/health` 和登录接口。
6. 检查 RFQ 列表、核价页面、议价提单、销售订单列表和 U9 预览。

### 10.3 上线验收

- RFQ 核价后含税单价、含税金额、未税单价、未税金额正确保存。
- RFQ 已核价和提单流程不再依赖 `final_*`。
- 销售订单导入价格为空仍可进入已提零件。
- 销售订单 U9 只有含税单价时才允许同步。
- 只有未税单价时明确阻断，不调用 U9 或数据中心。
- 物料号、MES 回调、WMS、待产和锁库逻辑不受影响。
- 操作日志和文件日志不出现旧字段、token、Authorization header 或完整外部请求体。
- 全仓库运行时代码不再包含 `final_*` 引用。

## 11. 风险与回滚

### 11.1 主要风险

- 旧版本后端在字段删除后启动会因 SQL 或实体映射失败。
- 未发现的报表、脚本或外部查询仍可能引用旧字段。
- 历史 `final_*` 与含税字段存在差异时，错误回填会改变价格事实。
- 某些旧接口仍返回 `finalUnitPrice`，会造成前端字段断裂。
- U9 只认含税单价后，只有未税价格的订单会从“可预览”变为“阻断”。

### 11.2 处理原则

- 冲突数据不自动覆盖，迁移直接失败。
- 迁移前备份是强制条件。
- 新版本不保留旧字段运行时兼容分支。
- U9 含税价格缺失必须前置阻断，不允许用未税价伪装含税价。
- 发现自定义 SQL 依赖时，先修改调用方并回归，再执行删除字段迁移。

### 11.3 回滚

本次数据库变更为破坏性变更，不提供普通应用层回滚。若上线后发现严重问题：

1. 停止新版本后端。
2. 保留迁移日志和错误信息。
3. 使用部署前 PostgreSQL 备份恢复数据库。
4. 恢复对应旧版本后端和前端。
5. 重新执行 RFQ 核价、销售订单导入、U9 预览和主链路验证。

## 12. 阶段划分

### 阶段 1：价格模型和迁移预检

目标：完成数据库层面的安全切换准备，保证删除字段前历史价格不丢失、不被错误覆盖。

修改：

- 新增 `V87__remove_final_price_fields.sql`。
- 在 `item_quote_version` 和 `sales_order_item` 上执行单价、金额冲突检查。
- 回填缺失的 `tax_included_unit_price`、`tax_included_total_price`。
- 按数量补齐缺失的含税金额。
- 将重复件索引条件切换到 `tax_included_unit_price`。
- 更新 `schema.sql`，新结构不再创建 `final_*`。
- 增加迁移验证 SQL 或集成测试，确认字段删除和索引定义正确。

验收：

- 一致历史数据迁移成功。
- 单价或金额冲突时迁移失败并回滚。
- 四个旧字段删除后，四个正式价格字段值完整。
- 数据库没有仍引用 `final_*` 的重复件索引。

### 阶段 2：后端价格来源替换

目标：让所有后端业务在数据库删除旧字段前就完成代码替换。

修改：

- 删除 `ItemQuoteVersion` 和 `SalesOrderItem` 的 `final*` 属性。
- 更新 `EngineerService` 的普通件和组合件核价保存。
- 更新 RFQ 状态计算、议价提单和重复件候选 SQL。
- 更新销售订单导入、自动提单和异常治理预览。
- 更新 `QuoteProcessService`、`OrderManagementService`、`OrderItemPageService`、`InternalTargetLeadTimeService` 和相关 DTO。
- 对外字段统一为 `taxIncludedUnitPrice`、`taxIncludedTotalPrice`、`untaxedUnitPrice`、`untaxedTotalPrice`。

验收：

- 后端运行时代码不再调用 `getFinal*` / `setFinal*`。
- RFQ 已核价、可提单和历史候选判断均使用含税单价。
- 销售订单导入不写旧字段，价格为空仍可自动提单。
- 后端在新数据库结构上能编译并启动。

### 阶段 3：U9 含税价格强校验

目标：消除未税价格被误当作含税价格发送到 U9 的风险。

修改：

- 删除 `SalesOrderU9SyncService.preferredPrice()` 多字段降级。
- 预览和提交统一只读取 `taxIncludedUnitPrice`。
- `OrderPriceTC` 和 `FinallyPriceTC` 统一组包含税单价。
- 含税单价为空时返回“含税单价为空”，不调用数据中心或 U9。
- 更新 U9 DTO、测试和对接文档。

验收：

- 只有未税单价的行不能预览通过。
- 预览通过后提交仍会再次校验含税单价。
- U9 外部请求价格与销售订单含税单价一致。
- U9 幂等、整单同步、失败重试和状态回写不回退。

### 阶段 4：前端和审计字段收口

目标：页面和审计口径与后端正式字段一致。

修改：

- 更新 `ItemPricing.vue`、`MarketRelease.vue`、`OrderManage.vue`、`QuoteProcess.vue`、`SalesMatch.vue` 和 `SalesOrderU9Sync.vue`。
- 将“最终单价 / 最终金额”改为含税价格字段展示。
- 删除前端 `finalUnitPrice` / `finalTotalPrice` 运行时引用。
- 更新 `auditLogFormat.js` 和相关源码测试。

验收：

- 前端页面不再依赖旧字段。
- U9 页面明确展示含税单价缺失状态。
- 核价、提单、异常治理页面的价格展示不出现空列或字段断裂。
- 无权限和只读页面行为不受影响。

### 阶段 5：文档与全量回归

目标：价格口径、部署口径、日志口径和全链路回归完整收口。

修改：

- 更新第 02、04、05、10、13、14、15、16、26、33 号报价状态文档。
- 更新权限清单，说明本次不新增权限、权限 code 和数据范围均不变化。
- 更新日志运维说明和主流程文件日志规范，统一正式价格字段名称。
- 明确正常读取不记操作日志。
- 补齐数据库迁移、RFQ、销售订单、U9 和前端测试。
- 执行全仓库旧字段扫描和新数据库启动验证。

验收：

- 后端测试通过，前端测试和构建通过。
- 文档不再把 `final_*` 描述为现行字段。
- 运行时代码、业务 SQL、实体、DTO 和前端组件不引用旧字段。
- RFQ 核价 / 提单、销售订单导入 / 自动提单、MES、WMS、待产、锁库和 U9 主链路可串通。

## 13. 最终验收标准

### 数据库

- `item_quote_version` 不存在 `final_unit_price`、`final_total_price`。
- `sales_order_item` 不存在 `final_unit_price`、`final_total_price`。
- 四个正式价格字段历史数据已完成回填或保持原有值。
- 重复件索引使用 `tax_included_unit_price`。

### 业务

- RFQ 核价、报价、议价提单继续正常运行。
- 销售订单导入、自动补全、自动提单、已提零件继续正常运行。
- 销售订单价格为空不阻断导入和自动提单。
- U9 只使用含税单价，含税单价为空时不能预览通过或提交。
- 未税单价不再作为含税价格的降级来源。
- MES 物料号回写、WMS 库存、待产和锁库链路不回退。

### 代码与文档

- 运行时代码、业务 SQL、实体、DTO 和前端组件不再使用 `final_*`。
- 文档不再把 `final_*` 描述为现行价格字段。
- 不新增权限。
- 操作日志和文件日志不记录完整业务请求、外部响应、token 或 Authorization header。
