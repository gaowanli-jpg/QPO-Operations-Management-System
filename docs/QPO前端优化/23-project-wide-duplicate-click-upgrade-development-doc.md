# QPO Project-Wide Duplicate Click Guard Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 对 QPO 前端所有耗时操作、写操作、导入导出、外部系统同步和异步任务入口进行统一升级，防止用户误重复点击导致重复请求、重复提交或状态错乱。

**Architecture:** 前端用统一的 pending/action guard 管理按钮级、行级、区域级 loading 和 disabled 状态；页面按业务对象控制禁用范围，避免过度全页锁定。后端仍保留关键写接口的状态校验和重复提交保护，前端只作为第一层交互防线。

**Tech Stack:** Vue 3 Composition API, Element Plus, Pinia, Axios, Vitest, QPO 既有 `erp-*` 页面样式体系。

## Global Constraints

- 以 `docs/QPO前端优化/22-action-loading-and-duplicate-guard.md` 为交互规则源。
- 只升级防重复点击、loading、disabled、任务状态反馈，不改变业务流程和权限模型。
- 不引入新的 UI 库，不替换 Element Plus。
- 不做全局粗暴遮罩；默认优先按钮级、行级、区域级 loading。
- 写操作、外部同步、异步任务触发必须防重复点击。
- 查询、筛选、翻页只做局部 loading，不阻塞无关操作。
- 后端关键写接口仍必须校验状态，前端防重复不能作为唯一防线。
- QPO 兼容命名继续保留：`erp-*`, `ERP_*`, `erp.env`, `erp_user` 等不改名。

---

## 参考文档

- `docs/QPO前端优化/22-action-loading-and-duplicate-guard.md`：交互规则和验收标准。
- `docs/QPO前端优化/06-component-guidelines.md`：按钮、表格、弹窗、loading 基础规范。
- `docs/QPO前端优化/09-acceptance-checklist.md`：UI 验收清单。
- `AGENTS.md`：QPO 边界、权限、日志和开发节奏要求。

## 升级范围

### 必须升级的操作

- 写入类：保存、提交、完成、回滚、作废、恢复、删除、重置、状态启停。
- 文件类：导入 RFQ、上传/替换/补充图纸、上传报价模板、导出文件。
- 批量类：生成提单、保存报价单、批量设置交期、刷新核价。
- 外部系统类：MES 物料号同步、WMS 锁库、后续对外系统推送。
- 异步任务类：所有返回 `taskId` 的任务提交按钮。

### 不需要强防重复的操作

- 只读详情、打开弹窗、打开图纸预览。
- 列表筛选、翻页、普通刷新。
- 客户/订单选择器加载、字典下拉加载。

这些操作仍应有局部 loading，但不需要禁用整个页面。

## 文件职责设计

### 新增文件

- `quote-frontend/src/composables/usePendingAction.js`
  - 负责统一管理 pending action。
  - 支持按钮级 key、行级 key、区域级 key。
  - 提供 `isPending(key)`, `runAction(key, fn)`, `setPending(key, value)`。

- `quote-frontend/src/composables/usePendingAction.test.js`
  - 验证重复调用被拦截。
  - 验证异常后状态恢复。
  - 验证多个 key 互不影响。

- `quote-frontend/src/utils/actionError.js`
  - 统一提取接口错误文案。
  - 避免各页面重复写 `error.response?.data?.message || error.message`。

### 修改文件

- `quote-frontend/src/api.js`
  - 保持现有请求封装，不在全局 axios 层自动拦截重复请求。
  - 原因：同一接口可能因为参数不同而合法并发，重复防护应在业务动作层判断。

- `quote-frontend/src/services/*.js`
  - 原则上不加 loading 状态。
  - 服务层只保持 API 调用职责，防重复逻辑放在页面或 composable。

- `quote-frontend/src/components/*.vue`
  - 为写操作、导入导出、外部同步、异步任务提交接入 pending guard。
  - 页面内保留现有业务判断、权限判断和状态判断。

## 推荐实现接口

### `usePendingAction`

