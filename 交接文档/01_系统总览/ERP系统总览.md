# ERP系统总览

## 1. 文档定位

本文档用于团队交接时快速理解 ERP 系统的业务边界、模块组成、数据流转、权限模型、部署方式和后续维护重点。

本文档只描述系统级信息，不逐项展开每个按钮、字段和输入输出。页面级详细说明见后续文档：

- `02_页面功能说明\ERP页面功能与输入输出说明.md`
- `03_业务流程\ERP核心业务流程.md`
- `04_部署运维\ERP部署说明.md`
- `05_开发交接\ERP开发交接说明.md`

## 2. 系统定位

ERP 系统是面向中型制造企业的订单、报价、工艺、提单和基础资料管理系统。当前系统重点覆盖从市场部接收客户询价，到工程部完成工艺核价，再到市场部生成报价单、议价确认、提单和工艺表的完整前段业务流程。

系统当前主要承担以下职责：

- 管理客户询价单和图纸文件。
- 将客户询价 Excel 明细导入为 ERP 订单和零件明细。
- 自动生成 RFQ 单号。
- 自动匹配 PDF 图纸与 Excel 中的图号。
- 工程部维护材料、工艺、加价费用和非工时费用基础数据。
- 工程部按零件进行材料、工艺路线和成本核价。
- 支持普通零件核价和组合件核价。
- 支持重复订单识别和历史报价复用。
- 市场部基于工程核价结果生成报价单。
- 市场部在议价后生成提单和工艺表。
- 向 APS 系统提供已提单订单和工艺基础数据。
- 管理系统用户、角色、登录、强制改密和报价单模板。
- 提供 Docker Compose 部署、备份、恢复、归档和容量报告基础能力。

## 3. 系统边界

### 3.1 ERP负责的范围

ERP 负责订单与报价源头数据，主要包括：

- 客户询价 Excel 导入。
- 客户 PDF 图纸保存与浏览。
- RFQ 主单、零件明细、核价结果维护。
- 材料、工艺、费用类基础字典维护。
- 工程核价和报价单导出。
- 提单和工艺表生成。
- 历史订单查询和补传图纸。
- 交给 APS 的数据源接口。

### 3.2 ERP不负责的范围

当前 ERP 不负责以下生产排程和仓储执行工作：

- PMC 派工。
- APS 部门中心任务执行。
- 生产任务排产。
- APS 零件库存、材料库存、锁定记录和缺口管理。
- 机台能力管理。

这些由 APS 系统负责。ERP 只在提单完成后向 APS 提供订单任务、工艺路线、材料、图纸链接和交期等数据。

## 4. 技术架构

### 4.1 前端

前端项目目录：

```text
D:\QPO-Operations-Management-System\quote-frontend
```

技术栈：

- Vue 3
- Vue Router
- Element Plus
- Vite
- Axios

前端入口：

- `src/main.js`
- `src/App.vue`
- `src/router/index.js`

生产构建：

```powershell
cd D:\QPO-Operations-Management-System\quote-frontend
npm install
npm run build
```

Docker 部署时，前端由 Nginx 容器提供静态页面，并将 API 请求反向代理到 ERP 后端。

### 4.2 后端

后端项目目录：

```text
D:\QPO-Operations-Management-System\quote-backend
```

技术栈：

- Java 17
- Spring Boot 3.2.5
- Spring Web
- PostgreSQL
- MyBatis-Plus
- Flyway
- EasyExcel
- Spring Security Crypto
- Maven

后端入口：

```text
D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\QuoteSystemApplication.java
```

后端默认端口：

```text
8080
```

健康检查接口：

```text
GET /api/health
```

### 4.3 数据库

数据库：

```text
PostgreSQL 15
```

迁移方式：

```text
Flyway
```

迁移目录：

```text
D:\QPO-Operations-Management-System\quote-backend\src\main\resources\db\migration
```

当前主数据表包括：

