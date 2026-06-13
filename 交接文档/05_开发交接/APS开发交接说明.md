# APS开发交接说明

本文档面向后续开发团队，说明 APS 系统的代码结构、技术栈、运行方式、核心模块、鉴权机制、数据库迁移、ERP 对接方式、测试方式和后续开发注意事项。

适用代码路径：

- APS 后端：`D:\Code\aps-system`
- APS 前端：`D:\Code\aps-frontend`
- APS Docker Compose：`D:\Code\docker-compose.aps.yml`
- APS 环境变量模板：`D:\Code\aps.env.example`
- APS 运维脚本：`D:\Code\scripts`

## 1. 系统定位

APS 是面向生产计划、库存锁定、部门派工和工艺能力维护的排产辅助系统。它不是独立订单系统，业务源头来自 ERP。

APS 主要职责：

- 从 ERP 同步可排产的交付项。
- 将 ERP 交付项转换为 APS 工单。
- 根据零件库存判断能否直接占用库存。
- 根据材料库存判断材料是否够用。
- 对不足零件生成生产需求。
- 对不足材料生成材料缺口。
- 将待分配工单派给生产部门或外协部门。
- 维护部门产能、机台、机台工艺能力。
- 维护 APS 管理员用户。

当前 APS 权限模型只开放管理员角色 `ADMIN`，为后续继续细分 PMC、仓库、工程、部门等角色预留了基础结构。

## 2. 技术栈

### 2.1 后端

| 项目 | 内容 |
| --- | --- |
| 语言 | Java 17 |
| 框架 | Spring Boot 3.5.14 |
| ORM | MyBatis-Plus 3.5.16 |
| 数据库 | PostgreSQL 15 |
| 迁移工具 | Flyway |
| 参数校验 | Spring Validation |
| 密码加密 | Spring Security Crypto BCrypt |
| Excel 导出 | EasyExcel |
| 测试 | JUnit 5、Spring Boot Test、Testcontainers |

### 2.2 前端

| 项目 | 内容 |
| --- | --- |
| 框架 | Vue 3 |
| 路由 | Vue Router |
| UI 组件 | Element Plus |
| 图标 | @element-plus/icons-vue |
| 构建工具 | Vite |
| 接口请求 | 原生 fetch 封装 |

### 2.3 容器部署

| 服务 | 镜像或构建方式 | 说明 |
| --- | --- | --- |
| `aps-postgres` | `postgres:15` | APS PostgreSQL 数据库 |
| `aps-backend` | `D:\Code\aps-system\Dockerfile` | Spring Boot 后端 |
| `aps-frontend` | `D:\Code\aps-frontend\Dockerfile` | Nginx 托管前端静态文件，并反代 `/api` |

## 3. 本地开发启动

### 3.1 后端本地启动

前置条件：

- Java 17。
- Maven Wrapper 可用。
- PostgreSQL 可用。
- 数据库连接信息与 `application.yaml` 或环境变量一致。

默认开发配置：

```yaml
server.port: 8091
spring.datasource.url: jdbc:postgresql://localhost:5433/aps
spring.datasource.username: aps
spring.datasource.password: aps123456
```

启动命令：

```powershell
cd D:\Code\aps-system
.\mvnw.cmd spring-boot:run
```

测试命令：

```powershell
cd D:\Code\aps-system
.\mvnw.cmd test
```

后端测试依赖 Testcontainers，需要本机 Docker 可用。

### 3.2 前端本地启动

前置条件：

- Node.js 22 或兼容版本。
- 已执行 `npm install`。
- 后端服务运行在 `http://localhost:8091`，或者通过 `VITE_API_BASE_URL` 指定其他地址。

启动命令：

```powershell
cd D:\Code\aps-frontend
npm install
npm run dev
```

构建命令：

```powershell
cd D:\Code\aps-frontend
npm run build
```

前端默认开发接口地址在 `D:\Code\aps-frontend\src\config.js`：

```js
VITE_API_BASE_URL 未设置时默认为 http://localhost:8091
```

容器部署时，前端构建参数把 `VITE_API_BASE_URL` 设为空字符串，使前端通过同域 `/api` 访问后端，避免跨域问题。