```js
import { computed, ref } from 'vue'

export const usePendingAction = () => {
  const pendingKeys = ref(new Set())

  const isPending = (key) => pendingKeys.value.has(String(key))

  const setPending = (key, value) => {
    const next = new Set(pendingKeys.value)
    const normalizedKey = String(key)
    if (value) {
      next.add(normalizedKey)
    } else {
      next.delete(normalizedKey)
    }
    pendingKeys.value = next
  }

  const runAction = async (key, action) => {
    if (isPending(key)) return undefined
    setPending(key, true)
    try {
      return await action()
    } finally {
      setPending(key, false)
    }
  }

  const anyPending = computed(() => pendingKeys.value.size > 0)

  return {
    anyPending,
    isPending,
    runAction,
    setPending
  }
}
```

### Action key 命名

按业务对象和动作命名，保证可读、可定位。

```js
const actionKey = {
  importRfq: 'market:rfq:import',
  generateTicket: (rfqId) => `market:release:generate-ticket:${rfqId}`,
  saveQuote: (rfqId) => `market:quote-process:save-quote:${rfqId}`,
  mesSync: (ticketId) => `delivery:mes-sync:${ticketId}`,
  wmsAllocation: (ticketId) => `delivery:wms-allocation:${ticketId}`,
  deleteOrder: (rfqId) => `order:delete:${rfqId}`
}
```

### 页面使用模式

```js
const { isPending, runAction } = usePendingAction()

const handleGenerateTicket = async () => {
  const key = `market:release:generate-ticket:${selectedOrderId.value}`
  await runAction(key, async () => {
    const response = await generateTicket(payload)
    ElMessage.success(response?.message || '提单已生成')
    await loadTickets()
  })
}
```

```vue
<el-button
  type="primary"
  :loading="isPending(`market:release:generate-ticket:${selectedOrderId}`)"
  :disabled="isPending(`market:release:generate-ticket:${selectedOrderId}`)"
  @click="handleGenerateTicket"
>
  生成提单
</el-button>
```

## 分阶段开发任务

### Task 1: 建立统一 pending action 基础能力

**Files:**
- Create: `quote-frontend/src/composables/usePendingAction.js`
- Create: `quote-frontend/src/composables/usePendingAction.test.js`
- Create: `quote-frontend/src/utils/actionError.js`

**Produces:**
- `usePendingAction()`
- `isPending(key)`
- `runAction(key, action)`
- `setPending(key, value)`
- `readActionError(error, fallback)`

- [ ] 写 `usePendingAction.test.js`，覆盖重复点击、异常恢复、多 key 并发。
- [ ] 实现 `usePendingAction.js`。
- [ ] 实现 `actionError.js`。
- [ ] 运行 `cd quote-frontend && npm test -- usePendingAction`。
- [ ] 确认新增工具不引入页面行为变化。

### Task 2: 市场 RFQ 导入和订单释放页升级

**Files:**
- Modify: `quote-frontend/src/components/MarketDashboard.vue`
- Modify: `quote-frontend/src/components/MarketRelease.vue`
- Modify: `quote-frontend/src/services/marketApi.test.js` if API behavior assumptions need locking
- Add or update page tests near existing `marketRelease*.test.js`

**Target actions:**
- RFQ 导入。
- 解析零件选择。
- 批量解析零件选择。
- 替换释放图纸。
- 更新 release 版本。
- 批量设置/移动内部目标交期。
- 生成提单。

- [ ] 为 RFQ 导入按钮增加 `importRfq` pending key。
- [ ] 为 MarketRelease 的生成提单增加订单级 pending key。
- [ ] 为批量交期和版本更新增加区域级或行级 pending key。
- [ ] 确认执行中禁用同一订单的冲突动作。
- [ ] 测试连续点击只触发一次 API。
- [ ] 运行 `cd quote-frontend && npm test -- marketRelease`。

### Task 3: 市场报价处理页升级

**Files:**
- Modify: `quote-frontend/src/components/QuoteProcess.vue`
- Modify: `quote-frontend/src/services/marketApi.test.js` if needed
- Add or update `quoteProcess*.test.js` / `largeOrderItemPaging.test.js`

**Target actions:**
- 保存报价单。
- 导出报价单。
- 批量设置报价交期。
- 解析报价处理零件选择。

- [ ] 保存报价单使用订单级 pending key。
- [ ] 导出动作使用按钮级 loading，下载完成或失败后恢复。
- [ ] 交期批量操作使用区域级 pending key。
- [ ] 筛选、翻页继续只使用表格 loading。
- [ ] 测试保存报价单连续点击只提交一次。

### Task 4: 工程设置和零件核价工作台升级