| 表名 | 说明 |
|---|---|
| `order_rfq` | RFQ 订单主表，保存 RFQ 号、客户、状态、工程全局默认设定 |
| `item_quote_version` | 零件明细和核价结果，保存图号、版本、数量、材料、工艺路线、费用、最终价格等 |
| `item_assembly_relation` | 组合件父子件关系 |
| `order_delivery_ticket` | 提单行 |
| `order_process_sheet` | 工艺表行 |
| `dict_material` | 材料基础字典 |
| `dict_process` | 工时工艺基础字典 |
| `dict_markup_fee` | 百分比费用类，例如管理费、利润、税费等 |
| `dict_extra_fee_item` | 非工时费用工艺，例如 EDM、热处理、表面处理、QA 等 |
| `erp_user` | ERP 用户账号和权限 |
| `audit_log` | 字典类操作审计日志 |
| `system_task` | 异步任务表，例如工程一键刷新核价、报价单后台导出 |
| `rfq_no_sequence` | RFQ 编号日期序列表 |

旧的仓库库存表已在迁移中移除：

```text
V22__remove_warehouse_inventory.sql
```

这表示 ERP 侧“仓库库存管理”已经不再作为当前 ERP 的业务模块，库存相关能力转移到 APS。

## 5. 角色和权限

当前 ERP 有三类角色：

| 角色 | 说明 | 主要权限 |
|---|---|---|
| `ADMIN` | 管理员 | 拥有全部权限，包含市场、工程、系统管理 |
| `MARKET` | 市场部 | 新单导入、报价单处理、议价提单、订单管理、历史订单 |
| `ENGINEER` | 工程部 | 工艺与核价、材料与工艺管理，部分订单读取能力 |

权限控制由后端注解和拦截器实现：

- 注解：`@RequireRole`
- 拦截器：`AuthInterceptor`
- 登录接口：`/api/auth/login`
- 当前用户接口：`/api/auth/me`
- 修改密码接口：`/api/auth/change-password`

`ADMIN` 被视为超级角色，拥有所有受控接口访问权限。

系统支持：

- JWT 登录。
- 角色路由守卫。
- 首次登录或管理员重置密码后强制改密。
- 登录失败次数记录。
- 锁定时间字段。
- 账号启用和停用。
- 管理员创建、编辑、重置密码和删除非保护账号。

## 6. 前端页面模块

ERP 前端路由以 `src/router/index.js` 为准。

| 路由 | 页面 | 角色 | 组件 |
|---|---|---|---|
| `/login` | 登录页 | 公开 | `LoginView.vue` |
| `/market` | 市场部：新单导入中心 | `MARKET`、`ADMIN` | `MarketDashboard.vue` |
| `/quote-process` | 市场部：报价单处理生成 | `MARKET`、`ADMIN` | `QuoteProcess.vue` |
| `/release` | 市场部：议价与提单 | `MARKET`、`ADMIN` | `MarketRelease.vue` |
| `/engineering` | 工程部：工艺与核价 | `ENGINEER`、`ADMIN` | `GlobalSetup.vue` |
| `/pricing` | 工程部：逐件工艺路线与核价中心 | `ENGINEER`、`ADMIN` | `ItemPricing.vue` |
| `/material-process` | 材料与工艺管理 | `ENGINEER`、`ADMIN` | `MaterialProcessManage.vue` |
| `/orders` | 订单管理 | `MARKET`、`ADMIN` | `OrderManage.vue` |
| `/history-orders` | 历史订单管理 | `MARKET`、`ADMIN` | `HistoryOrderManage.vue` |
| `/users` | 系统管理：用户权限 | `ADMIN` | `UserManage.vue` |
| `/quote-template` | 系统管理：报价单模板 | `ADMIN` | `QuoteTemplateManage.vue` |

左侧菜单当前按“核心业务流”组织，市场部三个页面已归并到“市场部订单处理”主模块下。

## 7. 核心业务流程

### 7.1 市场部导入新询价单

入口页面：

```text
市场部订单处理 -> 新单导入中心
```

主要输入：

- 客户名称或客户代码。
- 客户询价 Excel。
- PDF 图纸文件或图纸文件夹。

后端接口：

```text
POST /api/market/import-rfq
```

系统处理：

