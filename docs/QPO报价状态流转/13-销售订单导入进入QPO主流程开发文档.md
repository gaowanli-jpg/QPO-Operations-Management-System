# QPO 销售订单导入进入主流程开发文档
最后更新：2026-07-30

> 状态：已废弃，已被新方案取代。2026-07-30 复盘后确认，销售订单不再写入 `order_rfq` / `item_quote_version`，改为独立 `sales_order_header` / `sales_order_item`，导入后自动生成销售订单来源提单。最新实施依据见 `docs/QPO报价状态流转/14-销售订单独立主表开发文档.md`。本文档仅保留历史方案和决策痕迹，不再作为开发执行文档。

> 2026-08-07 价格字段收口：本文中的现行价格说明以 34 号文档为准，正式字段为四个 `tax_included_*` / `untaxed_*` 字段。`final_*` 只可能出现在历史讨论语境，不代表当前数据库或运行时字段；导入、自动提单允许价格为空，U9 同步前必须补含税单价。

## 1. 背景

市场部销售订单场景已经从“导入后立即同步 U9”调整为“两步走”：

1. 第一步：销售订单先进入 QPO 主流程，创建 QPO 订单和零件，订单 / 零件直接进入 `已报价` 状态，可被 `议价提单` 模块选择并生成提单。
2. 第二步：提单后从 MES 获取物料号，再用客户编码、单据类型编码、提单行物料号和销售订单明细同步 U9。

本开发文档只覆盖第一步。第一步不调用 U9，不要求导入时已有 MES 物料号。

## 2. 目标

- 销售订单入口仍放在市场 `新单导入` 页面下，不新增左侧模块。
- 页面选择客户，客户来自客户主数据；QPO 保存客户名称快照，同时保留客户编码供后续 U9 使用。
- 页面支持输入 / 选择单据类型编码，默认 `SO01`，不再要求 Excel 每行写单据类型编码。
- 按市场实际 `销售订单模版.xlsx` 导入，生成 QPO `order_rfq` 和 `item_quote_version`。
- 导入成功后订单状态为 `已报价`，零件状态为 `已报价`，零件价格按未税单价、未税总价、含税单价、含税总价四个口径保存。
- 导入后执行一次历史重复件匹配，按 `图号 + 版本` 补齐材料、工艺、图纸、重量、组合件等生产快照。
- 历史匹配失败不阻断建单，但导入结果必须提示“价格已导入，生产快照未匹配 / 未补齐”。
- 导入成功的订单可在 `议价提单` 模块被选中，并使用销售订单字段预填提单参数。

## 3. 非目标

- 本阶段不调用 U9 `SO_Create`。
- 本阶段不获取 MES 物料号，不提交 WMS。
- 本阶段不新增销售订单左侧菜单。
- 本阶段不让没有可追溯价格的零件强行进入 `已报价`。
- 本阶段不自动创建历史组合件缺失的子件；组合件关系只在当前导入行也能匹配到子件时补齐。

## 4. 当前代码核查结论

### 4.1 订单状态准入

现有 `QuoteStatusWorkflow` 中 `议价提单` 可读取状态为：

- `已报价`
- `部分已提单`
- `已生成提单`

正式生成提单要求订单状态为 `已报价` 或 `部分已提单`。因此销售订单导入只要创建 `order_rfq.status = 已报价`，并保证被选零件有 `tax_included_unit_price`，即可沿用现有提单准入。当前系统页面和报价单导出直接使用 `tax_included_unit_price / tax_included_total_price` 展示含税价，不再维护重复兼容字段。

### 4.2 不能复用现有重复件自动核价方法

现有 `EngineerService.applyRepeatQuotes()` 是给 `未核价` 订单使用的自动核价逻辑，会跳过已经存在 `taxIncludedUnitPrice` 的零件。销售订单导入的零件已经带价格，所以本阶段不能直接调用该方法。

应新增一个“销售订单历史快照补齐”方法，复用现有重复源查询口径，但只补齐生产快照，不覆盖销售订单导入价格。

### 4.3 当前主表字段不足

