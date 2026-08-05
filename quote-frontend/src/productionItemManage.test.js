import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pagePath = resolve(currentDir, 'components', 'ProductionItemManage.vue')
const pageSource = existsSync(pagePath) ? readFileSync(pagePath, 'utf8') : ''
const routerSource = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')
const appSource = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')
const orderApiSource = readFileSync(resolve(currentDir, 'services', 'orderApi.js'), 'utf8')

describe('ProductionItemManage split workbench', () => {
  test('wires production item route, menu permission, and customer scope', () => {
    expect(pageSource).toContain('<h1 class="erp-page-title">待产零件</h1>')
    expect(routerSource).toContain('ProductionItemManage')
    expect(routerSource).toContain("path: '/production-items'")
    expect(routerSource).toContain("permission: 'market:production-item:view'")
    expect(appSource).toContain("'/production-items': { title: '待产零件', permission: 'market:production-item:view'")
    expect(orderApiSource).toContain("'production-item': '/api/order-delivery/production-items/customers'")
    expect(pageSource).toContain('customer-scope="production-item"')
    expect(pageSource).toContain(`v-permission="'market:production-item:customer-select'"`)
  })

  test('loads only production item rows through the production item API', () => {
    expect(apiSource).toContain('listProductionItems')
    expect(apiSource).toContain("request('/api/order-delivery/production-items', { params })")
    expect(pageSource).toContain('listProductionItems')
    expect(pageSource).toContain('const response = await listProductionItems(buildParams())')
    expect(pageSource).not.toContain('listLockedItems')
    expect(pageSource).not.toContain("request('/api/order-delivery/locked-items'")
  })

  test('owns MES production dispatch interaction and batch result summary', () => {
    expect(pageSource).toContain('PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION')
    expect(pageSource).toContain('market:production-item:mes-production-submit')
    expect(pageSource).toContain('v-permission="PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION"')
    expect(pageSource).toContain('下发MES生产')
    expect(pageSource).toContain('@selection-change="handleSelectionChange"')
    expect(pageSource).toContain('<el-table-column type="selection" width="46" fixed align="center" />')
    expect(pageSource).toContain('submitMesProductionDispatchBatch')
    expect(pageSource).toContain('await submitMesProductionDispatchBatch(selectedAllocationIds.value)')
    expect(pageSource).toContain("mesProductionDispatch: () => 'production-item:mes-production:batch'")
    expect(pageSource).toContain('MES生产下发结果')
    expect(pageSource).toContain('successCount')
    expect(pageSource).toContain('failedCount')
    expect(pageSource).toContain('skippedCount')
  })

  test('hydrates production status filters from dashboard route query', () => {
    expect(pageSource).toContain('normalizeRouteQueryValue')
    expect(pageSource).toContain('syncFiltersFromRouteQuery')
    expect(pageSource).toContain('filters.mesProductionStatus = normalizeRouteQueryValue(route.query.mesProductionStatus)')
    expect(pageSource).toContain('watch(() => route.query.mesProductionStatus')
  })

  test('shows production status filters but never locked stock type filters', () => {
    expect(pageSource).toContain('mesProductionStatus')
    expect(pageSource).toContain("{ value: 'PENDING', label: '待下发' }")
    expect(pageSource).toContain("{ value: 'PROCESSING', label: '下发中' }")
    expect(pageSource).toContain("{ value: 'FAILED', label: '下发失败' }")
    expect(pageSource).not.toContain("{ value: 'SUCCESS'")
    expect(pageSource).not.toContain('IN_STOCK_LOCK')
    expect(pageSource).not.toContain('IN_PRODUCTION_LOCK')
  })

  test('keeps responsive workbench layout and original ticket tracing', () => {
    expect(pageSource).toContain('production-toolbar-main')
    expect(pageSource).toContain('production-primary-filters')
    expect(pageSource).toContain('production-batch-actions')
    expect(pageSource).toContain('production-advanced-filters')
    expect(pageSource).toContain('@media (max-width: 1200px)')
    expect(pageSource).toContain('@media (max-width: 720px)')
    expect(pageSource).toContain('getDeliveredItem')
    expect(pageSource).toContain('openTicketDetail')
    expect(pageSource).toContain('market:delivered-item:view')
  })
})