### 3.3 Docker 本地启动

先准备环境变量：

```powershell
cd D:\Code
Copy-Item aps.env.example aps.env
notepad aps.env
```

必须填写：

- `APS_POSTGRES_PASSWORD`
- `APS_JWT_SECRET`
- `APS_ERP_BASE_URL`
- `APS_ERP_AUTH_TOKEN`

启动：

```powershell
cd D:\Code
docker compose --env-file aps.env -f docker-compose.aps.yml up -d --build
```

访问：

```text
http://127.0.0.1:8095
```

查看状态：

```powershell
docker compose --env-file aps.env -f docker-compose.aps.yml ps
```

## 4. 后端目录说明

后端主包：

```text
com.machining.apssystem
```

主要目录：

| 路径 | 说明 |
| --- | --- |
| `controller` | REST 接口入口 |
| `service` | 核心业务逻辑 |
| `mapper` | MyBatis-Plus 数据库访问 |
| `entity` | 数据库表实体 |
| `dto` | 请求和响应对象 |
| `config` | 鉴权、跨域、异常处理、上下文 |
| `resources\db\migration` | Flyway 数据库迁移脚本 |
| `src\test` | 后端集成测试 |

## 5. 后端核心类

### 5.1 控制器

| 类 | 接口前缀 | 职责 |
| --- | --- | --- |
| `ApsAuthController` | `/api/auth` | 登录、登出、当前用户、修改密码 |
| `ApsAdminController` | `/api/aps/admin` | 管理员用户管理 |
| `ApsSyncController` | `/api/aps` | ERP 交付项同步、近期任务查询 |
| `ApsProcessController` | `/api/aps/processes` | APS 工艺列表、从 ERP 同步工艺 |
| `ApsPmcController` | `/api/aps/pmc` | PMC 派工、部门、机台、机台能力 |
| `ApsDepartmentCenterController` | `/api/aps/department-center` | 部门中心任务查询和详情 |
| `ApsInventoryController` | `/api/aps/inventory` | 零件库存、材料库存、锁定、缺口、导出 |
| `ApsHealthController` | `/api/health` | 健康检查 |

### 5.2 服务层

| 类 | 职责 |
| --- | --- |
| `ApsAuthService` | 登录校验、当前用户、修改密码 |
| `ApsJwtService` | JWT 生成和解析 |
| `ApsPasswordService` | 密码哈希、旧哈希兼容、BCrypt 校验 |
| `ApsUserAdminService` | 管理员新增、编辑、重置密码、删除 |
| `ApsSyncService` | 从 ERP 同步交付项并生成 APS 工单、库存锁定、材料锁定 |
| `ApsProcessService` | 工艺查询、从 ERP 同步工艺 |
| `ApsPmcService` | 待分配工单、已分配工单、派工、释放派工、删除工单 |
| `ApsDepartmentCenterService` | 部门中心任务分页和详情 |
| `ApsInventoryService` | 零件库存、材料库存、锁定确认、释放、缺口维护 |
| `ApsInventoryExportService` | 零件锁定记录导出 |
| `ApsDepartmentService` | 部门维护、产能调整、产能重置 |
| `ApsMachineCapabilityService` | 机台、机台工艺能力维护 |

## 6. 前端目录说明

主要目录：

| 路径 | 说明 |
| --- | --- |
| `src\main.js` | Vue 应用入口，注册 Element Plus 和图标 |
| `src\App.vue` | APS 外壳布局、侧边栏菜单、页面标题、登录状态 |
| `src\router\index.js` | 路由配置和登录守卫 |
| `src\api.js` | 统一请求封装、Token 注入、401/428 处理 |
| `src\auth.js` | 登录状态、本地存储、角色判断、当前用户验证 |
| `src\config.js` | API 地址配置 |
| `src\style.css` | 全局样式、Element Plus 主题覆盖 |
| `src\components` | 通用表格、分页、状态标签、工序表格 |
| `src\views` | 各业务页面 |

## 7. 前端页面与路由