当前 `order_rfq` 只保存 RFQ 号、客户名称、订单类型、状态、工程默认配置和归属信息；当前 `item_quote_version` 已有图号、版本、数量、描述、材料、表面处理、交期、工艺、报价和图纸等字段。

市场实际销售订单模板中的客户订单号、客户项目号、内部项目号、客户下单抬头、销售料号、未税单价、未税总价、含税单价、含税总价、已出数量、未出数量等字段当前没有完整承载位置。由于这些字段会在 `议价提单` 生成前作为默认参数和提单行快照使用，本阶段优先补齐主表字段。

### 4.4 价格追溯口径

本阶段借销售订单导入补齐价格字段时，同时完善原零件核价流程的价格追溯能力。

| 字段口径 | 新字段 | 说明 |
| --- | --- | --- |
| 未税单价 | `untaxed_unit_price` | 税费前单件价格 |
| 未税总价 | `untaxed_total_price` | 税费前金额，通常为未税单价 × 数量 |
| 含税单价 | `tax_included_unit_price` | 含税单件价格 |
| 含税总价 | `tax_included_total_price` | 含税金额，通常为含税单价 × 数量 |
| 含税单价 | `tax_included_unit_price` | 现有页面、提单准入和报价单导出的正式含税单价 |
| 含税金额 | `tax_included_total_price` | 现有页面和报价单导出的正式含税金额 |

原零件核价流程也应写入这四个新字段：如果费用明细中能识别税费行，则未税价为税费前金额，含税价为当前最终价；如果当前费用配置没有税费行，则未税价和含税价可相同。这样历史零件、销售订单零件、报价单导出和后续 U9 同步都能追溯完整价格来源。

## 5. 模板字段映射

销售订单导入以市场实际模板为准，表头如下：

| 模板字段 | QPO 映射 | 必填 | 说明 |
| --- | --- | --- | --- |
| 序号 | `item_quote_version.sales_order_line_no` | 否 | 用于导入结果定位，保留原始序号 |
| 下单日期 | `order_rfq.sales_order_date` | 是 | 同一订单分组内应一致 |
| 客户订单号 | `order_rfq.customer_order_no` / `item_quote_version.customer_order_no` | 是 | 参与订单分组和提单默认值 |
| 客户项目号 | `order_rfq.customer_project_no` / `item_quote_version.customer_project_no` | 否 | 提单默认值 |
| 客户下单抬头 | `order_rfq.customer_order_title` | 否 | 销售订单头快照 |
| 订单类型 | `order_rfq.order_type` / `item_quote_version.order_type` | 是 | 议价提单订单类型默认值 |
| 料号 | `item_quote_version.sales_material_no` | 否 | 销售订单模板料号，不等同于 MES 提单行 `material_no` |
| 内部项目号 | `order_rfq.internal_project_no` / `item_quote_version.internal_project_no` | 是 | 议价提单必填默认值 |
| 客户交期 | `item_quote_version.customer_delivery_date` | 否 | 客户要求日期 |
| 内部交期（给生产的交期） | `item_quote_version.internal_delivery_date`，并同步展示到 `internal_target_lead_time` | 否 | 生产交期快照 |
| 图号 | `item_quote_version.part_no` | 是 | 重复匹配主键之一 |
| 订单数量 | `item_quote_version.qty` | 是 | 必须大于 0 |
| 已出数量 | `item_quote_version.shipped_qty` | 否 | 默认 0 |
| 未出数量 | `item_quote_version.unshipped_qty` | 否 | 为空时按订单数量 - 已出数量计算 |
| 描述 | `item_quote_version.description` | 否 | 零件描述 |
| 材料 | `item_quote_version.material_name` | 否 | 历史匹配可进一步补 `material_data` |
| 表面处理 | `item_quote_version.surface_treatment` | 否 | 直接作为零件快照 |
| 版本 | `item_quote_version.version` | 是 | 重复匹配主键之一 |
| VAT单价-含税 | `item_quote_version.tax_included_unit_price` | 是 | 含税单价快照，现有提单准入和报价单导出沿用该口径 |
| 未税单价(CNY) | `item_quote_version.untaxed_unit_price` | 是 | 未税单价快照 |
| （未税）金额 | `item_quote_version.untaxed_total_price` | 否 | 为空时按未税单价 × 数量计算 |
| 备注 | `item_quote_version.remark` | 否 | 原样保存 |

