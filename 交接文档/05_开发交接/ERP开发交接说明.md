# ERP 开发交接说明

编写日期：2026-06-13  
适用系统：机加工报价 ERP 系统  
后端工程：`D:\Code\quote-system`  
前端工程：`D:\Code\quote-frontend`  
部署入口：`D:\Code\docker-compose.yml`  

本文面向后续开发团队，说明 ERP 系统的工程结构、启动方式、关键代码入口、开发规则、测试方式和常见改动路径。

## 1. 技术栈

### 1.1 后端

| 项目 | 当前技术 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.2.5 |
| ORM | MyBatis-Plus 3.5.5 |
| 数据库 | PostgreSQL 15 |
| 数据库迁移 | Flyway |
| Excel 处理 | EasyExcel 3.3.4 |
| 密码哈希 | Spring Security Crypto / BCrypt |
| 构建工具 | Maven Wrapper |
| 容器运行 | eclipse-temurin:17-jre |

### 1.2 前端

| 项目 | 当前技术 |
| --- | --- |
| 框架 | Vue 3 |
| 路由 | Vue Router 4 |
| UI | Element Plus |
| HTTP | Axios |
| 构建工具 | Vite |
| 容器运行 | Nginx 1.27 Alpine |

## 2. 后端工程结构

后端目录：

```text
D:\Code\quote-system
```

关键目录：

| 路径 | 说明 |
| --- | --- |
| `src\main\java\com\machining\quote` | 后端 Java 主包 |
| `src\main\resources\application.yaml` | 后端配置入口 |
| `src\main\resources\db\migration` | Flyway 数据库迁移脚本 |
| `src\test\java\com\machining\quote` | 后端测试 |
| `Dockerfile` | 后端 Docker 构建 |
| `pom.xml` | Maven 依赖和构建配置 |
| `.env.example` | 后端本地环境变量示例 |

后端包结构：

| 包 | 说明 |
| --- | --- |
| `controller` | HTTP API 控制器 |
| `service` | 核心业务逻辑 |
| `entity` | 数据库实体，对应表 |
| `mapper` | MyBatis-Plus Mapper |
| `dto` | 请求和响应 DTO |
| `dto.aps` | ERP 输出给 APS 的 DTO |
| `config` | 权限、拦截器、线程池、JSONB、全局异常等配置 |
| `util` | 工具类，例如图号解析、材料数据解析 |

## 3. 后端关键入口

### 3.1 启动类

| 文件 | 说明 |
| --- | --- |
| `QuoteSystemApplication.java` | Spring Boot 启动类，包含 `@MapperScan("com.machining.quote.mapper")` |

### 3.2 控制器

| 控制器 | 前缀 | 说明 |
| --- | --- | --- |
| `AuthController` | `/api/auth` | 登录、登出、当前用户、改密 |
| `AdminController` | `/api/admin` | 用户管理、报价单模板 |
| `MarketController` | `/api/market` | 新单导入、提单、流转卡、导出 |
| `QuoteProcessController` | `/api/market/quote-process` | 报价处理和报价交期 |
| `InternalTargetLeadTimeController` | `/api/market/internal-target-lead-time` | 内部目标交期 |
| `EngineerController` | `/api/engineer` | 工程待核价、提交核价、完成整单报价、装配关系 |
| `DictController` | `/api/dict` | 材料、工艺、费用字典 |
| `OrderManagementController` | `/api/order-management` | 订单查询、补图、重置生产输出、删除订单 |
| `ApsSourceController` | `/api/aps/source` | APS 读取 ERP 工艺和已提单数据 |
| `SystemTaskController` | `/api/tasks` | 后台任务查询和下载 |
| `UploadFileController` | `/uploads/**` | 上传文件访问 |
| `HealthController` | `/api/health` | 健康检查 |

### 3.3 核心 Service

| Service | 说明 |
| --- | --- |
| `AuthService` | 登录校验、JWT 用户信息、旧 SHA-256 密码兼容升级 |
| `UserAdminService` | 用户新增、编辑、删除、改密、密码校验 |
| `EngineerService` | 普通件核价、装配件核价、刷新核价、重复件应用 |
| `DeliveryService` | 生成提单和工艺流转卡，刷新订单与零件状态 |
| `QuoteProcessService` | 报价处理页面数据、报价交期调整 |
| `InternalTargetLeadTimeService` | 内部目标交期处理 |
| `OrderManagementService` | 订单分页、详情、补图、重置生产输出、删除 |
| `ExcelExportService` | 提单、流转卡、报价单等导出 |
| `QuoteTemplateService` | 报价模板上传和状态 |
| `QuoteTemplateExportService` | 按模板生成报价单 |
| `ApsSourceService` | ERP 到 APS 的数据输出 |
| `SystemTaskService` | 后台任务创建、执行、结果下载 |
| `RfqNumberService` | RFQ 编号生成 |
| `LeadTimeService` | 客户交期、报价交期、内部目标交期解析 |
| `OrderFilePathService` | 订单文件目录和路径管理 |