| 页面 | 路由 | 组件 |
| --- | --- | --- |
| 登录 | `/login` | `LoginView.vue` |
| ERP 同步 | `/sync` | `SyncView.vue` |
| 待分配 | `/assignment/pending` | `TasksView.vue` |
| 已分配 | `/assignment/assigned` | `AssignedTasksView.vue` |
| 部门中心 | `/department-center` | `DepartmentCenterView.vue` |
| 零件库存维护 | `/part-inventory/manage` | `PartInventoryView.vue` |
| 零件锁定记录 | `/part-inventory/locks` | `PartInventoryLocksView.vue` |
| 生产需求 | `/part-inventory/requirements` | `PartProductionRequirementsView.vue` |
| 材料库存维护 | `/material-inventory/manage` | `MaterialInventoryView.vue` |
| 材料锁定记录 | `/material-inventory/locks` | `MaterialLocksView.vue` |
| 材料缺口 | `/material-inventory/shortages` | `MaterialShortagesView.vue` |
| 部门管理 | `/departments` | `DepartmentsView.vue` |
| 工艺管理 | `/processes` | `ProcessesView.vue` |
| 机台能力 | `/machines` | `MachinesView.vue` |
| 用户权限 | `/users` | `UsersView.vue` |

## 8. 鉴权与权限

### 8.1 登录流程

1. 前端登录页提交用户名、密码到 `/api/auth/login`。
2. 后端校验 `aps_user`。
3. 登录成功返回 JWT Token 和用户信息。
4. 前端保存到 localStorage。
5. 后续请求统一在 `Authorization: Bearer <token>` 中携带 Token。
6. 路由跳转前通过 `/api/auth/me` 验证当前 Token。

### 8.2 强制改密

管理员用户有 `force_password_change` 字段。新建用户和重置密码后，该字段会被设为 true。

当前设计：

- 首次初始化管理员可能需要改密。
- 新增管理员首次登录后应修改密码。
- 被重置密码的管理员再次登录后应修改密码。
- 后端遇到需要改密的用户会返回对应状态，前端根据状态引导。

### 8.3 角色模型

当前只支持：

```text
ADMIN
```

所有业务页面路由和后端管理接口都按管理员权限开放。

后续扩展角色时，主要改动点：

- `aps_user.role` 的合法值。
- `ApsUserAdminService` 中的角色校验。
- 控制器上的 `@RequireRole`。
- 前端 `router\index.js` 中每个路由的 `meta.roles`。
- `auth.js` 中角色判断逻辑。
- 菜单是否按角色显示。

## 9. 数据库迁移规则

APS 使用 Flyway 管理数据库结构，迁移脚本路径：

```text
D:\Code\aps-system\src\main\resources\db\migration
```

迁移脚本必须按版本递增命名：

```text
V14__说明.sql
V15__说明.sql
```

已存在迁移不要修改内容，尤其是已经在生产环境执行过的迁移。需要新增字段、索引、表或修复结构时，新建下一版本迁移。

当前迁移：

| 版本 | 说明 |
| --- | --- |
| `V1__aps_baseline.sql` | APS 基础表 |
| `V2__aps_part_pending_stock.sql` | 零件在产库存明细 |
| `V3__department_assignment_nullable_department.sql` | 部门派工可为空部门兼容 |
| `V4__department_assignment_capacity_counted.sql` | 部门产能计数字段 |
| `V5__aps_process_management.sql` | APS 工艺表 |
| `V6__work_item_customer_order_fields.sql` | 工单客户订单字段 |
| `V7__drop_rfq_fields.sql` | 移除报价相关旧字段 |
| `V8__part_inventory_lock_detail.sql` | 零件锁定明细 |
| `V9__part_lock_detail_status.sql` | 零件锁定明细状态 |
| `V10__material_lock_warehouse_status.sql` | 材料锁定仓库状态 |
| `V11__work_item_process_route_grouping.sql` | 工单工序路线分组 |
| `V12__department_center_tasks.sql` | 部门中心任务表 |
| `V13__aps_user_management.sql` | APS 用户权限表 |

## 10. 主要数据表