## 6. 主表字段补齐

### 6.1 `order_rfq` 新增字段

建议新增迁移 `V63__sales_order_qpo_import_fields.sql`。实施前需先检查当前最新迁移号，避免与其他会话冲突。

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `source_type` | `VARCHAR(30) NOT NULL DEFAULT 'RFQ'` | 区分 `RFQ` / `SALES_ORDER` |
| `customer_code` | `VARCHAR(200)` | 客户主数据编码，后续 U9 `OrderBy` 来源 |
| `customer_short_name` | `VARCHAR(200)` | 客户简称快照 |
| `sales_document_type_code` | `VARCHAR(50)` | 单据类型编码，默认 `SO01` |
| `sales_order_date` | `DATE` | 下单日期 |
| `customer_order_no` | `VARCHAR(120)` | 客户订单号 |
| `customer_project_no` | `VARCHAR(120)` | 客户项目号 |
| `customer_order_title` | `VARCHAR(200)` | 客户下单抬头 |
| `internal_project_no` | `VARCHAR(120)` | 内部项目号 |
| `sales_order_import_batch_id` | `BIGINT` | 关联导入批次 |
| `sales_order_import_record_id` | `BIGINT` | 关联导入订单记录 |

索引建议：

- `idx_order_rfq_source_status_created`：`source_type, status, created_at`
- `idx_order_rfq_customer_order_no`：`customer_order_no`
- 不新增客户编码筛选索引，当前数据权限和旧页面仍按 `customer_name` 兼容。

### 6.2 `item_quote_version` 新增字段

| 字段 | 类型 | 用途 |
| --- | --- | --- |
| `sales_order_line_no` | `INTEGER` | 模板序号 |
| `customer_order_no` | `VARCHAR(120)` | 行级客户订单号快照 |
| `customer_project_no` | `VARCHAR(120)` | 行级客户项目号快照 |
| `internal_project_no` | `VARCHAR(120)` | 行级内部项目号快照 |
| `sales_material_no` | `VARCHAR(120)` | 模板料号，区别于 MES 物料号 |
| `customer_delivery_date` | `DATE` | 客户交期 |
| `internal_delivery_date` | `DATE` | 内部交期 |
| `tax_included_unit_price` | `NUMERIC(12, 2)` | 含税单价 |
| `untaxed_unit_price` | `NUMERIC(12, 2)` | 未税单价 |
| `tax_included_total_price` | `NUMERIC(12, 2)` | 含税总价 |
| `untaxed_total_price` | `NUMERIC(12, 2)` | 未税总价 |
| `shipped_qty` | `INTEGER DEFAULT 0` | 已出数量 |
| `unshipped_qty` | `INTEGER DEFAULT 0` | 未出数量 |
| `sales_order_import_record_id` | `BIGINT` | 关联导入记录 |

索引建议：

- `idx_item_quote_version_sales_order_record`：`sales_order_import_record_id`
- 不为新增展示字段加筛选索引；本阶段没有按这些字段查询的需求。

### 6.3 导入记录表扩展

现有 `sales_order_import_batch` / `sales_order_import_record` 可继续复用，但当前语义偏 U9。建议扩展为通用销售订单导入记录：

- `sales_order_import_batch.import_mode`：`QPO` / `U9`，本阶段写 `QPO`。
- `sales_order_import_record.rfq_id`
- `sales_order_import_record.rfq_no`
- `sales_order_import_record.qpo_status`
- `sales_order_import_record.matched_item_count`
- `sales_order_import_record.unmatched_item_count`
- `sales_order_import_record.warning_message`

历史 U9 字段保留，不删除，避免破坏已完成阶段。

## 7. 导入分组和校验

### 7.1 页面输入

销售订单页顶部输入：

