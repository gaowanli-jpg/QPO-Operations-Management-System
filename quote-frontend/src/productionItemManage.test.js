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
    expect(pageSource).toContain('permission="market:production-item:customer-select"')
  })

  test('loads only production item rows through the production item API', () => {
    expect(apiSource).toContain('listProductionItems')
    expect(apiSource).toContain("request('/api/order-delivery/production-items', { params })")
    expect(pageSource).toContain('listProductionItems')
    expect(pageSource).toContain('const response = await listProductionItems(buildParams())')
    expect(pageSource).not.toContain('listLockedItems')
    expect(pageSource).not.toContain("request('/api/order-delivery/locked-items'")
  })

  test('shows the linked sales order date before part number without changing pagination', () => {
    const dateColumnIndex = pageSource.indexOf('<el-table-column prop="salesOrderDate" label="下单日期"')
    const partNumberColumnIndex = pageSource.indexOf('<el-table-column prop="partNo" label="图号"')

    expect(dateColumnIndex).toBeGreaterThanOrEqual(0)
    expect(dateColumnIndex).toBeLessThan(partNumberColumnIndex)
    expect(pageSource).toContain('v-model:current-page="page"')
    expect(pageSource).toContain('v-model:page-size="pageSize"')
    expect(pageSource).toContain(':total="total"')
  })

  test('offers larger page sizes while keeping MES selection within the task limit', () => {
    expect(pageSource).toContain(':page-sizes="[20, 50, 100, 300, 500, 1000]"')
    expect(pageSource).toContain('const pageSize = ref(100)')
    expect(pageSource).toContain('MAX_MES_PRODUCTION_BATCH_SIZE = 1000')
    expect(pageSource).not.toContain('单次最多下发 1000 条')
    expect(pageSource).toContain('filteredRecords.slice(fromIndex, fromIndex + pageSize.value)')
  })

  test('owns MES production dispatch interaction and batch result summary', () => {
    expect(pageSource).toContain('PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION')
    expect(pageSource).toContain('market:production-item:mes-production-submit')
    expect(pageSource).toContain('v-permission="PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION"')
    expect(pageSource).toContain('下发MES生产')
    expect(pageSource).toContain('@selection-change="handleSelectionChange"')
    expect(pageSource).toContain('type="selection" width="46" fixed align="center"')
    expect(pageSource).not.toContain('v-if="viewMode === \'ACTIVE\'" type="selection"')
    expect(pageSource).toContain('submitMesProductionDispatchBatch')
    expect(pageSource).toContain('await submitMesProductionDispatchBatch(selectedAllocationIds.value)')
    expect(pageSource).toContain("mesProductionDispatch: () => 'production-item:mes-production:batch'")
    expect(pageSource).toContain('MES生产下发结果')
    expect(pageSource).toContain('successCount')
    expect(pageSource).toContain('failedCount')
    expect(pageSource).toContain('skippedCount')
  })

  test('supports module-local discarded view and keeps production dispatch out of discarded rows', () => {
    expect(pageSource).toContain('viewMode')
    expect(pageSource).toContain("{ label: '待处理', value: 'ACTIVE' }")
    expect(pageSource).toContain("{ label: '已废弃', value: 'DISCARDED' }")
    expect(pageSource).toContain('params.discarded = viewMode.value === \'DISCARDED\'')
    expect(pageSource).toContain('PRODUCTION_ITEM_DISCARD_PERMISSION')
    expect(pageSource).toContain('PRODUCTION_ITEM_RESTORE_PERMISSION')
    expect(pageSource).toContain('market:production-item:discard')
    expect(pageSource).toContain('market:production-item:restore')
    expect(pageSource).toContain('handleDiscard(row)')
    expect(pageSource).toContain('handleRestore(row)')
    expect(pageSource).toContain('废弃原因')
    expect(pageSource).toContain('废弃时间')
    expect(pageSource).toContain('废弃人')
    expect(pageSource).toContain('discardProductionItem(row.id')
    expect(pageSource).toContain('restoreProductionItem(row.id')
    expect(pageSource).toContain('v-if="viewMode === \'ACTIVE\'"')
    expect(pageSource).toContain('viewMode.value === \'DISCARDED\'')
  })

  test('supports batch discard and batch restore with one reason per selected batch', () => {
    expect(apiSource).toContain('batchDiscardProductionItems')
    expect(apiSource).toContain('batchRestoreProductionItems')
    expect(pageSource).toContain('batchDiscardProductionItems')
    expect(pageSource).toContain('batchRestoreProductionItems')
    expect(pageSource).toContain('batchDiscard: () => \'production-item:discard:batch\'')
    expect(pageSource).toContain('batchRestore: () => \'production-item:restore:batch\'')
    expect(pageSource).toContain('PRODUCTION_ITEM_DISCARD_PERMISSION')
    expect(pageSource).toContain('PRODUCTION_ITEM_RESTORE_PERMISSION')
    expect(pageSource).toContain('批量废弃')
    expect(pageSource).toContain('批量恢复')
    expect(pageSource).toContain('@click="handleBatchDiscard"')
    expect(pageSource).toContain('@click="handleBatchRestore"')
    expect(pageSource).toContain('请输入批量废弃原因')
    expect(pageSource).toContain('await batchDiscardProductionItems(selectedAllocationIds.value')
    expect(pageSource).toContain('await batchRestoreProductionItems(selectedAllocationIds.value')
    expect(pageSource).toContain('selectedRows.value = []')
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
    expect(pageSource).toContain("{ value: 'RESULT_UNKNOWN'")
    expect(pageSource).toContain("{ value: 'SUCCESS'")
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

  test('does not expose removed customer demand snapshot beside production item remarks', () => {
    const removedField = 'customer' + 'Requirement'
    expect(pageSource).not.toContain(`prop="${removedField}"`)
    expect(pageSource).not.toContain(`selectedTicketDetail.${removedField}`)
  })
})