| 表 | 作用 |
| --- | --- |
| `aps_user` | APS 管理员用户 |
| `aps_erp_sync_run` | ERP 同步批次记录 |
| `aps_work_item` | APS 工单主表 |
| `aps_work_item_process` | 工单工序明细 |
| `aps_process` | APS 工艺主数据 |
| `aps_part_inventory` | 零件库存主表 |
| `aps_part_inventory_pending_stock` | 零件在产待入库库存 |
| `aps_part_inventory_lock` | 零件锁定记录 |
| `aps_part_inventory_lock_detail` | 零件锁定来源明细 |
| `aps_production_requirement` | 零件生产需求 |
| `aps_material_inventory` | 材料库存 |
| `aps_material_lock` | 材料锁定记录 |
| `aps_material_shortage` | 材料缺口 |
| `aps_department` | 部门 |
| `aps_machine` | 机台 |
| `aps_machine_process_capability` | 机台工艺能力 |
| `aps_department_assignment` | 部门派工记录 |
| `aps_department_task` | 部门中心任务 |

详细字段说明见 `D:\Code\交接文档\05_开发交接\数据库表说明.md`。

## 11. ERP 对接

### 11.1 对接方向

APS 主动调用 ERP 接口，不由 ERP 推送。

主要对接：

- ERP 交付项同步到 APS 工单。
- ERP 工艺同步到 APS 工艺主数据。

### 11.2 配置项

后端从环境变量读取：

| 变量 | 作用 |
| --- | --- |
| `APS_ERP_BASE_URL` | ERP 后端内网地址 |
| `APS_ERP_AUTH_TOKEN` | APS 调用 ERP 的服务端 Token |
| `APS_ERP_SYNC_PAGE_SIZE` | 每批同步 ERP 交付项数量 |

Docker 部署时在 `D:\Code\aps.env` 中配置。

### 11.3 Token 一致性

`APS_ERP_AUTH_TOKEN` 必须和 ERP 侧允许 APS 访问的 Token 一致。否则 APS 同步 ERP 数据会失败。

交接时不要把真实 Token 写入文档或代码仓库，应通过安全渠道交付给部署人员。

## 12. 核心业务规则

### 12.1 ERP 交付项去重

APS 同步 ERP 交付项时，以 ERP 交付项 ID 作为唯一来源标识。已经同步过的交付项不会重复生成 APS 工单。

### 12.2 零件库存锁定

同步工单时，APS 会按客户、零件号、版本匹配零件库存：

1. 优先锁定现有库存。
2. 现有库存不足时，继续锁定在产待入库库存。
3. 仍不足时，生成零件生产需求。

零件锁定状态包括：

- `LOCKED`：库存满足。
- `PARTIAL_LOCKED`：部分满足。
- `NEED_PRODUCTION`：需要生产。

### 12.3 材料库存锁定

同步工单时，APS 会按材料名称、材料类型匹配材料库存。

材料需求重量优先使用 ERP 明确传入的需求重量；如果没有明确值，则按材料毛重乘数量计算。

材料锁定状态包括：

- `NOT_REQUIRED`：不需要材料。
- `LOCKED`：材料满足。
- `PARTIAL_LOCKED`：部分满足。
- `SHORTAGE`：材料不足。

### 12.4 部门派工

派工时需要满足：

- 目标部门存在。
- 目标部门启用。
- 部门剩余工时不少于工单工时。

派工成功后：

- 写入 `aps_department_assignment`。
- 写入或更新 `aps_department_task`。
- 占用部门月度工时。
- 工单从待分配转为已分配。

释放派工后：

- 释放部门占用工时。
- 派工记录置为释放状态。
- 工单回到待分配。

### 12.5 删除 APS 工单

删除工单时需要释放与工单相关的有效占用：

- 部门派工占用。
- 零件锁定占用。
- 材料锁定占用。

该逻辑集中在 `ApsPmcService`，后续改动时要避免只删主表导致库存占用残留。

## 13. 前端开发规范

### 13.1 UI 组件

APS 前端已经接入 Element Plus。新增页面优先使用：

- `el-container`
- `el-menu`
- `el-button`
- `el-input`
- `el-select`
- `el-table`
- `el-dialog`
- `el-pagination`
- `el-tag`
- Element Plus 图标

不要再新增大面积原生按钮、原生表格、原生弹窗，否则会重新拉开 APS 与 ERP 的前端风格差异。

