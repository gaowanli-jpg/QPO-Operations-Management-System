import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { fileURLToPath } from 'node:url'
import { dirname, resolve } from 'node:path'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales match module', () => {
  test('adds sales match route guarded by its own menu permission', () => {
    const router = readSource('router', 'index.js')

    expect(router).toContain("const SalesMatch = () => import('../components/SalesMatch.vue')")
    expect(router).toContain("{ path: '/sales-match', component: SalesMatch, meta: { permission: 'market:sales-match:view' } }")
  })

  test('places sales match between new import and quote process in the market sidebar', () => {
    const app = readSource('App.vue')

    expect(app).toContain("paths: ['/market', '/sales-match', '/quote-process'")
    expect(app).toContain("'/sales-match': { title: '销售匹配', permission: 'market:sales-match:view'")

    const importIndex = app.indexOf("'/market': { title: '新单导入'")
    const salesMatchIndex = app.indexOf("'/sales-match': { title: '销售匹配'")
    const quoteProcessIndex = app.indexOf("'/quote-process': { title: '报价处理'")

    expect(importIndex).toBeGreaterThanOrEqual(0)
    expect(salesMatchIndex).toBeGreaterThan(importIndex)
    expect(quoteProcessIndex).toBeGreaterThan(salesMatchIndex)
  })

  test('creates a standard sales match page using QPO layout', () => {
    const componentPath = resolve(currentDir, 'components', 'SalesMatch.vue')
    expect(existsSync(componentPath)).toBe(true)

    const component = readSource('components', 'SalesMatch.vue')
    expect(component).toContain('class="erp-page sales-match-page"')
    expect(component).toContain('class="erp-page-header sales-match-header"')
    expect(component).toContain('<h1 class="erp-page-title">销售匹配</h1>')
    expect(component).toContain('workflow-workspace sales-match-workspace')
    expect(component).toContain('sales-match-side-panel')
    expect(component).toContain('sales-match-main-panel')
    expect(component).not.toContain('class="erp-toolbar"')
  })

  test('sales match page uses the shared sales-order picker as the only order entry', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).toContain("import OrderPickerDialog from './OrderPickerDialog.vue'")
    expect(component).toContain('<OrderPickerDialog')
    expect(component).toContain('customer-scope="sales-match"')
    expect(component).toContain('order-scope="sales-match"')
    expect(component).toContain(':allowed-statuses="SALES_MATCH_ALLOWED_STATUSES"')
    expect(component).toContain("const SALES_MATCH_ALLOWED_STATUSES = ['已报价']")
    expect(component).toContain('openOrderPicker')
    expect(component).toContain('handleOrderSelected')
    expect(component).toContain('orderPickerVisible')
    expect(component).toContain('选择销售订单')
    expect(component).not.toContain('选择RFQ')
    expect(component).not.toContain('RFQ订单选择')
    expect(component).not.toContain('询价单选择')
  })

  test('sales match page removes the old in-page order and item workbench', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).not.toContain('sales-match-orders')
    expect(component).not.toContain('sales-match-items')
    expect(component).not.toContain('selectedItemIds')
    expect(component).not.toContain('selectedItems')
    expect(component).not.toContain('matchOptions.onlyUnmatched')
    expect(component).not.toContain('listSalesMatchOrders')
    expect(component).not.toContain('listSalesMatchItems')
    expect(component).not.toContain('orderPagination')
    expect(component).not.toContain('itemPagination')
    expect(component).not.toContain('<el-table-column type="selection"')
    expect(component).not.toContain('paginateRows(')
  })

  test('sales match actions are permission-gated and use order-level APIs', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).toContain("const SALES_MATCH_APPLY_PERMISSION = 'market:sales-match:apply'")
    expect(component).toContain("const SALES_MATCH_ORDER_SELECT_PERMISSION = 'market:sales-match:order-select'")
    expect(component).toContain('v-permission="SALES_MATCH_ORDER_SELECT_PERMISSION"')
    expect(component).toContain('v-permission="SALES_MATCH_APPLY_PERMISSION"')
    expect(component).toContain('previewSalesOrderMatchByOrder(selectedOrderId.value)')
    expect(component).toContain('applySalesOrderMatchByOrder(selectedOrderId.value)')
    expect(component).toContain('await previewSelectedOrder({ silent: true, preserveResult: true })')
    expect(component).not.toContain('buildMatchPayload')
    expect(component).not.toContain('salesOrderItemIds')
  })

  test('sales match page shows selected order, match condition, preview summary, and apply result', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).toContain('已选销售订单')
    expect(component).toContain('匹配条件')
    expect(component).toContain('订单信息')
    expect(component).toContain('previewSummary')
    expect(component).toContain('applyResult')
    expect(component).toContain('warningMessage')
    expect(component).toContain('matchedItemCount')
    expect(component).toContain('unmatchedItemCount')
    expect(component).toContain('补全结果')
    expect(component).toContain('整单零件预览')
    expect(component).toContain('命中 {{ previewSummary.matchedItemCount }}')
    expect(component).toContain('未匹配 {{ previewSummary.unmatchedItemCount }}')
  })

  test('sales match order-level flow auto previews, refreshes, and applies without item scope', () => {
    const component = readSource('components', 'SalesMatch.vue')
    const api = readSource('services', 'marketApi.js')
    const apiTest = readSource('services', 'marketApi.test.js')

    expect(component).toContain('await previewSelectedOrder({ silent: true })')
    expect(component).toContain('await previewSelectedOrder({ silent: false, preserveResult: true })')
    expect(component).toContain('await previewSelectedOrder({ silent: true, preserveResult: true })')
    expect(component).toContain('确认对销售订单')
    expect(component).toContain('整单补全空缺生产快照')
    expect(component).not.toContain('覆盖字段')
    expect(api).toContain('previewSalesOrderMatchByOrder')
    expect(api).toContain('applySalesOrderMatchByOrder')
    expect(apiTest).toContain("'/api/market/sales-match/orders/101/preview'")
    expect(apiTest).toContain("'/api/market/sales-match/orders/101/apply'")
  })

  test('sales match page frames matching as filling production snapshots without overwrite options', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).not.toContain('overwriteExistingProductionSnapshot')
    expect(component).not.toContain('overwriteTemplateMaterial')
    expect(component).not.toContain('覆盖已有生产快照')
    expect(component).not.toContain('覆盖模板材料/表面处理')
    expect(component).not.toContain('覆盖导入材料')
    expect(component).toContain('导入材料')
    expect(component).toContain('真实材料')
    expect(component).toContain('材料形态')
    expect(component).toContain('空缺生产快照')
    expect(component).toContain('保护字段摘要')
    expect(component).toContain('补全生产快照')
    expect(component).toContain('applySalesOrderMatchByOrder(selectedOrderId.value)')
    expect(component).not.toContain('applyAllMatchedInSelectedOrders')
  })
})
