import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order U9 sync module', () => {
  test('registers an independent protected U9 sync route', () => {
    const router = readSource('router', 'index.js')

    expect(router).toContain("const SalesOrderU9Sync = () => import('../components/SalesOrderU9Sync.vue')")
    expect(router).toContain("{ path: '/u9-sync', component: SalesOrderU9Sync, meta: { permission: 'market:u9-sync:view' } }")
  })

  test('places U9 sync between release and delivery documents in the market sidebar', () => {
    const app = readSource('App.vue')

    expect(app).toContain("paths: ['/market', '/sales-match', '/quote-process', '/release', '/u9-sync', '/delivery-documents'")
    expect(app).toContain("'/u9-sync': { title: 'U9同步', permission: 'market:u9-sync:view'")

    const marketGroupIndex = app.indexOf("{ key: 'market'")
    const releaseIndex = app.indexOf("'/release'", marketGroupIndex)
    const u9SyncIndex = app.indexOf("'/u9-sync'", marketGroupIndex)
    const deliveryDocumentIndex = app.indexOf("'/delivery-documents'", marketGroupIndex)

    expect(releaseIndex).toBeGreaterThan(-1)
    expect(u9SyncIndex).toBeGreaterThan(releaseIndex)
    expect(deliveryDocumentIndex).toBeGreaterThan(u9SyncIndex)
  })

  test('renders the full sales-order-only U9 sync workspace', () => {
    const componentPath = resolve(currentDir, 'components', 'SalesOrderU9Sync.vue')
    expect(existsSync(componentPath)).toBe(true)

    const component = readSource('components', 'SalesOrderU9Sync.vue')
    expect(component).toContain('class="erp-page sales-order-u9-sync-page"')
    expect(component).toContain('class="erp-page-header sales-order-u9-sync-header"')
    expect(component).toContain('<h1 class="erp-page-title">U9同步</h1>')
    expect(component).toContain('class="erp-toolbar"')
    expect(component).toContain('销售订单列表')
    expect(component).toContain('U9同步预览')
    expect(component).toContain('U9同步结果')
    expect(component).toContain('同步就绪摘要')
    expect(component).toContain('阻断原因')
    expect(component).toContain('失败明细')
    expect(component).toContain('销售订单')
    expect(component).not.toContain('RFQ')
    expect(component).not.toContain('sales-order-u9-sync-placeholder')
  })

  test('uses U9 sync API helpers and backend paging instead of client-side full loading', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('listSalesOrderU9SyncOrders')
    expect(component).toContain('previewSalesOrderU9Sync')
    expect(component).toContain('syncSalesOrderU9')
    expect(component).toContain('page: orderPagination.page')
    expect(component).toContain('pageSize: orderPagination.pageSize')
    expect(component).toContain('v-model:current-page="orderPagination.page"')
    expect(component).toContain('v-model:page-size="orderPagination.pageSize"')
    expect(component).toContain('@current-change="handleOrderPageChange"')
    expect(component).toContain('@size-change="handleOrderPageSizeChange"')
  })

  test('gates submit action and disables sync when the selected order is blocked', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain("const U9_SYNC_SUBMIT_PERMISSION = 'market:u9-sync:submit'")
    expect(component).toContain('v-permission="U9_SYNC_SUBMIT_PERMISSION"')
    expect(component).toContain(':disabled="!canSyncSelected"')
    expect(component).toContain('syncDisabledReason')
    expect(component).toContain('blockedReason')
    expect(component).toContain('materialReadyStatus')
  })

  test('shows sales order U9 fields, material readiness, return result and failure details', () => {
    const component = readSource('components', 'SalesOrderU9Sync.vue')

    expect(component).toContain('prop="salesOrderNo" label="销售订单号"')
    expect(component).toContain('prop="customerName" label="客户"')
    expect(component).toContain('prop="customerOrderNo" label="客户订单号"')
    expect(component).toContain('prop="internalProjectNo" label="内部项目号"')
    expect(component).toContain('label="零件数"')
    expect(component).toContain('label="已确认物料号"')
    expect(component).toContain('prop="u9SyncStatus" label="U9状态"')
    expect(component).toContain('prop="u9OrderCode" label="U9编码"')
    expect(component).toContain('prop="itemCode" label="ItemCode"')
    expect(component).toContain('prop="materialNoStatus" label="物料号状态"')
    expect(component).toContain('prop="errorMessage" label="失败明细"')
  })
})