### 13.2 请求规范

新增接口请求应走：

```text
D:\Code\aps-frontend\src\api.js
```

不要在页面里直接拼完整后端地址。原因：

- `api.js` 会统一附加 Token。
- `api.js` 会统一处理 401。
- `api.js` 会统一处理 428 强制改密。
- 容器部署时前端使用同域 `/api`，不应写死 `localhost`。

### 13.3 路由规范

新增页面要同时维护：

- `D:\Code\aps-frontend\src\router\index.js`
- `D:\Code\aps-frontend\src\App.vue` 侧边栏菜单
- 对应页面组件，例如 `src\views\CapacityBoardView.vue`

如果页面需要权限，设置 `meta.roles`。

### 13.4 样式规范

全局样式在：

```text
D:\Code\aps-frontend\src\style.css
```

APS 当前外壳和 Element Plus 变量已经向 ERP 风格靠齐。新增样式应优先复用现有类和 Element Plus 变量。

## 14. 后端开发规范

### 14.1 接口返回

现有接口主要返回 JSON 对象。前端 `api.js` 会判断：

- HTTP 状态码是否成功。
- 响应体中的 `success === false`。
- 响应体中的 `message`。

新增接口应保持错误信息清晰，便于前端直接显示。

### 14.2 参数校验

请求 DTO 应使用 Validation 注解，常见规则：

- 必填字段使用 `@NotBlank`、`@NotNull`。
- 数量、重量、工时使用正数校验。
- 字符串长度应限制。

业务规则校验放在 Service 层，不要只依赖前端。

### 14.3 数据访问

新增表建议按现有结构创建：

1. Entity。
2. Mapper。
3. DTO。
4. Service。
5. Controller。
6. Flyway migration。
7. 集成测试。

### 14.4 事务

涉及库存扣减、锁定、释放、派工、删除工单的操作必须使用事务。否则可能出现主记录已改但库存占用未同步的情况。

需要重点保护的流程：

- ERP 同步生成工单。
- 零件锁定和释放。
- 材料锁定和释放。
- 部门派工和释放。
- 删除 APS 工单。

## 15. 测试说明

### 15.1 后端测试

执行：

```powershell
cd D:\Code\aps-system
.\mvnw.cmd test
```

后端测试使用 Testcontainers 启动临时 PostgreSQL，能验证 Flyway 迁移和核心业务流程。

重点测试文件：

| 文件 | 说明 |
| --- | --- |
| `ApsSystemApplicationTests.java` | Spring Boot 基础上下文测试 |
| `ApsWorkflowIntegrationTests.java` | APS 工作流集成测试 |

### 15.2 前端构建

执行：

```powershell
cd D:\Code\aps-frontend
npm run build
```

构建通过说明 Vue、路由、Element Plus 和页面语法基本正常。

### 15.3 Docker 构建

执行：

```powershell
cd D:\Code
docker compose --env-file aps.env -f docker-compose.aps.yml build
```

然后启动：

```powershell
docker compose --env-file aps.env -f docker-compose.aps.yml up -d
```

## 16. 部署相关文件

| 文件 | 说明 |
| --- | --- |
| `D:\Code\docker-compose.aps.yml` | APS Compose 部署入口 |
| `D:\Code\aps.env.example` | APS 环境变量模板 |
| `D:\Code\aps-system\Dockerfile` | 后端镜像构建 |
| `D:\Code\aps-frontend\Dockerfile` | 前端镜像构建 |
| `D:\Code\aps-frontend\nginx.conf` | 前端 Nginx 静态资源和 `/api` 反代 |
| `D:\Code\scripts\backup-aps.ps1` | APS 备份 |
| `D:\Code\scripts\restore-aps.ps1` | APS 正式恢复 |
| `D:\Code\scripts\restore-drill-aps.ps1` | APS 恢复演练 |

部署详情见：

```text
D:\Code\交接文档\04_部署运维\APS部署说明.md
D:\Code\交接文档\04_部署运维\APS备份与恢复说明.md
```

## 17. 默认管理员

新库初始化后，Flyway 会创建默认管理员：