## 4. 前端工程结构

前端目录：

```text
D:\Code\quote-frontend
```

关键文件：

| 路径 | 说明 |
| --- | --- |
| `src\main.js` | Vue 入口，注册 Element Plus、图标、Axios 拦截器 |
| `src\App.vue` | 应用外壳、侧边栏、页面布局 |
| `src\router\index.js` | 路由和角色权限 |
| `src\auth.js` | 登录状态、本地 token、角色判断 |
| `src\config.js` | API 根地址 |
| `src\download.js` | 下载文件辅助 |
| `src\authenticatedFile.js` | 带鉴权的文件访问辅助 |
| `src\style.css` | 全局样式和 Element Plus 风格覆盖 |
| `Dockerfile` | 前端 Docker 构建 |
| `nginx.conf` | 前端容器 Nginx 反向代理配置 |
| `package.json` | 前端依赖和脚本 |

前端页面组件：

| 组件 | 页面 |
| --- | --- |
| `LoginView.vue` | 登录页 |
| `MarketDashboard.vue` | 市场部新询价单导入中心 |
| `QuoteProcess.vue` | 市场部报价单处理生成 |
| `MarketRelease.vue` | 市场部议价确认与提单生成 |
| `GlobalSetup.vue` | 工程部全局设定 |
| `ItemPricing.vue` | 工程部逐件工艺路线与核价中心 |
| `MaterialProcessManage.vue` | 工程部材料与工艺管理 |
| `OrderManage.vue` | 市场部订单管理 |
| `HistoryOrderManage.vue` | 市场部历史订单管理 |
| `UserManage.vue` | 系统管理：用户权限 |
| `QuoteTemplateManage.vue` | 系统管理：报价单模板 |
| `OrderPickerDialog.vue` | 订单选择弹窗 |
| `EngineeringOptionPickerDialog.vue` | 工程材料 / 工艺弹出选择组件 |

## 5. 本地开发启动

### 5.1 后端本地启动

进入后端目录：

```powershell
cd D:\Code\quote-system
```

运行测试：

```powershell
.\mvnw.cmd test
```

本地启动：

```powershell
.\mvnw.cmd spring-boot:run
```

默认后端端口：

```text
8080
```

后端本地默认数据库来自 `application.yaml`：

```text
jdbc:postgresql://localhost:5432/postgres
```

如使用 Docker Compose 中的数据库，应确保 `erp.env` 和本地环境变量一致，或直接通过 Compose 启动整套系统。

### 5.2 前端本地启动

进入前端目录：

```powershell
cd D:\Code\quote-frontend
```

安装依赖：

```powershell
npm install
```

开发启动：

```powershell
npm run dev
```

构建：

```powershell
npm run build
```

前端 API 默认：

| 场景 | API 根地址 |
| --- | --- |
| 本地开发未配置 `VITE_API_BASE_URL` | `http://localhost:8080` |
| Docker 构建 | 空字符串，前端通过 `/api` 访问同域反向代理 |

### 5.3 Docker 启动

根目录：

```powershell
cd D:\Code
docker compose --env-file erp.env up -d --build
```

前端访问：

```text
http://127.0.0.1:8088
```

## 6. 配置入口

### 6.1 后端配置

后端配置文件：

```text
D:\Code\quote-system\src\main\resources\application.yaml
```

主要环境变量：

| 变量 | 说明 |
| --- | --- |
| `SERVER_PORT` | 后端端口 |
| `DB_URL` | PostgreSQL 连接地址 |
| `DB_USERNAME` | 数据库用户名 |
| `DB_PASSWORD` | 数据库密码 |
| `ERP_UPLOAD_DIR` | 上传文件目录 |
| `ERP_BACKUP_DIR` | 备份目录 |
| `ERP_CORS_ALLOWED_ORIGINS` | 允许跨域来源 |
| `ERP_AUTH_ENABLED` | 是否启用登录鉴权 |
| `ERP_JWT_SECRET` | JWT 签名密钥 |
| `ERP_JWT_TTL_MINUTES` | JWT 有效期 |
| `ERP_APS_SOURCE_TOKEN` | APS 读取 ERP 数据的令牌 |
| `ERP_TASK_CORE_POOL_SIZE` | 后台任务核心线程数 |
| `ERP_TASK_MAX_POOL_SIZE` | 后台任务最大线程数 |
| `ERP_TASK_OUTPUT_DIR` | 后台任务输出目录 |

### 6.2 前端配置

前端配置文件：

```text
D:\Code\quote-frontend\src\config.js
```

配置项：

