# ERP Pinia 与 Axios Service 层重构开发文档

> 日期：2026-07-14  
> 范围：`quote-frontend` 前端  
> 目标：在不改变现有业务行为的前提下，把 ERP 前端的跨页面状态收敛到 Pinia，并把 API 调用拆到可复用的 Axios service 层。

## 1. 当前项目结论

### 技术栈现状

- `quote-frontend` 使用 Vue 3、Vite、Element Plus、Vue Router、Axios。
- 当前没有使用 Pinia：
  - `quote-frontend/package.json` 没有 `pinia` 依赖。
  - `quote-frontend/src` 内没有 `createPinia`、`defineStore`、`useXxxStore`。
- APS 前端已经完成 Axios service 层拆分，可作为 ERP 的实现风格参考：
  - `APS 前端目录/src/api.js`
  - `APS 前端目录/src/services/authApi.js`
  - `APS 前端目录/src/services/taskApi.js`
  - `APS 前端目录/src/services/inventoryApi.js`

### ERP 当前主要问题

- 认证状态散落在 `src/auth.js`：
  - 用 `reactive` 维护 `token`、`user`、`verified`。
  - 直接读写 `localStorage` 的 `erp_token`、`erp_user`。
  - `verifyAuth()` 仍使用 `fetch`，与项目 Axios 化方向不一致。
- Axios 拦截器写在 `src/main.js`：
  - 请求拦截器读取 `authState.token`。
  - 响应 `401` 时清空登录并跳转登录页。
  - 响应 `428` 时标记强制改密。
- 路由守卫依赖 `src/auth.js`：
  - `router/index.js` 直接使用 `authState`、`verifyAuth()`、`hasRole()`。
- 布局层依赖认证状态和直接 API：
  - `App.vue` 读取用户信息、角色、强制改密状态。
  - `App.vue` 直接调用 `/api/auth/change-password`、`/api/auth/logout`。
- 全局报价配置通过组件直接读写 `localStorage`：
  - `GlobalSetup.vue` 写入 `erp_global_config_${orderId}` 和 `erp_global_config`。
  - `ItemPricing.vue` 读取并再次写入同一批 key。
- 字典数据在多个页面重复请求：
  - `GlobalSetup.vue` 请求材料、工艺、非工时费用。
  - `ItemPricing.vue` 请求材料、工艺、非工时费用、百分比费用。
  - `MaterialProcessManage.vue` 请求并维护材料、工艺、百分比费用、非工时费用。
- ERP 前端直接 `axios.*` 调用较多：
  - `MaterialProcessManage.vue` 约 16 处。
  - `ItemPricing.vue` 约 12 处。
  - `MarketRelease.vue` 约 11 处。
  - `GlobalSetup.vue` 约 7 处。

## 2. 重构目标

### 必须完成

- 引入 Pinia，并在 `main.js` 注册。
- 用 `authStore` 替代 `src/auth.js` 的全局 reactive 状态。
- 用 Axios service 方法替代认证相关的直接 API 调用。
- 把全局报价配置从组件内 `localStorage` 读写迁移到 `globalConfigStore`。
- 把可复用字典数据迁移到 `dictionaryStore`，减少重复请求和重复数据格式化。
- 保持现有页面、路由权限、强制改密、登录跳转、Docker Compose 部署方式不变。

### 建议完成

- 建立 ERP 自己的 Axios 客户端：
  - `src/api.js` 提供 `apiClient`、`request()`、`downloadFile()`、认证拦截器配置能力。
  - 风格对齐 APS 的 `APS 前端目录/src/api.js`。
- 建立业务 service 层：
  - `src/services/authApi.js`
  - `src/services/dictApi.js`
  - `src/services/engineerApi.js`
  - `src/services/marketApi.js`
  - `src/services/orderApi.js`
  - `src/services/adminApi.js`
  - `src/services/taskApi.js`
  - `src/services/fileApi.js`
- 对高频基础 service 增加轻量单元测试，至少覆盖请求路径、method、payload。

### 本轮不做

- 不改后端接口。
- 不改数据库、Docker Compose 服务拓扑、无 Redis 架构。
- 不把每个页面的表单状态、弹窗开关、局部 loading 都搬进 Pinia。
- 不重写 UI、不调整现有业务流程。
- 不一次性改变所有接口返回结构的消费方式；先通过 service 保持兼容。

## 3. 推荐架构

### 目录结构

```text
quote-frontend/src/
  api.js
  authenticatedFile.js
  download.js
  main.js
  router/index.js
  stores/
    authStore.js
    globalConfigStore.js
    dictionaryStore.js
  services/
    authApi.js
    dictApi.js
    engineerApi.js
    marketApi.js
    orderApi.js
    adminApi.js
    taskApi.js
    fileApi.js
```