describe('ProductionItemManage daily auto dispatch policy', () => {
  test('exposes the policy dialog from the active-view toolbar behind its own permission', () => {
    expect(pageSource).toContain("const PRODUCTION_ITEM_AUTO_DISPATCH_SCHEDULE_PERMISSION = 'market:production-item:mes-production-schedule'")
    expect(pageSource).toContain('v-permission="PRODUCTION_ITEM_AUTO_DISPATCH_SCHEDULE_PERMISSION"')
    expect(pageSource).toContain('@click="openAutoDispatchDialog"')
    expect(pageSource).toContain('定时下发策略')

    const policyButtonIndex = pageSource.indexOf('v-permission="PRODUCTION_ITEM_AUTO_DISPATCH_SCHEDULE_PERMISSION"')
    const submitButtonIndex = pageSource.indexOf('v-permission="PRODUCTION_ITEM_MES_PRODUCTION_SUBMIT_PERMISSION"')
    expect(policyButtonIndex).toBeGreaterThanOrEqual(0)
    expect(policyButtonIndex).toBeLessThan(submitButtonIndex)
  })

  test('wires all three policy endpoints through the production item api', () => {
    expect(apiSource).toContain('getProductionItemAutoDispatchConfig')
    expect(apiSource).toContain("request('/api/order-delivery/production-items/auto-dispatch/config')")
    expect(apiSource).toContain('updateProductionItemAutoDispatchConfig')
    expect(apiSource).toContain("method: 'PUT'")
    expect(apiSource).toContain('runProductionItemAutoDispatchNow')
    expect(apiSource).toContain("request('/api/order-delivery/production-items/auto-dispatch/run-now', {")

    expect(pageSource).toContain('await getProductionItemAutoDispatchConfig()')
    expect(pageSource).toContain('await updateProductionItemAutoDispatchConfig({')
    expect(pageSource).toContain('await runProductionItemAutoDispatchNow()')
  })

  test('ships the agreed defaults: 17:00 daily, 200 rows per batch, 3000ms between batches', () => {
    expect(pageSource).toContain("dailyRunTime: '17:00:00'")
    expect(pageSource).toContain('batchSize: 200')
    expect(pageSource).toContain('batchIntervalMs: 3000')
    expect(pageSource).toContain('failureBreaker: 3')
    expect(pageSource).toContain('maxRounds: 50')
    expect(pageSource).toContain('warnDurationMs: 3600000')
    expect(pageSource).toContain('enabled: false')
    expect(pageSource).toContain('scheduleMode: \'DAILY_TIME\'')
  })

  test('limits only the batch size and never caps the daily total', () => {
    expect(pageSource).toContain('当天所有待下发行会全部处理完，不做每日数量限制')
    expect(pageSource).not.toContain('dailyMaxRows')
    expect(pageSource).not.toContain('daily_max_rows')
    expect(pageSource).not.toContain('每日上限')
    expect(pageSource).not.toContain('顺延到第二天')
    expect(pageSource).toContain('系统级全量下发，不受个人数据权限限制')
  })

  test('keeps advanced parameters collapsed until the operator asks for them', () => {
    expect(pageSource).toContain('const autoDispatchAdvancedOpen = ref([])')
    expect(pageSource).toContain('<el-collapse v-model="autoDispatchAdvancedOpen"')
    expect(pageSource).toContain('<el-collapse-item title="高级参数" name="advanced">')
    expect(pageSource).toContain('最大轮次')
    expect(pageSource).toContain('慢执行告警')
    expect(pageSource).toContain('仅防死循环')
    expect(pageSource).toContain('只告警不中断')
  })

  test('reports the last run result and allows an immediate manual run', () => {
    expect(pageSource).toContain('autoDispatchResultText')
    expect(pageSource).toContain('autoDispatchStatusLabel')
    expect(pageSource).toContain("NEVER: '未执行'")
    expect(pageSource).toContain("PARTIAL: '部分失败'")
    expect(pageSource).toContain('暂无执行记录')
    expect(pageSource).toContain('@click="runAutoDispatchNow"')
    expect(pageSource).toContain('立即执行一次')
    expect(pageSource).toContain('await loadAutoDispatchConfig()')
  })
})