- 客户：必选，来自客户主数据，保存 `customerCode / customerShortName / customerName` 快照。
- 单据类型编码：默认 `SO01`，可手动输入，后续可接入可维护下拉。
- Excel 文件：按市场实际模板上传。

### 7.2 分组规则

第一版推荐按以下键分组生成 QPO 订单：

```text
客户订单号 + 内部项目号
```

原因：

- 一个客户订单号下可能存在多个内部项目号，混在同一 QPO 订单里会影响议价提单参数默认值。
- `内部项目号` 是提单必填字段，分组后可自然预填。

同一分组内 `下单日期 / 客户项目号 / 客户下单抬头 / 订单类型` 不一致时，整组导入失败并返回明确错误，避免一个 QPO 订单出现多套头信息。

### 7.3 已报价准入校验

每个零件进入 `已报价` 必须满足：

- `图号` 非空。
- `版本` 非空。
- `订单数量` 大于 0。
- `VAT单价-含税` 非空且大于等于 0。
- `未税单价(CNY)` 非空且大于等于 0。
- `未税金额` 为空时可按 `未税单价 × 订单数量` 自动计算。
- `含税总价` 为空时按 `VAT单价-含税 × 订单数量` 自动计算。

历史重复件匹配不上不影响 `已报价` 状态；价格缺失会影响 `已报价` 状态，因此价格缺失行必须拒绝导入。

## 8. 后端设计

### 8.1 新增 / 调整类

建议新增：

- `SalesOrderQpoImportReq`：承载页面客户、单据类型编码和文件。
- `SalesOrderQpoExcelParser`：解析市场实际模板，不再解析 U9 接口型模板。
- `SalesOrderQpoImportService`：创建 QPO 订单 / 零件、写导入记录、调用历史补齐。
- `SalesOrderRepeatEnrichmentService`：复用重复件来源查询，补齐销售订单生产快照。
- `SalesOrderQpoImportResp` / `SalesOrderQpoImportRow`：返回导入结果、RFQ 号、匹配数量、警告。

旧 Excel 直连 U9 导入链路已下线并清理；前端统一调用 `/api/market/sales-orders/import-qpo`。销售订单同步 U9 由独立的销售订单 U9 同步模块承担，避免“导入销售订单”与“同步 U9”语义混淆。

### 8.2 接口设计

```text
POST /api/market/sales-orders/import-qpo
Permission: market:rfq:import-sales-order
Content-Type: multipart/form-data
```

请求参数：

- `customerId`：客户主数据 ID。
- `documentTypeCode`：单据类型编码，空值使用 `SO01`。
- `excelFile`：市场实际销售订单模板。

响应示例：

```json
{
  "success": true,
  "batchNo": "SOQ202607300001",
  "totalOrderCount": 2,
  "successOrderCount": 2,
  "failedOrderCount": 0,
  "rows": [
    {
      "rfqId": 101,
      "rfqNo": "RFQ-20260730-0001",
      "customerOrderNo": "PO-001",
      "internalProjectNo": "GSQ260001",
      "itemCount": 8,
      "matchedItemCount": 6,
      "unmatchedItemCount": 2,
      "status": "SUCCESS",
      "warningMessage": "2 个零件未匹配到历史生产快照"
    }
  ]
}
```

### 8.3 建单写入规则

每个分组生成一个 `order_rfq`：

- `rfq_no` 使用现有 `RfqNumberService.nextRfqNo()`。
- `customer_name` 使用客户主数据 `customerName`，兼容现有筛选和数据权限。
- `customer_code / customer_short_name` 保存快照。
- `status = 已报价`。
- `source_type = SALES_ORDER`。
- `order_type / customer_order_no / customer_project_no / internal_project_no / sales_document_type_code` 从页面和模板写入。
- `created_by / owner_user_id / owner_dept_id` 沿用新单导入归属规则，确保数据权限可追溯。

每一行生成一个 `item_quote_version`：