### 状态边界

Pinia 只管理跨页面共享或需要缓存的状态：

- `authStore`
  - `token`
  - `user`
  - `verified`
  - `isAuthenticated`
  - `mustChangePassword`
  - `displayName`
  - `roleName`
  - `setAuth(token, user)`
  - `clearAuth()`
  - `updateUser(patch)`
  - `verifyAuth()`
  - `hasRole(roles)`
  - `login(credentials)`
  - `logout()`
  - `changePassword(payload)`
- `globalConfigStore`
  - `currentRfqId`
  - `configByRfqId`
  - `fallbackConfig`
  - `getConfig(rfqId)`
  - `setConfig(rfqId, config)`
  - `loadLocalConfig(rfqId)`
  - `persistLocalConfig(rfqId, config)`
  - `applyEngineerConfig(rfqId, config)`
- `dictionaryStore`
  - `materials`
  - `processes`
  - `extraFeeItems`
  - `markupFees`
  - `loadedAt`
  - `loading`
  - `loadBaseDicts({ includeMarkupFees, includeAllExtraFees, force })`
  - `reloadAfterMutation()`
  - `combinedProcessOptions`

组件继续保留局部状态：

- 表单内容。
- 弹窗显示状态。
- 当前表格选择。
- 页面专属 loading。
- 与单个组件强绑定的临时计算变量。

### Service 边界

Service 层只负责 HTTP：

- 统一 baseURL。
- 统一 token 注入。
- 统一 `401`、`428` 响应处理。
- 统一错误对象。
- 统一下载 Blob 处理。
- 组件和 store 不再拼接完整 `API_BASE_URL + path`。

Store 层负责状态和缓存：

- 调用 service。
- 写入或清除 `localStorage`。
- 暴露业务动作给组件。
- 不直接处理复杂 UI 消息。

组件层负责页面交互：

- 调 store 或 service。
- 展示 `ElMessage`、`ElMessageBox`。
- 保持现有模板和业务流程。

## 4. 分阶段实施流程

### 阶段 1：基础设施

目标：引入 Pinia 和 ERP Axios 客户端，但不改业务页面行为。

文件：

- 修改 `quote-frontend/package.json`
- 修改 `quote-frontend/package-lock.json`
- 新增 `quote-frontend/src/api.js`
- 修改 `quote-frontend/src/main.js`

步骤：

1. 执行 `npm install pinia`。
2. 在 `main.js` 中：
   - `import { createPinia } from 'pinia'`
   - `const pinia = createPinia()`
   - `app.use(pinia)`
3. 新增 `src/api.js`：
   - 用 `axios.create({ baseURL: API_BASE_URL })` 建立 `apiClient`。
   - 提供 `configureApiAuth({ getToken, onUnauthorized, onPasswordChangeRequired })`。
   - 提供 `request(path, options)`。
   - 提供 `downloadFile(path, fallbackName)`。
4. 暂时保留 `src/auth.js`，降低第一步风险。

验收：

- `npm run build` 通过。
- 登录、刷新页面、直接访问受保护路由行为不变。

### 阶段 2：认证 Store

目标：用 `authStore` 替换 `src/auth.js`，认证接口进入 `authApi.js`。

文件：

- 新增 `quote-frontend/src/services/authApi.js`
- 新增 `quote-frontend/src/stores/authStore.js`
- 修改 `quote-frontend/src/main.js`
- 修改 `quote-frontend/src/router/index.js`
- 修改 `quote-frontend/src/App.vue`
- 修改 `quote-frontend/src/components/LoginView.vue`
- 修改 `quote-frontend/src/components/HistoryOrderManage.vue`
- 删除或废弃 `quote-frontend/src/auth.js`

关键设计：

- `authStore` 初始化时从 `localStorage` 读取 `erp_token`、`erp_user`。
- `verifyAuth()` 调用 `authApi.getCurrentUser()`，不再使用 `fetch`。
- `configureApiAuth()` 在应用启动后绑定：
  - `getToken: () => authStore.token`
  - `onUnauthorized: () => authStore.clearAuth() + 跳转 /login`
  - `onPasswordChangeRequired: () => authStore.updateUser({ forcePasswordChange: true })`
- `App.vue` 改用 store 的 getter：
  - `displayName`
  - `roleName`
  - `mustChangePassword`
  - `hasRole()`
- `LoginView.vue` 改为：
  - `await authStore.login(form)`
  - 根据返回角色或 store 用户角色跳转。

验收：

- 登录成功后 token 持久化。
- 刷新页面后仍保持登录。
- 访问无权限页面仍回到对应角色首页。
- 后端返回 `401` 时清登录并跳转 `/login?redirect=...`。
- 后端返回 `428` 时弹出强制改密弹窗。
- 修改密码成功后 `forcePasswordChange` 被清除。
- 登出仍调用后端 logout，失败时也能本地退出。

