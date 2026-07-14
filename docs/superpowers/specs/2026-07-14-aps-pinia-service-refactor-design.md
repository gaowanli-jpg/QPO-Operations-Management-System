# APS Pinia 与状态治理重构开发文档

> 目标：在不改变现有 APS 业务行为的前提下，把认证与跨页面状态逐步收敛到 Pinia，并用测试护栏保护现有 Axios service 层、路由守卫和库存/任务核心流程。

## 1. 当前结论

APS 前端不是完全没有测试，但测试确实偏少。补测前只有：

- `aps-frontend/src/api.test.js`
- `aps-frontend/src/services/apiServices.test.js`

补测前基线：`npm test` 为 2 个测试文件、14 个测试。已有测试主要覆盖 API helper 和少量 service 合约，缺少认证状态、任务流、库存流等重构高风险入口的独立测试。

本轮已先补上必要基础测试：

- `aps-frontend/src/auth.test.js`
- `aps-frontend/src/services/taskApi.test.js`
- `aps-frontend/src/services/inventoryApi.test.js`
- 扩展 `aps-frontend/src/api.test.js`

补测后基线：5 个测试文件、35 个测试，覆盖认证持久化、`verifyAuth()`、401/428 API 钩子、任务分页/批量操作/同步、库存锁定与库存变更、API 错误结构兼容。

## 2. 现状评估

### 已经做得比较好的部分

- APS 已有统一 `apiClient` 与 `request()`，业务页面没有直接 `axios.*` 调用，直接 Axios 只集中在 `aps-frontend/src/api.js`。
- 业务 API 已按模块拆分在 `aps-frontend/src/services/`：
  - `authApi.js`
  - `departmentApi.js`
  - `inventoryApi.js`
  - `machineApi.js`
  - `processApi.js`
  - `taskApi.js`
  - `userApi.js`
- 页面已经大量通过 service 调用后端，后续重构不需要重复做 ERP 阶段 5 那种大规模 Axios service 拆分。

### 主要问题

- `aps-frontend/package.json` 当前没有 `pinia`，认证状态仍由 `src/auth.js` 的模块级 `reactive()` 管理。
- `src/auth.js` 同时承担本地存储、用户状态、权限判断、API 钩子绑定、401 跳转，职责偏多。
- `App.vue`、`router/index.js`、`LoginView.vue` 直接消费 `authState` / `setAuth()` / `clearAuth()` / `verifyAuth()` / `hasRole()`，后续权限逻辑扩展不够集中。
- 列表页的筛选、分页、选中项、详情弹窗状态大量散落在 view 内，尤其是任务中心和库存页。
- 多个页面仍使用 `window.confirm()`，交互风格和 Element Plus 其余页面不完全统一。
- 测试目前主要是 service contract 和 auth 单元测试，缺少路由守卫、组件交互、关键页面状态组合测试。

## 3. 重构原则

- 不改后端接口路径、请求方法、payload 字段和响应消费结构。
- 先迁移状态管理，不重写页面 UI 和表格结构。
- 先做认证 Store，再做任务/库存 Store；避免一次性把所有 view 状态搬进 Pinia。
- 可复用但不泛化：只抽跨页面复用或重构风险高的状态，本页面独有表单仍留在组件。
- 每阶段单独测试、构建、提交，必要时可回滚到上一阶段。

## 4. 推荐目标结构

```text
aps-frontend/src/
  api.js
  services/
    authApi.js
    departmentApi.js
    inventoryApi.js
    machineApi.js
    processApi.js
    taskApi.js
    userApi.js
  stores/
    authStore.js
    taskStore.js
    inventoryStore.js
    referenceDataStore.js
  tests or colocated *.test.js
    api.test.js
    authStore.test.js
    taskStore.test.js
    inventoryStore.test.js
```

说明：

- `authStore.js` 替代 `auth.js`，集中 token、user、verify、login/logout/changePassword、role getter。
- `taskStore.js` 只承接任务列表共用状态和操作：同步、待分配、已分配、部门中心的分页与选中项可逐步接入。
- `inventoryStore.js` 先承接零件/材料库存的常用列表缓存和 mutation 后刷新逻辑。
- `referenceDataStore.js` 可在后续承接部门、工艺、机台能力等低频字典数据，避免页面重复请求。

## 5. 分阶段实施方案

### 阶段 0：测试护栏补齐

目标：先保护现有行为，为 Pinia 化做回归基线。

已完成：

- 扩展 `api.test.js`：
  - `unwrapResponsePayload()` 对 `success:false` 保留 `error.response.status` 和 `error.response.data`。
- 新增 `auth.test.js`：
  - `setAuth()` 持久化 token/user。
  - `clearAuth()` 清除本地认证。
  - `verifyAuth()` 成功刷新用户、失败清登录。
  - `hasRole()` 保持 ADMIN 通行逻辑。
  - API 401/428 钩子行为不变。
- 新增 `taskApi.test.js`：
  - 任务分页、部门中心分页、释放分配、批量删除/创建、ERP 同步。