1. 自动生成 RFQ 号，格式由 `RfqNumberService` 控制，当前形如 `RFQ-yyyyMMdd-0001`。
2. 读取 Excel 明细，字段来自 `RfqExcelDTO`。
3. 从 PDF 文件名中提取图号，并和 Excel 行中的图号匹配。
4. 保存订单主表 `order_rfq`。
5. 保存零件明细 `item_quote_version`。
6. 将 PDF 图纸保存到订单图纸目录。
7. 返回图纸匹配报告。

导入后状态：

- 订单状态：`待工程部评估`
- 零件状态：`待核价`

导入 Excel 当前识别字段：

| Excel表头 | 后端字段 | 说明 |
|---|---|---|
| 图号 | `partNo` | 零件图号 |
| 物料描述 | `description` | 零件名称或描述 |
| 材料名称 | `materialName` | 客户提供材料描述 |
| 表面处理 | `surfaceTreatment` | 表面处理要求 |
| 版本 | `version` | 零件版本，系统会去空格并转大写 |
| 数量 | `qty` | 询价数量 |
| 单位 | `unit` | 单位 |
| 交货周期 | `leadTime` | 客户询价交期 |
| 备注 | `remark` | 补充说明 |

### 7.2 工程部全局设定

入口页面：

```text
工程部：工艺与核价
```

主要作用：

- 选择待工程评估订单。
- 查看本单零件明细、图纸、缺图状态和重复订单识别结果。
- 配置订单级默认材料、毛坯形态和通用工序。
- 将默认设定保存到 `order_rfq.engineer_config`。
- 进入逐件核价页面。

后端接口：

```text
GET /api/market/summary/{rfqId}
GET /api/engineer/repeat-analysis/{rfqId}
POST /api/engineer/order-config/{rfqId}
POST /api/engineer/apply-repeat-quotes/{rfqId}
```

### 7.3 工程部逐件核价

入口页面：

```text
工程部：逐件工艺路线与核价中心
```

主要作用：

- 按零件逐个核算材料成本、工艺成本、加价费用和最终含税价格。
- 支持普通零件和组合件两种核价模式。
- 支持 PDF 图纸预览。
- 支持材料选择弹窗和工艺选择弹窗。
- 支持一键刷新核价。
- 支持导出工程侧报价单。
- 支持完成整单报价。

后端接口：

```text
POST /api/engineer/submit-quote
POST /api/tasks/engineer/refresh-quotes/{rfqId}
GET /api/tasks/{taskId}
POST /api/engineer/complete-order/{rfqId}
GET /api/engineer/assembly-relations/{parentItemId}
```

普通零件核价规则：

1. 材料成本来自材料字典与毛坯形态。
2. 支持板材、棒材、管材三类形态。
3. 如果提供尺寸，系统按米制尺寸计算体积、重量和材料成本。
4. 工时工艺按 `输入工时 * 对应数量阶梯单价` 计算。
5. 非工时费用工艺按输入金额直接计入成本。
6. 基础成本为材料成本加工艺成本。
7. 百分比费用类按基础成本乘以费率计算。
8. 最终含税单价为基础成本加全部百分比费用。
9. 最终总价为最终含税单价乘以数量。

组合件核价规则：

1. 组合件父件价格来自子件含税单价汇总。
2. 可额外录入组装费。
3. 组装费会参与管理费、利润、税费等百分比费用计算。
4. 组装费不收运输费。
5. 组合件父子关系保存到 `item_assembly_relation`。

核价后：

- 零件状态更新为 `已报价`。
- 材料数据、工艺路线、费用明细、成本、含税单价、含税总价保存到 `item_quote_version`。

整单完成报价后：

- 订单状态更新为 `已报价`。

### 7.4 市场部报价单处理

入口页面：

```text
市场部订单处理 -> 报价单处理生成
```

主要作用：

- 选择已报价 RFQ 订单。
- 查看零件报价明细。
- 调整报价交期。
- 导出报价单。

后端接口：

```text
GET /api/market/quote-process/{rfqId}
POST /api/market/quote-process/lead-time/shift
POST /api/market/quote-process/lead-time/set
POST /api/market/quote-process/lead-time/update
GET /api/market/export/quote/{rfqId}
GET /api/market/export/quote/simple/{rfqId}
GET /api/market/export/quote/template/detail/{rfqId}
GET /api/market/export/quote/template/simple/{rfqId}
```