### 阶段 3：全局报价配置 Store

目标：把 `GlobalSetup.vue` 与 `ItemPricing.vue` 共用的全局配置抽到 `globalConfigStore`。

文件：

- 新增 `quote-frontend/src/stores/globalConfigStore.js`
- 修改 `quote-frontend/src/components/GlobalSetup.vue`
- 修改 `quote-frontend/src/components/ItemPricing.vue`
- 新增或扩展 `quote-frontend/src/services/engineerApi.js`
- 新增或扩展 `quote-frontend/src/services/marketApi.js`

关键设计：

- 保持现有 localStorage key 兼容：
  - `erp_global_config_${rfqId}`
  - `erp_global_config`
- `globalConfigStore` 负责 normalize、load、persist。
- `GlobalSetup.vue` 在保存全局设定后调用：
  - `globalConfigStore.setConfig(orderId, config)`
  - `engineerApi.saveOrderConfig(orderId, config)`
- `ItemPricing.vue` 进入页面时调用：
  - `globalConfigStore.loadLocalConfig(rfqId)`
  - 如果订单返回 `engineerConfig`，用 `globalConfigStore.applyEngineerConfig(rfqId, engineerConfig)` 覆盖缓存。
- 继续保留组件内与具体报价行相关的计算逻辑，避免单次重构过大。

验收：

- 从工程设置页进入逐件核价页时，默认材料、形状、工艺路线仍能带入。
- 后端订单已有 `engineerConfig` 时优先使用后端配置。
- 修改逐件核价中的全局路线后，返回页面仍能读取最新配置。
- 原有 `localStorage` 数据不丢失。

### 阶段 4：字典 Store

目标：把材料、工艺、费用类字典抽到 `dictionaryStore`，减少重复请求。

文件：

- 新增 `quote-frontend/src/services/dictApi.js`
- 新增 `quote-frontend/src/stores/dictionaryStore.js`
- 修改 `quote-frontend/src/components/GlobalSetup.vue`
- 修改 `quote-frontend/src/components/ItemPricing.vue`
- 修改 `quote-frontend/src/components/MaterialProcessManage.vue`

关键设计：

- `dictApi.js` 提供：
  - `listMaterials()`
  - `saveMaterial(payload)`
  - `deleteMaterial(id)`
  - `listProcesses()`
  - `saveProcess(payload)`
  - `deleteProcess(id)`
  - `listMarkupFees()`
  - `saveMarkupFee(payload)`
  - `deleteMarkupFee(id)`
  - `listExtraFeeItems({ includeDisabled })`
  - `saveExtraFeeItem(payload)`
  - `deleteExtraFeeItem(id)`
- `dictionaryStore` 提供缓存加载：
  - `loadBaseDicts({ includeMarkupFees = false, includeAllExtraFees = false, force = false })`
  - 字典维护页保存或删除后调用 `reloadAfterMutation()`。
- `GlobalSetup.vue` 和 `ItemPricing.vue` 使用 store 数据和 computed，不再各自发重复请求。
- `MaterialProcessManage.vue` 可以继续保留表单状态，但 CRUD 请求改用 `dictApi`，保存成功后刷新 store。

验收：

- 工程设置页材料/工艺选择弹窗数据正常。
- 逐件核价页材料、工艺、非工时费用、百分比费用数据正常。
- 材料与工艺管理页新增、编辑、删除后，其他页面再次进入能读取最新字典。
- 字典接口失败时，逐件核价页仍可保留现有 fallback 数据策略。

### 阶段 5：业务 Service 层补齐

目标：按照模块把剩余直接 `axios.*` 调用迁移到 service 文件，让组件只处理 UI 与业务编排。

文件建议：

- `quote-frontend/src/services/marketApi.js`
- `quote-frontend/src/services/engineerApi.js`
- `quote-frontend/src/services/orderApi.js`
- `quote-frontend/src/services/adminApi.js`
- `quote-frontend/src/services/taskApi.js`
- `quote-frontend/src/services/fileApi.js`

迁移顺序建议：

1. `authApi.js`：登录、当前用户、改密、登出。
2. `dictApi.js`：材料、工艺、费用类。
3. `engineerApi.js`：订单配置、重复报价分析、逐件核价提交、完成报价、装配关系。
4. `marketApi.js`：订单汇总、RFQ 导入、放行、议价、报价流程、导出。
5. `orderApi.js`：客户、订单列表、订单详情、补图、重置生产、删除历史订单。
6. `adminApi.js`：用户管理、报价模板。
7. `taskApi.js`：后台任务提交、轮询。
8. `fileApi.js`：鉴权文件预览和下载可视情况迁移，确保 Blob 错误消息不退化。