- 新增 `inventoryApi.test.js`：
  - 材料默认分页、材料锁定确认/释放、零件库存增加/转入/修改、零件锁定确认/释放、导出路径。

验收：

- `npm test` 通过。
- `npm run build` 通过。

### 阶段 1：引入 Pinia 基础设施

目标：只接入 Pinia，不改变页面行为。

文件：

- 修改 `aps-frontend/package.json`
- 修改 `aps-frontend/package-lock.json`
- 修改 `aps-frontend/src/main.js`

步骤：

1. 执行 `npm install pinia`。
2. 在 `main.js` 中：
   - `import { createPinia } from 'pinia'`
   - `const pinia = createPinia()`
   - `app.use(pinia)`
   - 保持 `app.use(router)` 和 Element Plus 初始化顺序清晰。

验收：

- `npm test` 通过。
- `npm run build` 通过。
- 登录、刷新、直接访问受保护路由行为不变。

### 阶段 2：认证 Store

目标：用 `authStore` 替换 `src/auth.js`。

文件：

- 新增 `aps-frontend/src/stores/authStore.js`
- 新增 `aps-frontend/src/stores/authStore.test.js`
- 修改 `aps-frontend/src/main.js`
- 修改 `aps-frontend/src/router/index.js`
- 修改 `aps-frontend/src/App.vue`
- 修改 `aps-frontend/src/views/LoginView.vue`
- 废弃或删除 `aps-frontend/src/auth.js`

关键设计：

- 保持 localStorage key 兼容：
  - `aps_token`
  - `aps_user`
- `authStore` state：
  - `token`
  - `user`
  - `verified`
- `authStore` getters：
  - `displayName`
  - `roleName`
  - `mustChangePassword`
  - `isAuthenticated`
- `authStore` actions：
  - `setAuth(token, user)`
  - `clearAuth()`
  - `updateUser(patch)`
  - `verifyAuth()`
  - `login(credentials)`
  - `logout()`
  - `changePassword(payload)`
  - `hasRole(roles)`
- `configureApiAuth()` 在应用启动后绑定：
  - `getToken: () => authStore.token`
  - `onUnauthorized: () => authStore.clearAuth() + 跳转 /login`
  - `onPasswordChangeRequired: () => authStore.updateUser({ forcePasswordChange: true })`

验收：

- 登录成功后 token 持久化。
- 刷新页面后仍保持登录。
- 未登录访问任意业务路由跳转 `/login?redirect=...`。
- 401 清理登录并跳转登录页。
- 428 触发强制改密弹窗。
- 修改密码成功后清除 `forcePasswordChange`。
- 登出调用后端失败时也能本地退出。

### 阶段 3：任务 Store

目标：把任务中心共用的任务列表、分页、选中项和同步动作抽到 `taskStore`，先覆盖 PMC 任务，不重写表格组件。

文件：

- 新增 `aps-frontend/src/stores/taskStore.js`
- 新增 `aps-frontend/src/stores/taskStore.test.js`
- 修改：
  - `aps-frontend/src/views/SyncView.vue`
  - `aps-frontend/src/views/TasksView.vue`
  - `aps-frontend/src/views/AssignedTasksView.vue`
  - `aps-frontend/src/views/DepartmentCenterView.vue`

关键设计：

- `taskStore` 保留页面可控筛选项，但集中列表加载和 mutation 后刷新逻辑。
- `SyncView.vue` 使用 store 的 `syncErpDeliveryItems()` 与 `loadPmcTasks()`。
- `TasksView.vue` 使用 store 的待分配查询、批量删除、分配后刷新。
- `AssignedTasksView.vue` 使用 store 的已分配查询、释放分配后刷新。
- `DepartmentCenterView.vue` 可先只复用 service，不强行合并部门中心的本地详情状态。

验收：

- ERP 同步任务后列表刷新。
- 待分配任务分页、筛选、批量选中、批量删除、分配功能不变。
- 已分配任务释放后回到待分配池。
- 部门中心分页和详情加载不变。

### 阶段 4：库存 Store

目标：把零件库存、材料库存、锁定列表、缺口列表的共用加载和 mutation 后刷新逻辑抽到 `inventoryStore`。

文件：

- 新增 `aps-frontend/src/stores/inventoryStore.js`
- 新增 `aps-frontend/src/stores/inventoryStore.test.js`
- 修改：
  - `aps-frontend/src/views/PartInventoryView.vue`
  - `aps-frontend/src/views/PartInventoryLocksView.vue`
  - `aps-frontend/src/views/PartProductionRequirementsView.vue`
  - `aps-frontend/src/views/MaterialInventoryView.vue`
  - `aps-frontend/src/views/MaterialLocksView.vue`
  - `aps-frontend/src/views/MaterialShortagesView.vue`

关键设计：

- 先集中数据加载和 CRUD，不迁移页面内表单草稿。
- 零件库存页保留 `createForm`、`stockForm`、`transferForm`、`modifyForm` 在组件内。
- Store actions 负责：
  - `loadPartCustomers()`
  - `loadPartInventoryPage(params)`
  - `addPartStock(payload)`
  - `transferPartPending(payload)`
  - `modifyPartInventory(payload)`
  - `confirmPartLock(id)`
  - `releasePartLock(id)`
  - `loadMaterials(params)`
  - `confirmMaterialLock(id)`
  - `releaseMaterialLock(id)`