```text
用户名：admin
初始密码：admin123
```

注意：

- 该账号仅用于初始化。
- 正式部署后必须立即修改密码。
- 生产环境不得长期保留默认密码。
- 初始 `admin` 账号不可删除。

如果本机使用旧数据库 volume，密码可能已经被修改，不一定还是 `admin123`。

## 18. 常见开发任务

### 18.1 新增一个 APS 页面

步骤：

1. 在 `src\views` 新建页面组件。
2. 在 `src\router\index.js` 添加路由。
3. 在 `App.vue` 添加菜单项。
4. 在 `api.js` 中复用统一请求方法。
5. 后端新增 Controller/Service/DTO。
6. 必要时新增数据库迁移。
7. 执行前端构建和后端测试。

### 18.2 新增一个数据库字段

步骤：

1. 新建 Flyway 脚本，例如 `V14__add_department_priority_field.sql`。
2. 修改 Entity。
3. 修改 DTO。
4. 修改 Service 写入和查询逻辑。
5. 修改前端页面显示或表单。
6. 补充测试。

不要直接修改已经上线执行过的历史迁移。

### 18.3 新增角色权限

步骤：

1. 明确角色名称和页面权限。
2. 修改 `aps_user.role` 校验。
3. 修改用户管理页面角色选择。
4. 修改后端接口 `@RequireRole`。
5. 修改前端路由 `meta.roles`。
6. 修改菜单显示规则。
7. 加测试验证越权访问。

当前系统只设置管理员，后续角色设计要和 ERP 权限模型统一。

### 18.4 新增 ERP 同步字段

步骤：

1. 先确认 ERP 对外接口是否已经返回该字段。
2. 修改 APS 的 ERP DTO。
3. 修改 `ApsSyncService` 转换逻辑。
4. 修改 APS 表结构和 Entity。
5. 修改前端显示。
6. 补充同步测试。
7. 更新 `ERP与APS数据流转说明.md` 和 `接口说明.md`。

## 19. 重要风险点

### 19.1 库存锁定一致性

库存锁定、释放、删除工单之间存在强关联。改动相关逻辑前必须先读：

- `ApsSyncService`
- `ApsInventoryService`
- `ApsPmcService`

避免出现：

- 库存已占用但工单不存在。
- 工单已删除但锁定记录仍有效。
- 释放锁定时没有恢复库存。
- 生产需求重复生成。

### 19.2 部门产能一致性

派工和释放会影响部门占用工时。改动相关逻辑前必须验证：

- 部门剩余工时不能为负。
- 释放派工要归还占用工时。
- 删除工单要释放派工占用。
- 禁用部门不能继续派工。

### 19.3 ERP 接口兼容性

APS 同步依赖 ERP 输出结构。ERP 字段名或状态规则变化后，APS 可能无法同步或同步出错。涉及 ERP 接口调整时，必须同时修改 APS DTO、同步服务和文档。

### 19.4 生产环境密钥

以下内容不能提交到代码仓库：

- 生产 `aps.env`。
- 生产数据库密码。
- 生产 `APS_JWT_SECRET`。
- 生产 `APS_ERP_AUTH_TOKEN`。
- HTTPS 证书私钥。

## 20. 交接检查清单

开发团队接手 APS 前，应确认：

| 检查项 | 要求 |
| --- | --- |
| 本地后端测试 | `.\mvnw.cmd test` 通过 |
| 本地前端构建 | `npm run build` 通过 |
| Docker 构建 | `docker compose --env-file aps.env -f docker-compose.aps.yml build` 通过 |
| 数据库迁移 | 新库启动后 Flyway 能自动建表 |
| 登录 | 默认管理员或已配置管理员能登录 |
| 用户权限 | 能新增、编辑、重置、删除非初始管理员 |
| ERP 同步 | Token 和 ERP 地址配置正确 |
| 派工流程 | 待分配、已分配、释放派工正常 |
| 库存流程 | 零件和材料锁定、释放、缺口正常 |
| 备份恢复 | 能手动备份并完成恢复演练 |

完成以上检查后，后续团队可以基于现有结构继续开发角色权限、排产算法、甘特图、产能看板、消息提醒等扩展功能。