验收：

- `rg -n "import axios|axios\\." quote-frontend/src` 只允许出现在 `src/api.js` 或少量文件下载兼容层中。
- 组件内不再拼接 `${API_BASE_URL}/api/...`。
- service 单元测试能证明路径、method、payload 不变。

## 5. 测试与验证

### 本地构建

```powershell
cd D:\QPO-Operations-Management-System\quote-frontend
npm run build
```

预期：

- Vite 构建成功。
- 不出现 Pinia、Router、Axios import 错误。

### 代码扫描

```powershell
cd D:\QPO-Operations-Management-System
rg -n "from '../auth'|from './auth'|authState|setAuth|clearAuth|updateAuthUser|verifyAuth|hasRole" quote-frontend/src
rg -n "import axios|axios\\." quote-frontend/src
rg -n "localStorage\\.getItem\\(`erp_global_config|localStorage\\.setItem\\(`erp_global_config" quote-frontend/src
```

预期：

- 第一条认证扫描最终无旧 `src/auth.js` 消费方。
- 第二条 Axios 扫描最终只剩 `src/api.js` 或经确认保留的兼容文件。
- 第三条全局配置扫描最终只剩 `globalConfigStore.js`。

### Docker Compose 验证

ERP 当前仍是无 Redis 版本，验证命令保持：

```powershell
cd D:\QPO-Operations-Management-System
docker compose --env-file erp.env up -d --build
docker compose --env-file erp.env ps
```

预期：

- `quote-postgres` healthy。
- `quote-backend` healthy。
- `quote-frontend` healthy。
- 前端访问 `http://127.0.0.1:8088` 正常。

### 手工回归清单

- 管理员登录后进入市场首页。
- 市场角色看不到工程和系统管理菜单。
- 工程角色默认进入工程设置页。
- 刷新页面后权限菜单仍正确。
- 未登录直接访问 `/pricing` 会跳转登录页并带 redirect。
- 初始密码账号触发强制修改密码。
- 工程设置页选择订单、保存全局配置、跳转逐件核价。
- 逐件核价读取全局材料、形状、工艺路线。
- 材料/工艺管理页新增、编辑、删除后，相关选择列表刷新。
- RFQ 导入、报价流程、议价提单、导出报价单路径不变。

## 6. 风险与控制

### 风险 1：Pinia 初始化顺序影响路由守卫

控制：

- `main.js` 中先创建 `pinia`，再安装 router。
- 路由守卫内使用 `useAuthStore()`，不要在模块顶层提前读取未初始化 store。

### 风险 2：Axios 拦截器拿不到最新 token

控制：

- `configureApiAuth({ getToken })` 使用函数延迟读取 `authStore.token`。
- 不在创建 `apiClient` 时直接固定 token。

### 风险 3：全局配置缓存迁移导致历史订单配置丢失

控制：

- 保持旧 key 不变。
- `globalConfigStore` 同时维护订单级 key 和 fallback key。
- 后端 `engineerConfig` 优先级高于本地 fallback。

### 风险 4：字典缓存导致维护页保存后其他页面读到旧数据

控制：

- 字典 CRUD 成功后统一调用 `dictionaryStore.reloadAfterMutation()`。
- 关键页面进入时可用 `force: true` 强刷。

### 风险 5：一次性迁移业务 API 过大

控制：

- 按 service 文件分批提交。
- 每批迁移后运行 build 和对应手工回归。
- 页面行为不变，路径和 payload 由测试保护。

## 7. 建议提交拆分

1. `feat: add ERP pinia and api client`
2. `refactor: migrate ERP auth state to pinia`
3. `refactor: centralize ERP quote global config state`
4. `refactor: centralize ERP dictionary state`
5. `refactor: split ERP business API services`
6. `test: cover ERP service request contracts`

## 8. 完成标准

- `quote-frontend` 已安装并注册 Pinia。
- 认证状态由 `authStore` 管理，旧 `src/auth.js` 被删除或不再被引用。
- 登录、登出、权限路由、强制改密行为与重构前一致。
- 全局报价配置只由 `globalConfigStore` 读写本地缓存。
- 字典数据由 `dictionaryStore` 统一加载和刷新。
- 主要业务 API 已拆到 `src/services/*.js`。
- `npm run build` 通过。
- ERP Docker Compose 无 Redis 架构启动成功并 healthy。
- 手工回归清单通过。

## 9. 推荐执行策略

推荐按阶段实施、每阶段单独验证和提交。虽然目标是“方案 C：完整 service 层重构”，但 ERP 的认证、报价配置、字典数据牵涉路由守卫和核心报价流程，最稳的做法是一次性设计到位、分阶段落地。这样最终结构完整，过程中也能随时回滚到上一阶段。