交期规则：

- 客户询价交期保存在 `leadTime`。
- 市场部报价交期保存在 `quoteLeadTime`。
- 如果 `quoteLeadTime` 为空，报价单导出时沿用客户询价交期。
- 系统支持按天数加减交期、批量设置交期、单行修改交期。
- 交期支持明确日期、Excel 日期序列号和包含数字的周期文本。

### 7.5 市场部议价与提单

入口页面：

```text
市场部订单处理 -> 议价与提单
```

主要作用：

- 选择已报价订单。
- 录入开工日期、安排类型、内部项目号、客户订单号、客户项目号。
- 调整内部目标交期。
- 输入每个零件的通过数量。
- 生成提单行和工艺表行。
- 导出提单表和工艺表。
- 删除提单行或工艺表行。

后端接口：

```text
GET /api/market/internal-target-lead-time/{rfqId}/items
POST /api/market/internal-target-lead-time/shift
POST /api/market/internal-target-lead-time/set
POST /api/market/internal-target-lead-time/update
POST /api/market/generate-ticket
GET /api/market/tickets/{rfqId}
GET /api/market/process-sheets/{rfqId}
DELETE /api/market/tickets/{ticketId}
DELETE /api/market/process-sheets/{sheetId}
GET /api/market/export/tickets/{rfqId}
GET /api/market/export/process-sheets/{rfqId}
```

提单规则：

- 只有已核价零件可以生成提单。
- 提单数量不能超过询价数量。
- 通过数量为空或小于等于 0 的零件不会生成提单行。
- 生成提单时同时生成工艺表行。
- 提单交期按内部目标交期、报价交期、客户交期和开工日期推算。
- 每个提单行保留内部项目号、客户订单号、客户项目号、安排类型等字段。

提单后状态：

- 单个零件可变为 `已部分提单` 或 `已生成提单`。
- 整单可变为 `部分已提单` 或 `已生成提单`。

### 7.6 订单管理和历史订单

入口页面：

```text
订单管理
历史订单管理
```

主要作用：

- 按客户查看订单。
- 按 RFQ 号或状态搜索。
- 查看订单详情。
- 查看零件明细、提单行和工艺表行。
- 查看缺图零件。
- 补传图纸。
- 重置提单和工艺表。
- 删除历史订单。

后端接口：

```text
GET /api/order-management/customers
GET /api/order-management/orders
GET /api/order-management/orders/{rfqId}
POST /api/order-management/orders/{rfqId}/reset-production
POST /api/order-management/orders/{rfqId}/supplement-drawings
DELETE /api/order-management/orders/{rfqId}
```

说明：

- `订单管理` 用于当前订单维护。
- `历史订单管理` 用于按客户查看历史订单和长期数据清理。
- 彻底删除订单会删除订单、零件、提单、工艺表和图纸文件。

## 8. 基础资料管理

入口页面：

```text
材料与工艺管理
```

该页面由工程部或管理员使用，包含四类基础数据。

### 8.1 材料费用

表：

```text
dict_material
```

主要字段：

- 材料名称
- 材质
- 备注
- 板材密度
- 板材单价
- 棒材密度
- 棒材单价
- 管材密度
- 管材单价

用途：

- 工程核价时按材料和毛坯形态计算材料成本。

### 8.2 工艺费用

表：

```text
dict_process
```

主要字段：

- 工序名称
- 类型
- 排序号
- 数量阶梯单价：`1-5`、`5-10`、`10-50`、`50-100`、`100-500`、`500-1000`、`1000-3000`、`>3000`

用途：

- 工程核价时，按零件数量匹配工序阶梯单价。

当前后端保存工艺时会把 `calcType` 归一为：

```text
工时
```

### 8.3 百分比费用类

表：

```text
dict_markup_fee
```

主要字段：

- 费用名称
- 费率百分比
- 排序号
- 是否启用

用途：

- 按基础成本计算附加费用，例如管理费、利润、税费等。

### 8.4 非工时费用工艺

表：

