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
    expect(component).toContain('class="erp-toolbar"')
    expect(component).toContain('class="erp-surface"')
  })

  test('sales match page only exposes sales order selection and no RFQ order picker', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).toContain('销售订单筛选')
    expect(component).toContain('销售订单列表')
    expect(component).toContain('销售订单号')
    expect(component).not.toContain('选择RFQ')
    expect(component).not.toContain('RFQ订单选择')
    expect(component).not.toContain('询价单选择')
  })

  test('sales match page uses backend pagination for orders and items', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).toContain('listSalesMatchOrders(buildOrderParams())')
    expect(component).toContain('listSalesMatchItems(selectedOrderId.value, buildItemParams())')
    expect(component).toContain('v-model:current-page="orderPagination.page"')
    expect(component).toContain('v-model:page-size="orderPagination.pageSize"')
    expect(component).toContain('v-model:current-page="itemPagination.page"')
    expect(component).toContain('v-model:page-size="itemPagination.pageSize"')
    expect(component).toContain('page: orderPagination.page')
    expect(component).toContain('pageSize: orderPagination.pageSize')
    expect(component).toContain('page: itemPagination.page')
    expect(component).toContain('pageSize: itemPagination.pageSize')
    expect(component).not.toContain('paginateRows(')
  })

  test('sales match actions are permission-gated and refresh state after preview and apply', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).toContain("const SALES_MATCH_APPLY_PERMISSION = 'market:sales-match:apply'")
    expect(component).toContain('v-permission="SALES_MATCH_APPLY_PERMISSION"')
    expect(component).toContain('previewSalesOrderMatch(buildMatchPayload())')
    expect(component).toContain('applySalesOrderMatch({')
    expect(component).toContain('await refreshAfterApply()')
    expect(component).toContain('await loadOrders()')
    expect(component).toContain('await loadItems()')
  })

  test('sales match page shows preview summary, warnings, and apply result', () => {
    const component = readSource('components', 'SalesMatch.vue')

    expect(component).toContain('previewSummary')
    expect(component).toContain('applyResult')
    expect(component).toContain('warningMessage')
    expect(component).toContain('matchedItemCount')
    expect(component).toContain('unmatchedItemCount')
    expect(component).toContain('补全结果')
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
    expect(component).toContain('补全空缺生产快照')
    expect(component).toContain('applySalesOrderMatch({')
    expect(component).toContain('applyAllMatchedInSelectedOrders: matchOptions.onlyUnmatched')
  })
})