- `status = 已报价`。
- `untaxed_unit_price = 未税单价(CNY)`。
- `untaxed_total_price = （未税）金额`，为空时按未税单价 × 数量计算。
- `tax_included_unit_price = VAT单价-含税`。
- `tax_included_total_price = VAT单价-含税 × 数量`。
- `tax_included_unit_price` 直接保存模板含税单价。
- `tax_included_total_price` 直接保存模板含税金额或按数量计算的结果。
- `material_name / surface_treatment / description / remark / qty / part_no / version` 按模板写入。
- `order_type / customer_order_no / customer_project_no / internal_project_no` 写行快照。
- `internal_delivery_date` 同步到 `internal_target_lead_time` 的展示值，方便现有页面兼容。

### 8.4 历史补齐规则

匹配键：

```text
图号 + 版本
```

重复源使用“已提单状态或已生成提单行历史零件”口径：

- 历史零件 `status = 已提单`。
- 历史零件有 `tax_included_unit_price`。
- 历史零件有 `material_data`。
- 历史零件有 `process_route`。
- 按最新 `item_quote_version.id DESC` 取一条。

补齐字段：

- `material_data`
- `process_route`
- `fee_data`
- `engineering_material_name`
- `material_gross_weight`
- `part_net_weight`
- `quote_mode`
- `assembly_fee`
- `drawing_url`（仅当前为空时复制）
- `unit`（仅当前为空时复制）

不覆盖字段：

- `tax_included_unit_price`
- `tax_included_total_price`
- `untaxed_unit_price`
- `untaxed_total_price`
- `tax_included_unit_price`
- `tax_included_total_price`
- `qty`
- `customer_order_no`
- `internal_project_no`
- 任何销售订单模板原始字段

组合件处理：

- 历史源为组合件时，复制组合件核价模式和组装费。
- 当前导入行中也存在可匹配的子件时，可按历史关系补齐 `item_assembly_relation`。
- 当前导入缺少历史子件时，不自动创建子件，只记录警告，不阻断导入。

## 9. 议价提单接入

### 9.1 订单选择

`议价提单` 当前已允许选择 `已报价 / 部分已提单 / 已生成提单`。销售订单导入后状态为 `已报价`，因此无需新增状态。

需要确认候选分页接口返回新增字段：

- `sourceType`
- `customerOrderNo`
- `customerProjectNo`
- `internalProjectNo`
- `orderType`
- `salesDocumentTypeCode`
- `customerDeliveryDate`
- `internalDeliveryDate`

### 9.2 提单参数预填

选择销售订单后，`MarketRelease.vue` 应从订单 / 零件销售快照预填：

- 订单类型：`order_rfq.order_type`
- 内部项目号：`order_rfq.internal_project_no`
- 客户订单号：`order_rfq.customer_order_no`
- 客户项目号：`order_rfq.customer_project_no`
- 开工日期：默认仍为当天，可后续按内部交期反推，不在本阶段扩展
- 安排类型：仍默认 `计划`

多订单选择时：

- 如果所有已选订单上述字段完全一致，则自动预填。
- 如果不一致，则不自动覆盖用户当前输入，并提示“多订单字段不一致，请手动确认提单参数”。

生成提单仍调用现有 `generateTicket()`，不新增提单权限。

## 10. 前端设计

### 10.1 新单导入页面

销售订单页签调整为：

- 顶部客户选择器。
- 单据类型编码输入框，默认 `SO01`。
- 下载销售订单模板按钮，模板改为市场实际表头。
- 上传销售订单 Excel。
- 按钮文案改为 `导入销售订单` 或 `创建销售订单`，不再写 `同步 U9`。
- 结果表展示：
  - RFQ号
  - 客户订单号
  - 内部项目号
  - 零件数
  - 历史匹配数
  - 未匹配数
  - 状态
  - 提示信息

### 10.2 权限显示

- 页面入口仍由 `market:rfq:view` 控制。
- 销售订单导入按钮仍由 `market:rfq:import-sales-order` 控制。
- 客户选择仍沿用 `market:rfq:customer-select`。

## 11. 权限与日志

### 11.1 权限

本阶段不新增权限点，继续使用：