验收：

- 零件库存新增、在产转入、直接入库、修改、删除行为不变。
- 零件锁定确认/释放后列表刷新。
- 材料库存新增、删除、锁定确认/释放、缺口列表正常。
- 导出零件锁定记录路径不变。

### 阶段 5：基础资料 Store 与交互统一

目标：减少部门、工艺、机台能力等基础数据重复请求，并统一确认弹窗风格。

文件：

- 新增 `aps-frontend/src/stores/referenceDataStore.js`
- 修改：
  - `aps-frontend/src/views/DepartmentsView.vue`
  - `aps-frontend/src/views/ProcessesView.vue`
  - `aps-frontend/src/views/MachinesView.vue`
  - 涉及 `window.confirm()` 的页面

关键设计：

- `referenceDataStore` 缓存：
  - departments
  - processes
  - machines
  - machineCapabilities
- 管理页保存/删除后调用 store reload。
- 把 `window.confirm()` 逐步替换为 Element Plus `ElMessageBox.confirm()`，保持确认文案不变。

验收：

- 部门容量调整、重置、删除行为不变。
- ERP 工艺同步行为不变。
- 机台新增、编辑、删除、能力新增/删除行为不变。
- 删除/释放确认弹窗风格统一。

## 6. 测试策略

### 单元测试

继续使用 Vitest，优先补三类测试：

- API helper：
  - request config
  - backend `success:false` 错误结构
  - blob 下载错误消息
- Service contract：
  - 路径
  - method
  - query string
  - payload
  - fallback filename
- Store：
  - localStorage 兼容
  - action 调 service
  - mutation 后刷新
  - 错误时状态不污染

### 构建验证

每阶段运行：

```powershell
cd D:\Code\aps-frontend
npm test
npm run build
npm audit --omit=dev
cd D:\Code
git diff --check
```

### 代码扫描

```powershell
rg -n "from '../auth'|from './auth'|authState|setAuth|clearAuth|updateAuthUser|verifyAuth|hasRole" aps-frontend/src
rg -n "import axios|axios\\.|API_BASE_URL|fetch\\(" aps-frontend/src
rg -n "window\\.confirm" aps-frontend/src
```

预期：

- 认证迁移完成后第一条只剩测试或完全无旧 `auth.js` 消费方。
- 第二条只允许 `src/api.js` 和 `src/config.js`。
- 阶段 5 完成后第三条无业务页面残留。

## 7. Docker Compose 验证

APS 验证命令：

```powershell
cd D:\Code
docker compose -f docker-compose.aps.yml --env-file aps.env up -d --build
docker compose -f docker-compose.aps.yml --env-file aps.env ps
```

预期：

- APS 后端容器 healthy。
- APS 前端容器 healthy。
- APS 依赖数据库容器 healthy。
- 前端访问 `http://127.0.0.1:8098` 或部署说明中的 APS 前端端口正常。

## 8. 手工回归清单

- 管理员登录 APS。
- 刷新页面后仍保持登录。
- 未登录直接访问 `/sync` 跳转登录页并带 redirect。
- 初始密码账号触发强制修改密码。
- ERP 同步生成 APS 任务。
- 待分配任务筛选、分页、批量分配、批量删除。
- 已分配任务释放分配。
- 部门中心查看任务详情。
- 零件库存新增、入库、在产转入、修改、删除。
- 零件锁定确认/释放。
- 材料库存和材料锁定确认/释放。
- 工艺同步、部门容量、机台能力维护。

## 9. 风险与控制

### 风险 1：Pinia 初始化顺序影响路由守卫

控制：

- `main.js` 先 `app.use(pinia)`，再 `app.use(router)`。
- 路由守卫内调用 `useAuthStore()`，避免模块顶层读取未初始化 Store。

### 风险 2：认证本地缓存 key 改动导致用户被意外登出

控制：

- 继续使用 `aps_token` 和 `aps_user`。
- `authStore.test.js` 明确覆盖 localStorage 兼容。

### 风险 3：任务/库存 Store 迁移过大导致页面行为变动

控制：

- 表单草稿和弹窗状态先留组件内。
- Store 只承接跨页面数据、列表、选中项和 mutation 后刷新。
- 每个页面迁移后跑对应 service/store 测试和手工回归。

### 风险 4：后端错误提示退化

控制：

- `api.test.js` 已覆盖 `error.response.data.message` 兼容。
- 页面 catch 中继续使用 `err.message` 或 `error.response?.data?.message`。

## 10. 建议提交节奏

1. `test: add APS auth and service coverage`
2. `feat: add APS pinia foundation`
3. `refactor: migrate APS auth to pinia`
4. `refactor: centralize APS task state`
5. `refactor: centralize APS inventory state`
6. `refactor: centralize APS reference data`

推荐先执行阶段 1/2，原因是认证 Store 是后续所有页面状态重构的基础；任务和库存 Store 可以在认证稳定后分批推进。