```text
dict_extra_fee_item
```

主要字段：

- 工艺名称
- 排序号
- 启用状态
- 备注

用途：

- 在工程核价工艺路线中作为“非工时费用”使用。
- 费用按工程师输入金额直接计入基础成本。

## 9. 系统管理

### 9.1 用户权限

入口页面：

```text
系统管理：用户权限
```

后端接口：

```text
GET /api/admin/users
POST /api/admin/users
PUT /api/admin/users/{id}
DELETE /api/admin/users/{id}
PUT /api/admin/users/me/password
```

主要能力：

- 新增用户。
- 编辑显示名称、角色、启用状态。
- 重置密码。
- 停用或启用用户。
- 删除非保护用户。

当前角色枚举：

```text
ADMIN
MARKET
ENGINEER
```

### 9.2 报价单模板

入口页面：

```text
系统管理：报价单模板
```

后端接口：

```text
GET /api/admin/quote-template
POST /api/admin/quote-template
```

用途：

- 管理客户模板报价单 `.xlsx` 文件。
- 市场部导出“客户模板详细报价单”和“客户模板简化报价单”时使用。

模板要求：

- 文件必须为 `.xlsx`。
- 模板至少包含一个工作表。
- 第 8 行必须包含表头。
- 必要表头包括：序号、图号、物料描述、图纸状态、材料名称、表面处理、版本、数量、单位、含税单价、含税总价、交货周期、备注。
- 详细报价单模板还需要支持工艺名称、费率、基础成本等字段。

## 10. ERP与APS数据关系

ERP 是 APS 的上游系统。

APS 当前从 ERP 拉取两类数据：

### 10.1 工艺基础数据

ERP 接口：

```text
GET /api/aps/source/processes
```

返回数据来自：

- `dict_process`
- `dict_extra_fee_item`

返回字段：

| 字段 | 说明 |
|---|---|
| `sourceId` | ERP 源数据 ID |
| `processName` | 工艺名称 |
| `processType` | `WORK_HOUR` 或 `NON_WORK_HOUR` |
| `calcType` | `工时` 或 `非工时费用` |
| `sortNo` | 排序号 |

### 10.2 已提单订单明细

ERP 接口：

```text
GET /api/aps/source/delivery-items
```

该接口基于提单行 `order_delivery_ticket` 输出 APS 任务数据，并关联：

- RFQ 主单 `order_rfq`
- 零件核价数据 `item_quote_version`
- 工艺表行 `order_process_sheet`

主要返回字段包括：

- `ticketId`
- `rfqId`
- `itemId`
- `sourceProcessSheetId`
- `rfqNo`
- `customerName`
- `orderStatus`
- `internalProjectNo`
- `customerOrderNo`
- `customerProjectNo`
- `partNo`
- `version`
- `description`
- `qty`
- `materialName`
- `materialType`
- `materialGrossWeight`
- `materialDemandWeight`
- `partNetWeight`
- `surfaceTreatment`
- `startDate`
- `deliveryDate`
- `drawingUrl`
- `drawingViewUrl`
- `materialData`
- `processRows`
- `totalWorkHours`

APS 同步时可通过 `afterTicketId` 增量拉取：

```text
GET /api/aps/source/delivery-items?afterTicketId=0&limit=200
```

ERP 对 APS 数据源接口做了专门令牌校验：

- 配置项：`ERP_APS_SOURCE_TOKEN`
- APS 侧对应：`APS_ERP_AUTH_TOKEN`
- 支持 `Authorization: Bearer <token>` 或 `X-APS-Auth-Token`

## 11. 文件与图纸管理

ERP 支持 PDF 图纸上传、保存和浏览。

关键配置：

```yaml
erp.upload-dir
erp.public-base-url
```

Docker Compose 中后端挂载：

```text
uploads_data:/data/uploads
backups_data:/data/backups
```

图纸导入规则：

- 市场部导入询价时可同时上传 PDF 图纸。
- 系统从图纸文件名中提取图号。
- 系统将提取出的图号和 Excel 中图号匹配。
- 匹配成功的图纸 URL 写入零件明细 `drawing_url`。
- 未匹配图纸和缺图零件会在导入结果中返回。
- 订单管理页面支持对缺图零件补传图纸。