| 变量 | 说明 |
| --- | --- |
| `VITE_API_BASE_URL` | 前端调用后端 API 的根地址 |

本地开发可使用：

```env
VITE_API_BASE_URL=http://localhost:8080
```

Docker 生产构建使用空字符串：

```text
VITE_API_BASE_URL=
```

这样前端调用 `/api`，由前端 Nginx 容器转发到后端容器。

## 7. 权限体系

### 7.1 前端权限

前端路由定义：

```text
D:\Code\quote-frontend\src\router\index.js
```

路由通过 `meta.roles` 限制访问：

| 角色 | 说明 |
| --- | --- |
| `ADMIN` | 管理员，可访问全部页面 |
| `MARKET` | 市场部 |
| `ENGINEER` | 工程部 |

前端登录状态：

```text
D:\Code\quote-frontend\src\auth.js
```

本地存储：

| key | 说明 |
| --- | --- |
| `erp_token` | JWT |
| `erp_user` | 当前用户信息 |

### 7.2 后端权限

后端权限注解：

```text
RequireRole.java
```

后端权限拦截器：

```text
AuthInterceptor.java
```

原则：

1. 前端路由只负责用户体验。
2. 后端 `@RequireRole` 才是接口安全边界。
3. 新增接口时必须确认是否需要 `@RequireRole`。
4. `ADMIN` 在后端权限判断中具备全部角色能力。
5. APS 源数据接口可通过 ERP-APS 共享 token 访问。

## 8. 数据库开发规则

### 8.1 Flyway 规则

迁移脚本目录：

```text
D:\Code\quote-system\src\main\resources\db\migration
```

规则：

1. 新增表、字段、索引，必须新增新的 Flyway 脚本。
2. 不要修改已经在正式环境执行过的迁移脚本。
3. 脚本命名使用更高版本号，例如 `V23__add_quote_audit_field.sql`。
4. 删除表或字段前必须确认页面、接口、导出、APS 同步是否仍引用。
5. 上线前先备份数据库。

### 8.2 实体和 Mapper

实体目录：

```text
src\main\java\com\machining\quote\entity
```

Mapper 目录：

```text
src\main\java\com\machining\quote\mapper
```

当前使用 MyBatis-Plus：

1. 实体通过 `@TableName` 映射数据库表。
2. Mapper 通常继承 `BaseMapper<T>`。
3. JSONB 字段使用 `JsonbTypeHandler`。
4. 新增 JSONB 字段时要确认实体 `autoResultMap = true`。

## 9. 常见开发改动路径

### 9.1 新增前端页面

步骤：

1. 在 `quote-frontend\src\components` 新增 Vue 组件。
2. 在 `quote-frontend\src\router\index.js` 新增路由。
3. 设置 `meta.roles`。
4. 在 `App.vue` 侧边栏增加菜单入口。
5. 如需接口，在组件中使用 Axios 调用后端。
6. 执行 `npm run build`。

### 9.2 新增后端接口

步骤：

1. 在对应 Controller 中新增方法，或新增 Controller。
2. 设计请求 DTO 和响应结构。
3. 在 Service 中实现业务逻辑。
4. 如涉及数据库，新增实体、Mapper、Flyway 迁移。
5. 加 `@RequireRole` 权限限制。
6. 补充测试。
7. 更新接口说明文档。

### 9.3 新增数据库字段

步骤：

1. 新增 Flyway 脚本。
2. 修改实体字段。
3. 修改 Service 读写逻辑。
4. 修改前端页面展示或输入。
5. 修改导入、导出或 APS DTO，如果字段参与这些流程。
6. 执行后端测试和前端构建。

### 9.4 修改报价计算逻辑

重点文件：

```text
EngineerService.java
```

相关数据：

| 数据 | 说明 |
| --- | --- |
| `dict_material` | 材料密度和价格 |
| `dict_process` | 工时类工艺阶梯价格 |
| `dict_markup_fee` | 百分比费用 |
| `dict_extra_fee_item` | 非工时费用项 |
| `item_quote_version.materialData` | 材料核价 JSON |
| `item_quote_version.processRoute` | 工艺路线 JSON |
| `item_quote_version.feeData` | 费用明细 JSON |

修改报价计算后至少测试：

1. 普通件核价。
2. 非工时费用。
3. 百分比费用。
4. 装配件核价。
5. 一键刷新核价。
6. 报价单导出。
7. 流转卡生成。

### 9.5 修改 Excel 导入

重点文件：

| 文件 | 说明 |
| --- | --- |
| `MarketController.java` | 新询价导入入口 |
| `RfqExcelDTO.java` | Excel 表头到字段映射 |
| `DrawingPartNoResolver.java` | 图纸文件名图号解析 |
| `OrderFilePathService.java` | 文件路径管理 |

修改后至少测试：