**Files:**
- Modify: `quote-frontend/src/components/GlobalSetup.vue`
- Modify: `quote-frontend/src/components/ItemPricing.vue`
- Modify: `quote-frontend/src/services/engineerApi.test.js` if needed
- Add or update `globalConfigStore.test.js`, `itemPricing*.test.js`

**Target actions:**
- 保存整单默认设定。
- 自动套用重复报价。
- 保存单个零件核价。
- 刷新整单核价任务。
- 完成订单核价。
- 导出核价表。

- [ ] 保存整单默认设定使用订单级 pending key。
- [ ] 保存单件核价使用零件级 pending key。
- [ ] 自动套用重复报价执行中禁用重复触发入口。
- [ ] 刷新整单核价如果返回 `taskId`，按钮 loading 只持续到任务提交成功，后续由任务状态展示接管。
- [ ] 完成订单核价执行中禁用保存、刷新、完成等同订单冲突动作。
- [ ] 测试连续点击保存单件核价只调用一次 `submitQuote`。

### Task 5: 提单、已提零件、锁库零件和外部同步升级

**Files:**
- Modify: `quote-frontend/src/components/DeliveryDocumentManage.vue`
- Modify: `quote-frontend/src/components/DeliveredItemManage.vue`
- Modify: `quote-frontend/src/components/LockedItemManage.vue`
- Modify: `quote-frontend/src/services/deliveredItemApi.test.js` if needed
- Add or update `deliveredItem*.test.js`, `lockedItemManage.test.js`

**Target actions:**
- 导出提单/工艺表。
- 单行同步 MES 物料号。
- 单行提交 WMS 锁库。
- 查看锁库结果入口。

- [ ] MES 同步使用 `delivery:mes-sync:{ticketId}` 行级 pending key。
- [ ] WMS 锁库使用 `delivery:wms-allocation:{ticketId}` 行级 pending key。
- [ ] 同一行 MES/WMS 执行期间禁用同一行冲突动作。
- [ ] 导出动作使用按钮级 loading。
- [ ] 锁库列表查询继续使用表格 loading，不锁页面。
- [ ] 测试连续点击同一行 WMS 锁库只触发一次 `submitWmsAllocation`。

### Task 6: 订单管理和历史订单页升级

**Files:**
- Modify: `quote-frontend/src/components/OrderManage.vue`
- Modify: `quote-frontend/src/components/HistoryOrderManage.vue`
- Modify: `quote-frontend/src/services/orderApi.test.js` if needed
- Add or update `orderStatusManage.test.js`

**Target actions:**
- 删除订单。
- 回滚已报价订单。
- 回滚已核价订单。
- 作废订单。
- 恢复订单。
- 补充图纸。

- [ ] 删除、回滚、作废、恢复使用订单级 pending key。
- [ ] 确认弹窗确认后立即进入 pending 状态。
- [ ] 操作执行中禁用同一订单其它状态变更按钮。
- [ ] 补充图纸上传使用订单级或弹窗级 pending key。
- [ ] 测试重复点击状态操作只提交一次。

### Task 7: 系统管理、字典、模板和日志页升级

**Files:**
- Modify: `quote-frontend/src/components/UserManage.vue`
- Modify: `quote-frontend/src/components/SystemRoleManage.vue`
- Modify: `quote-frontend/src/components/SystemOrgManage.vue`
- Modify: `quote-frontend/src/components/MaterialProcessManage.vue`
- Modify: `quote-frontend/src/components/QuoteTemplateManage.vue`
- Modify: `quote-frontend/src/components/AuditLogManage.vue`
- Modify matching `adminApi.test.js`, `dictApi.test.js`, `auditApi.test.js` only if API call expectations need coverage

**Target actions:**
- 用户保存、重置密码、启停、删除。
- 角色保存、授权、数据范围保存、启停、删除。
- 部门/岗位保存、启停、删除。
- 材料、工艺、费用字典维护。
- 模板上传保存。
- 日志导出、日志保留策略保存、立即清理。

- [ ] 表单保存类使用弹窗级 pending key。
- [ ] 行级启停/删除使用行级 pending key。
- [ ] 授权和数据范围保存使用抽屉级 pending key。
- [ ] 日志导出使用按钮级 loading。
- [ ] 日志立即清理使用确认弹窗 + 按钮级 loading。
- [ ] 测试管理页关键写操作有 loading/disabled 绑定。

### Task 8: 后端关键接口重复提交保护复核