## 12. 异步任务

ERP 有异步任务能力，表为：

```text
system_task
```

当前使用场景：

- 工程部一键刷新整单核价。
- 报价单后台导出。

接口：

```text
POST /api/tasks/engineer/refresh-quotes/{rfqId}
POST /api/tasks/market/export-quote/{rfqId}
GET /api/tasks/{taskId}
GET /api/tasks/{taskId}/download
GET /api/tasks
```

任务字段包括：

- 任务类型
- 状态
- 进度
- 总数
- 请求 JSON
- 结果 JSON
- 输出文件路径
- 错误信息
- 创建人
- 开始时间
- 完成时间

## 13. 订单状态说明

当前系统中的主要订单状态如下：

| 状态 | 产生场景 | 说明 |
|---|---|---|
| `待工程部评估` | 市场导入新单后 | 工程部尚未完成整单报价 |
| `已报价` | 工程部点击完成整单报价后 | 市场部可处理报价单 |
| `部分已提单` | 部分已报价零件生成提单后 | 订单未全部提单 |
| `已生成提单` | 已报价零件全部生成提单后 | 可向 APS 同步生产任务 |

主要零件状态如下：

| 状态 | 产生场景 | 说明 |
|---|---|---|
| `待核价` | 市场导入新单后 | 工程部尚未核价 |
| `已报价` | 工程部提交核价后 | 有最终含税单价 |
| `已部分提单` | 该零件已有部分数量提单 | 还未达到询价数量 |
| `已生成提单` | 该零件已按全部数量提单 | 该零件提单完成 |

## 14. 部署概况

ERP Docker Compose 文件：

```text
D:\QPO-Operations-Management-System\docker-compose.yml
```

ERP 环境变量文件：

```text
D:\QPO-Operations-Management-System\erp.env
```

启动命令：

```powershell
cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env up -d --build
```

本机默认访问：

```text
http://localhost:8088
```

后端健康检查：

```text
http://localhost:8080/api/health
```

当前 Compose 已配置：

- PostgreSQL、后端、前端三个服务。
- `restart: unless-stopped`。
- 日志大小限制。
- CPU 和内存限制。
- `Asia/Shanghai` 时区。
- 后端、数据库、前端健康检查。
- 前端、后端和数据库默认绑定 `127.0.0.1`。
- 前端容器通过 Nginx 提供静态页面并反向代理 API。

生产 HTTPS 由外层 Nginx 或网关处理，ERP 容器内部仍使用 HTTP。

## 15. 备份、恢复和归档

相关脚本：

| 脚本 | 说明 |
|---|---|
| `scripts\backup.ps1` | ERP 手动备份 |
| `scripts\restore.ps1` | ERP 覆盖式恢复，需要 `-Force` |
| `scripts\restore-drill.ps1` | 不影响业务库的恢复演练 |
| `scripts\install-backup-task.ps1` | 安装每日定时备份任务 |
| `scripts\capacity-report.ps1` | 容量报告 |

备份内容：

- PostgreSQL 逻辑备份。
- 上传文件和图纸。
- 备份元数据。

日志保留：

- 操作日志保留、归档和清理由系统页面 **系统 > 操作日志 > 日志保留策略** 配置。
- 后端运行日志、Postgres 慢 SQL 和 Docker 容器日志查看方式见 `docs/QPO日志模块/03-日志运维说明.md`。

## 16. 安全设计

当前系统已具备以下基础安全措施：

- 登录鉴权默认开启。
- JWT 密钥要求生产环境必须使用强随机值。
- 默认密钥或过短密钥会导致后端启动失败。
- 后端接口按角色控制访问。
- 初始密码或重置密码后强制用户改密。
- 用户可被停用。
- APS 数据源接口支持独立 token，不需要暴露 ERP 用户账号。
- Docker Compose 默认把前端、后端、数据库绑定到本机回环地址。
- 生产 HTTPS 推荐由外层 Nginx 或网关统一处理。

需要部署人员在正式环境确认：