| 能力 | 权限 |
| --- | --- |
| 查看新单导入页面 | `market:rfq:view` |
| 销售订单导入 | `market:rfq:import-sales-order` |
| 新单导入客户选择 | `market:rfq:customer-select` |

后端接口必须使用 `@RequirePermission("market:rfq:import-sales-order")`，不能只靠前端隐藏按钮。

### 11.2 操作日志

新增操作日志动作：

- `SALES_ORDER_IMPORT_QPO` -> `导入销售订单进入QPO主流程`

摘要字段：

- `batchNo`
- `sourceFileName`
- `customerCode`
- `customerName`
- `documentTypeCode`
- `totalOrderCount`
- `successOrderCount`
- `failedOrderCount`
- `createdRfqNos`
- `matchedItemCount`
- `unmatchedItemCount`
- `durationMs`

不记录：

- 完整 Excel 内容。
- 完整行明细。
- 图纸文件内容。
- token、Authorization header、Cookie。

### 11.3 文件日志

建议事件名：

```text
Sales order QPO import started batchNo={} customerCode={} sourceFileName={}
Sales order QPO import parsed batchNo={} orderCount={} lineCount={}
Sales order QPO import enriched batchNo={} matchedItemCount={} unmatchedItemCount={}
Sales order QPO import completed batchNo={} successOrderCount={} failedOrderCount={} durationMs={}
Sales order QPO import rejected batchNo={} reason={}
Sales order QPO import failed batchNo={} durationMs={}
```

普通客户列表、模板下载、页面查询不新增业务 INFO。

## 12. 开发阶段

### 阶段 1：字段补齐与文档口径

目标：先让主表能承载市场实际销售订单字段。

修改：

- 新增 `V63__sales_order_qpo_import_fields.sql`，实施前确认迁移号。
- 扩展 `order_rfq` 字段。
- 扩展 `item_quote_version` 字段。
- 扩展 `sales_order_import_batch / record` 的 QPO 导入关联字段。
- 原零件核价保存逻辑统一写入 `untaxed_unit_price / untaxed_total_price / tax_included_unit_price / tax_included_total_price`，不再写重复兼容价格字段。
- 更新 `schema.sql`。
- 更新 `OrderRfq.java` / `ItemQuoteVersion.java` / 销售订单导入记录实体。
- 更新权限清单、日志运维说明、销售订单直提讨论文档。

验收：

- 主表字段能完整承载模板头信息和行信息。
- 原 RFQ 零件完成核价后，四个价格追溯字段同步落库。
- 迁移可重复执行。
- 不改变现有 RFQ 导入、报价、议价提单字段语义；`tax_included_*` 是现行正式含税价格字段。

### 阶段 2：市场实际模板解析

目标：按市场真实模板解析销售订单，而不是 U9 接口型模板。

修改：

- 新增 `SalesOrderQpoExcelParser`。
- 解析 `销售订单模版.xlsx` 实际表头。
- 支持日期、数量、金额格式校验。
- 按 `客户订单号 + 内部项目号` 分组。
- 缺必填字段返回行号和字段名。

验收：

- 模板表头缺失时返回明确错误。
- 多行同组生成一个 QPO 订单解析结果。
- 含税单价或未税单价缺失行不能进入已报价。

### 阶段 3：后端 QPO 建单接口

目标：销售订单导入能创建 QPO 订单和零件。

修改：

- 新增 `/api/market/sales-orders/import-qpo`。
- 新增 `SalesOrderQpoImportService`。
- 创建 `order_rfq.status = 已报价`。
- 创建 `item_quote_version.status = 已报价`。
- 写入未税单价、未税总价、含税单价、含税总价，并同步 `tax_included_*` 为含税价。
- 写入导入批次、记录、RFQ 关联。
- 接入 `SALES_ORDER_IMPORT_QPO` 操作日志。

验收：

- 无 `market:rfq:import-sales-order` 不能调用接口。
- 成功导入返回 `rfqNo`。
- 订单和零件均为 `已报价`。
- 销售订单导入零件的四个价格字段均可追溯，`tax_included_unit_price / tax_included_total_price` 与含税价一致。
- 数据权限按导入客户和归属用户生效。