1. 标准客户询价表导入。
2. 空字段处理。
3. 版本字段规范化。
4. PDF 图纸匹配。
5. 缺失图纸和未匹配图纸报告。

### 9.6 修改 Excel 导出

重点文件：

| 文件 | 说明 |
| --- | --- |
| `ExcelExportService.java` | 普通报价单、提单、流转卡等导出 |
| `QuoteTemplateExportService.java` | 按模板导出报价单 |
| `QuoteTemplateService.java` | 模板上传和校验 |

修改后至少测试：

1. 标准报价单。
2. 简版报价单。
3. 模板明细报价单。
4. 模板简版报价单。
5. 提单导出。
6. 工艺流转卡导出。

### 9.7 修改 APS 同步字段

重点文件：

| 文件 | 说明 |
| --- | --- |
| `ApsSourceController.java` | APS 源数据接口 |
| `ApsSourceService.java` | APS 输出数据组装 |
| `dto\aps\ApsProcessDTO.java` | APS 工艺 DTO |
| `dto\aps\ApsDeliveryItemDTO.java` | APS 交付任务 DTO |
| `dto\aps\ApsDeliveryItemBatchDTO.java` | APS 批量交付 DTO |

注意：

1. ERP 是源系统，APS 不应直接写 ERP 数据。
2. 修改字段前要同步修改 APS 解析逻辑。
3. 部署时 `ERP_APS_SOURCE_TOKEN` 必须与 APS 侧 `APS_ERP_AUTH_TOKEN` 一致。

### 9.8 修改权限角色

当前角色：

```text
ADMIN
MARKET
ENGINEER
```

新增角色时必须同步修改：

1. 后端用户角色校验逻辑。
2. 后端 `@RequireRole`。
3. 前端 `router/index.js`。
4. 前端用户管理角色下拉。
5. 菜单显示逻辑。
6. 种子用户或迁移脚本。
7. 交接文档和验收清单。

## 10. 测试

### 10.1 后端测试

测试目录：

```text
D:\Code\quote-system\src\test\java\com\machining\quote
```

当前测试覆盖：

| 测试类 | 关注点 |
| --- | --- |
| `RfqNumberServiceTests` | RFQ 编号生成 |
| `QuoteTemplateServiceTests` | 报价模板上传和校验 |
| `QuoteTemplateExportServiceTests` | 模板报价导出 |
| `OrderFilePathServiceTests` | 订单文件路径 |
| `MarketControllerPartSelectionTests` | 市场部零件选择解析 |
| `ExcelExportServiceProcessSheetTests` | 工艺流转卡导出 |
| `EngineerServiceProcessSortTests` | 工艺排序 |
| `EngineerServiceMaterialCalculationTests` | 材料成本计算 |
| `DrawingPartNoResolverTests` | 图纸文件名图号解析 |
| `DeliveryServiceDateParsingTests` | 提单交期解析 |
| `CoreWorkflowIntegrationTests` | 核心业务流程集成 |
| `OperationalWorkflowIntegrationTests` | 运维相关流程 |
| `AssemblyWorkflowIntegrationTests` | 装配件流程 |

运行：

```powershell
cd D:\Code\quote-system
.\mvnw.cmd test
```

### 10.2 前端构建

前端目前没有单元测试配置，主要通过构建和业务页面验收验证。

运行：

```powershell
cd D:\Code\quote-frontend
npm run build
```

### 10.3 Docker 验证

```powershell
cd D:\Code
docker compose --env-file erp.env up -d --build
docker compose --env-file erp.env ps
```

然后访问：

```text
http://127.0.0.1:8088
```

## 11. 开发注意事项

1. 正式环境不要关闭 `ERP_AUTH_ENABLED`。
2. 正式环境不要使用默认 `ERP_JWT_SECRET`。
3. `erp.env` 包含密钥，不应提交到公开仓库。
4. Flyway 已上线脚本不要改，只能新增脚本。
5. 删除表、字段、页面前要先查前端、后端、导出、APS 同步是否引用。
6. 报价计算结果以后端为准，前端只负责输入和展示。
7. Excel 导入导出字段变更要同步通知业务。
8. 图纸和模板文件属于业务数据，要随数据库一起备份。
9. 高风险操作包括删除订单、重置生产输出、恢复数据库、删除字典项。
10. 修改 ERP 输出给 APS 的接口时，必须和 APS 同步发布。

## 12. 推荐开发交接顺序

新团队接手时建议按顺序熟悉：

1. 先运行 Docker Compose，确认系统可登录。
2. 阅读 `ERP系统总览.md`。
3. 阅读 `ERP页面功能与输入输出说明.md`。
4. 阅读 `ERP核心业务流程.md`。
5. 阅读本文，理解工程结构。
6. 跑后端测试和前端构建。
7. 跟一单完整业务：导入、核价、报价、提单。
8. 再开始修改代码。