**Files:**
- Review: `quote-backend/src/main/java/com/machining/quote/controller`
- Review: `quote-backend/src/main/java/com/machining/quote/service`
- Review: `quote-backend/src/test/java/com/machining/quote`

**Target backend areas:**
- 生成提单。
- 保存报价单。
- 刷新核价任务。
- MES 物料号同步。
- WMS 锁库。
- 删除、回滚、作废、恢复订单。
- 用户、角色、权限和日志治理操作。

- [ ] 确认每个关键写接口有状态校验。
- [ ] 确认异步任务提交有 PROCESSING/SUCCESS 防重复逻辑。
- [ ] 确认失败返回清晰 message，前端可直接展示。
- [ ] 如果发现后端缺少保护，新增后端测试再补实现。
- [ ] 运行相关后端测试，至少覆盖被补强接口。

### Task 9: 全项目验收和回归

**Files:**
- Modify: `docs/QPO前端优化/09-acceptance-checklist.md` only if发现新验收项
- Modify: `docs/QPO前端优化/22-action-loading-and-duplicate-guard.md` only if规则需要调整

- [ ] 搜索所有 `@click=`，确认写操作都有 pending 或既有 loading 防护。
- [ ] 搜索所有 `method: 'POST'`, `PUT`, `DELETE`，确认对应页面入口有防重复。
- [ ] 运行 `cd quote-frontend && npm test`。
- [ ] 运行 `cd quote-frontend && npm run build`。
- [ ] 对重点流程做浏览器手工验证：导入 RFQ、工程设置、单件核价、生成提单、MES 同步、WMS 锁库、订单回滚/作废。
- [ ] 记录未覆盖项和原因，不允许静默遗留高风险写操作。

## 页面优先级

### P0 必须第一批完成

- `MarketDashboard.vue`：RFQ 导入。
- `MarketRelease.vue`：生成提单、批量交期、图纸替换。
- `GlobalSetup.vue`：保存整单默认设定。
- `ItemPricing.vue`：保存单件核价、刷新核价、完成订单核价。
- `DeliveredItemManage.vue`：MES 同步、WMS 锁库。
- `OrderManage.vue`：删除、回滚、作废、恢复、补图。

### P1 第二批完成

- `QuoteProcess.vue`：保存报价单、导出、批量交期。
- `DeliveryDocumentManage.vue`：导出提单/工艺表。
- `QuoteTemplateManage.vue`：模板上传。
- `MaterialProcessManage.vue`：字典维护。

### P2 第三批完成

- `UserManage.vue`
- `SystemRoleManage.vue`
- `SystemOrgManage.vue`
- `AuditLogManage.vue`
- `HistoryOrderManage.vue`
- 选择器和只读弹窗类组件。

## 测试策略

### 单元测试

重点测工具函数：

- `runAction` 在 pending 时不会重复执行。
- `runAction` 成功后清理 pending。
- `runAction` 异常后仍清理 pending。
- 不同 key 可并行执行。

### 页面测试

页面测试不要只断言按钮存在，要断言行为：

- 连续触发 handler 时 API 只调用一次。
- loading 状态时按钮有 `:loading` 或 `:disabled` 绑定。
- 异常后按钮可恢复。
- 行级操作只影响当前行，不影响其它行。

### 回归测试

必须跑：

```powershell
cd quote-frontend
npm test
npm run build
```

涉及后端状态保护补强时，额外跑对应后端测试：

```powershell
cd quote-backend
.\mvnw.cmd test
```

如果只改局部后端接口，优先跑精确测试类，避免拖慢验证。

## 验收标准

- 所有 P0 写操作都不能通过连续点击发起重复请求。
- 所有 P0 外部同步操作都有行级 loading 和防重复点击。
- 所有 P0 文件导入/上传/导出动作都有按钮级 loading。
- 所有异步任务入口提交成功后能看到任务状态反馈。
- 查询、筛选、翻页仍然可用，不被全页 loading 过度阻塞。
- 异常后 loading 状态一定恢复。
- 前端测试和构建通过。
- 后端关键写接口的状态校验没有被前端改动绕过。

## 不做事项

- 不把所有按钮都统一禁用。
- 不在 axios 全局层按 URL 粗暴去重。
- 不改变权限点、菜单、角色、数据范围。
- 不改变接口路径、请求结构和返回结构，除非后端补强必须新增错误状态。
- 不把长耗时异步任务伪装成同步等待。