### 阶段 4：重复匹配补齐生产快照

目标：导入后自动补齐历史材料、工艺和图纸快照。

修改：

- 新增 `SalesOrderRepeatEnrichmentService` 或抽取可复用重复源查询服务。
- 复用 `图号 + 版本` 历史已提单匹配口径。
- 补齐生产快照字段，不覆盖导入价格。
- 记录匹配 / 未匹配 / 部分补齐结果。

验收：

- 匹配成功的零件有 `material_data / process_route`。
- 未匹配的零件仍保留已报价价格。
- 导入结果能看到未匹配数量和警告。

### 阶段 5：前端销售订单导入页面改造

目标：市场可按新流程创建 QPO 销售订单。

修改：

- `MarketDashboard.vue` 销售订单页签新增客户选择。
- 新增单据类型编码输入 / 选择，默认 `SO01`。
- 导入按钮改为 QPO 导入接口。
- 模板下载改为市场实际销售订单表头。
- 结果表展示创建 RFQ 和匹配结果。
- `marketApi.js` 新增 `importSalesOrderToQpo()`。

验收：

- 原询价单导入不受影响。
- 无权限看不到销售订单导入按钮。
- 成功后能看到创建的 RFQ 号和匹配结果。
- 页面文案不再暗示已经同步 U9。

### 阶段 6：议价提单默认值接入与回归

目标：销售订单可直接进入议价提单生成。

修改：

- 候选零件分页接口返回销售订单新增字段。
- 选择单个销售订单时预填提单参数。
- 多订单字段一致时预填，不一致时提示手动确认。
- 补前后端测试。

验收：

- 销售订单导入成功后可在议价提单选择。
- 提单参数能看到销售订单已有内部项目号、客户订单号、客户项目号、订单类型。
- 生成正式提单仍走现有 `market:release:create` 权限和 `DELIVERY_TICKET_GENERATE` 日志。
- `mvnw.cmd test`、`npm test`、`npm run build` 通过。

## 13. 风险与待确认

| 风险 | 处理 |
| --- | --- |
| 市场模板价格列可能为空 | 未税单价为空的行拒绝导入，不能强行置为已报价 |
| 同一客户订单号下头字段不一致 | 按 `客户订单号 + 内部项目号` 分组，并校验组内头字段一致 |
| 历史重复件匹配不上 | 不阻断建单，但导入结果提示生产快照未匹配 |
| 组合件缺少子件 | 不自动创建缺失子件，只补齐可确认字段并提示 |
| 当前 U9 同步代码已存在 | 本阶段前端不调用 U9 同步；后续第二步重新基于提单和 MES 物料号设计 |
| 新字段影响旧流程 | 新字段均允许为空，旧 RFQ 导入不写这些字段 |

## 14. 完成标准

- 销售订单导入按市场实际模板创建 QPO 订单和零件。
- 订单 / 零件状态为 `已报价`。
- 价格字段只写入 `untaxed_unit_price / untaxed_total_price / tax_included_unit_price / tax_included_total_price`，不再同步重复兼容字段。
- 重复匹配补齐生产快照，未匹配有清晰提示。
- 新销售订单导入成功后自动生成正式提单并进入已提零件，不再由议价提单页面手动选择。
- 权限沿用 `market:rfq:import-sales-order`，后端强制校验。
- 导入写操作有审计和低噪声文件日志。
- 直接导入销售订单后续通过独立 U9 同步模块处理；RFQ 议价提单派生销售订单的 U9 口径以 37 号文档为准。

### 阶段 8 收口补充

本文件中的“销售订单导入”仍只描述 `DIRECT_IMPORT` 销售订单来源，不覆盖 RFQ 议价提单派生订单。RFQ 派生销售订单由 37 号文档定义，进入同一销售订单 U9 页面；销售订单直接导入、RFQ 导入和 RFQ 议价提单仍保持主表隔离。

销售订单导入自动补全、自动提单、MES/WMS 和 U9 阻断规则以 33、34、36、37 号文档为准。本次不新增权限，普通读取不记操作日志。