- `ERP_JWT_SECRET` 已替换为强随机值。
- `POSTGRES_PASSWORD` 已替换为强随机值。
- `ERP_CORS_ALLOWED_ORIGINS` 指向真实 HTTPS 域名。
- `ERP_APS_SOURCE_TOKEN` 与 APS 的 `APS_ERP_AUTH_TOKEN` 一致。
- 外层 HTTPS 证书和反向代理配置正确。
- 数据库和后端端口不直接暴露到公网。

## 17. 当前已知设计取舍

### 17.1 ERP不再保留仓库库存模块

仓库库存表已经通过 Flyway 迁移删除。库存管理统一向 APS 侧收敛，ERP 仅保留订单、报价和提单数据。

### 17.2 ERP向APS提供数据源，不主动推送

APS 通过 ERP 数据源接口主动拉取：

- 工艺基础数据。
- 已提单订单明细。

这样做可以降低 ERP 对 APS 可用性的依赖，ERP 不需要在提单时同步等待 APS。

### 17.3 图纸文件保存在文件卷，不进入数据库

数据库只保存图纸 URL 或路径，实际 PDF 保存在上传目录。备份时必须同时备份数据库和上传文件。

### 17.4 报价单模板由管理员维护

系统支持默认导出和客户模板导出。客户模板对 Excel 表头结构有要求，模板格式不正确会被拒绝。

## 18. 后续维护重点

交接后建议团队重点关注以下事项：

- 补齐业务用户手册和每页字段说明。
- 对 ERP 与 APS 的数据同步字段保持版本一致。
- 新增字段必须同步考虑导入、核价、导出、提单、APS 数据源和备份恢复。
- 修改材料或工艺定价规则后，应回归测试核价、报价导出和 APS 工艺同步。
- 修改提单逻辑后，应回归测试订单状态、零件状态、工艺表行和 APS 同步。
- 正式上线前应保留一份恢复演练成功的备份。
- 管理员账号和 token 不应写入交接文档正文，应由部署负责人单独保管。

## 19. 关键代码位置

| 类型 | 路径 |
|---|---|
| 前端路由 | `D:\QPO-Operations-Management-System\quote-frontend\src\router\index.js` |
| 前端主布局 | `D:\QPO-Operations-Management-System\quote-frontend\src\App.vue` |
| 登录状态 | `D:\QPO-Operations-Management-System\quote-frontend\src\auth.js` |
| 市场新单导入页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\MarketDashboard.vue` |
| 报价单处理页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\QuoteProcess.vue` |
| 议价提单页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\MarketRelease.vue` |
| 工程全局设定页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\GlobalSetup.vue` |
| 工程逐件核价页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\ItemPricing.vue` |
| 材料与工艺管理页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\MaterialProcessManage.vue` |
| 订单管理页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\OrderManage.vue` |
| 用户管理页面 | `D:\QPO-Operations-Management-System\quote-frontend\src\components\UserManage.vue` |
| 市场接口控制器 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\controller\MarketController.java` |
| 工程接口控制器 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\controller\EngineerController.java` |
| 字典接口控制器 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\controller\DictController.java` |
| 系统管理控制器 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\controller\AdminController.java` |
| APS 数据源控制器 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\controller\ApsSourceController.java` |
| 工程核价服务 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\service\EngineerService.java` |
| 提单服务 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\service\DeliveryService.java` |
| APS 数据源服务 | `D:\QPO-Operations-Management-System\quote-backend\src\main\java\com\machining\quote\service\ApsSourceService.java` |
| 数据库迁移 | `D:\QPO-Operations-Management-System\quote-backend\src\main\resources\db\migration` |

## 20. 交接结论

ERP 当前已经形成较完整的市场、工程、提单、报价导出和系统管理闭环。它适合作为 APS 的上游订单与工艺数据来源。正式交接时，团队应重点掌握三条主线：

1. 市场导入客户询价和图纸，形成 RFQ 与零件明细。
2. 工程完成材料、工艺路线和核价，形成可报价数据。
3. 市场确认议价后生成提单和工艺表，再由 APS 拉取生产任务。

只要维护好这三条主线，ERP 与 APS 的后续扩展就有清晰边界。
