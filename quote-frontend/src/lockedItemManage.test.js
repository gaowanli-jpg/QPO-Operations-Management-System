import { describe, expect, test } from 'vitest'
import { existsSync, readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const pagePath = resolve(currentDir, 'components', 'LockedItemManage.vue')
const pageSource = existsSync(pagePath) ? readFileSync(pagePath, 'utf8') : ''
const routerSource = readFileSync(resolve(currentDir, 'router', 'index.js'), 'utf8')
const appSource = readFileSync(resolve(currentDir, 'App.vue'), 'utf8')
const apiSource = readFileSync(resolve(currentDir, 'services', 'deliveredItemApi.js'), 'utf8')

describe('LockedItemManage guardrails', () => {
  test('wires locked item route, menu permission, and four-character sidebar label', () => {
    expect(pageSource).toContain('<h1 class="erp-page-title">锁库零件</h1>')
    expect(routerSource).toContain('LockedItemManage')
    expect(routerSource).toContain("path: '/locked-items'")
    expect(routerSource).toContain("permission: 'market:locked-item:view'")
    expect(appSource).toContain("'/locked-items': { title: '锁库零件', permission: 'market:locked-item:view'")
  })

  test('loads locked item allocation rows through delivered item API service', () => {
    expect(apiSource).toContain('listLockedItems')
    expect(apiSource).toContain('/api/order-delivery/locked-items')
    expect(apiSource).toContain('listTicketAllocations')
    expect(apiSource).toContain('/api/order-delivery/tickets/${ticketId}/allocations')
    expect(apiSource).toContain('getDeliveredItem')
    expect(apiSource).toContain('/api/order-delivery/tickets/${ticketId}')
    expect(pageSource).toContain('listLockedItems')
    expect(pageSource).toContain('listTicketAllocations')
  })

  test('can trace a locked allocation back to original delivered ticket detail', () => {
    expect(pageSource).toContain('getDeliveredItem')
    expect(pageSource).toContain('openTicketDetail')
    expect(pageSource).toContain('查看提单行')
    expect(pageSource).toContain('ticketDetailVisible')
    expect(pageSource).toContain('原始提单行详情')
    expect(pageSource).toContain('selectedTicketDetail')
    expect(pageSource).toContain('selectedTicketDetail.mesSyncStatus')
    expect(pageSource).toContain('selectedTicketDetail.wmsAllocateStatus')
    expect(pageSource).toContain('selectedTicketDetail.materialNo')
    expect(pageSource).toContain('selectedTicketDetail.sourceType')
    expect(pageSource).toContain('selectedTicketDetail.sourceOrderId')
    expect(pageSource).toContain('selectedTicketDetail.processRoute')
    expect(pageSource).toContain('market:delivered-item:view')
  })

  test('renders locked item filters and table columns', () => {
    for (const token of [
      'customerName',
      'rfqNo',
      'materialNo',
      'internalProjectNo',
      'customerOrderNo',
      'customerProjectNo',
      'allocationType',
      'dateRange'
    ]) {
      expect(pageSource).toContain(token)
    }

    for (const label of ['客户', '来源订单号', '图号', '版本', '物料号', '零件类型', '内部项目号', '分配类型', '数量']) {
      expect(pageSource).toContain(label)
    }
    expect(pageSource).not.toContain('在产订单号')
    expect(pageSource).not.toContain('placeholder="RFQ号"')
    expect(pageSource).not.toContain('label="RFQ号"')
  })

  test('uses workbench filter layout with primary filters, stock picking batch actions, and collapsed advanced filters', () => {
    expect(pageSource).toContain('locked-toolbar-main')
    expect(pageSource).toContain('locked-primary-filters')
    expect(pageSource).toContain('locked-batch-actions')
    expect(pageSource).toContain('locked-advanced-filters')
    expect(pageSource).toContain('advancedFiltersVisible = ref(false)')
    expect(pageSource).toContain('advancedFiltersVisible ? \'收起筛选\' : \'高级筛选\'')
    expect(pageSource).toContain('handleResetFilters')
    expect(pageSource).not.toContain('下发MES生产')
    expect(pageSource).toContain('下发提库存工单')
    expect(pageSource).toContain('LOCKED_ITEM_STOCK_PICKING_SUBMIT_PERMISSION')
    expect(pageSource).toContain('v-permission="LOCKED_ITEM_STOCK_PICKING_SUBMIT_PERMISSION"')
  })

  test('allows selecting active locked allocation rows for stock picking order dispatch', () => {
    expect(pageSource).toContain('@selection-change="handleSelectionChange"')
    expect(pageSource).toContain('v-if="viewMode === \'ACTIVE\'" type="selection"')
    expect(pageSource).toContain('const selectedRows = ref([])')
    expect(pageSource).toContain('const selectedAllocationIds = computed(() => selectedRows.value.map(row => row.id).filter(Boolean))')
    expect(pageSource).toContain('const handleSelectionChange = (rows) =>')
  })


  test('keeps MES production dispatch out while wiring stock picking order dispatch', () => {
    expect(apiSource).toContain('submitMesProductionDispatchBatch')
    expect(apiSource).toContain('/api/order-delivery/production-items/mes-production')
    expect(apiSource).toContain('submitLockedStockPickingOrderBatch')
    expect(apiSource).toContain('/api/order-delivery/locked-items/stock-picking-orders')
    expect(pageSource).not.toContain('submitMesProductionDispatchBatch')
    expect(pageSource).toContain('ElMessageBox.confirm')
    expect(pageSource).toContain('确认提交提库存工单')
    expect(pageSource).toContain('非锁库行会自动跳过')
    expect(pageSource).toContain(':disabled="selectedAllocationIds.length === 0 || isStockPickingDispatchPending"')
    expect(pageSource).toContain(':loading="isStockPickingDispatchPending"')
    expect(pageSource).toContain('@click="handleStockPickingDispatch"')
    expect(pageSource).toContain('return runAction(lockedActionKeys.stockPickingDispatch(), async () =>')
    expect(pageSource).toContain('await submitLockedStockPickingOrderBatch(selectedAllocationIds.value)')
  })

  test('shows stock picking order batch result summary on the locked result page', () => {
    expect(pageSource).toContain('stockPickingResult')
    expect(pageSource).toContain('stock-picking-result-surface')
    expect(pageSource).toContain('提库存工单下发结果')
    expect(pageSource).toContain('successCount')
    expect(pageSource).toContain('failedCount')
    expect(pageSource).toContain('skippedCount')
    expect(pageSource).toContain('waitStockPickingTaskResult(taskId)')
    expect(pageSource).toContain('selectedRows.value = []')
  })

  test('uses module customer picker and freezes primary versus advanced locked-item filters', () => {
    const primaryStart = pageSource.indexOf('locked-primary-filters')
    const primarySource = pageSource.slice(primaryStart, pageSource.indexOf('</div>', primaryStart))
    const advancedStart = pageSource.indexOf('locked-advanced-filters')
    const advancedSource = pageSource.slice(advancedStart, pageSource.indexOf('</div>', advancedStart))

    expect(pageSource).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(pageSource).toContain('<CustomerPickerDialog')
    expect(pageSource).toContain('customer-scope="locked-item"')
    expect(pageSource).toContain('v-permission="\'market:locked-item:customer-select\'"')
    expect(pageSource).toContain('@select="handleCustomerSelected"')
    expect(primarySource).not.toContain('v-model="filters.customerName"')

    for (const token of [
      'filters.partNo',
      'filters.version',
      'filters.allocationType'
    ]) {
      expect(primarySource).toContain(token)
    }

    for (const token of [
      'filters.rfqNo',
      'filters.internalProjectNo',
      'filters.customerOrderNo',
      'filters.customerProjectNo',
      'filters.materialNo',
      'filters.dateRange'
    ]) {
      expect(advancedSource).toContain(token)
      expect(primarySource).not.toContain(token)
    }

    for (const removedCommonFilter of [
      'filters.' + 'wms' + 'ProductionOrderNo'
    ]) {
      expect(primarySource).not.toContain(removedCommonFilter)
      expect(advancedSource).not.toContain(removedCommonFilter)
    }
  })

  test('freezes locked item table column order for WMS allocation review', () => {
    const expectedOrder = [
      '分配ID',
      '提单ID',
      '图号',
      '版本',
      '物料号',
      '零件类型',
      '分配类型',
      '提库存状态',
      '订单类型',
      '数量',
      '客户',
      '来源订单号',
      '内部项目号',
      '客户订单号',
      '客户项目号',
      '材料编码',
      '材料类型',
      '材料',
      '材料形态',
      '毛重',
      '净重',
      '工艺路线',
      '交货日期',
      '材料描述',
      '表面处理',
      '备注',
      '操作'
    ]

    const labels = Array.from(pageSource.matchAll(/label="([^"]+)"/g)).map(match => match[1])
    let cursor = -1
    for (const label of expectedOrder) {
      const nextIndex = labels.indexOf(label, cursor + 1)
      expect(nextIndex, `${label} should appear after ${expectedOrder[Math.max(0, expectedOrder.indexOf(label) - 1)]}`).toBeGreaterThan(cursor)
      cursor = nextIndex
    }

    for (const helper of ['formatMaterialCode', 'formatMaterialType', 'formatMaterialName', 'formatMaterialShape', 'formatWeight', 'formatProcessRoute']) {
      expect(pageSource).toContain(helper)
    }
  })

  test('does not expose removed customer demand snapshot beside locked item remarks', () => {
    const removedField = 'customer' + 'Requirement'
    expect(pageSource).not.toContain(`prop="${removedField}"`)
    expect(pageSource).not.toContain(`selectedTicketDetail.${removedField}`)
  })

  test('keeps allocation and delivered ticket id tooltips enabled for long ids', () => {
    expect(pageSource).toContain('prop="id" label="分配ID" width="96" fixed align="center" show-overflow-tooltip')
    expect(pageSource).toContain('prop="ticketId" label="提单ID" width="96" fixed align="center" show-overflow-tooltip')
  })

  test('localizes allocation types and uses stable tag types', () => {
    const allocationTypeStart = pageSource.indexOf('const allocationTypeOptions')
    const allocationTypeSource = pageSource.slice(
      allocationTypeStart,
      pageSource.indexOf('const stockPickingStatusOptions', allocationTypeStart)
    )

    expect(pageSource).toContain('IN_STOCK_LOCK')
    expect(pageSource).toContain('IN_PRODUCTION_LOCK')
    expect(pageSource).not.toContain('PRODUCTION_REQUIRED')
    expect(pageSource).toContain('已入库库存锁定')
    expect(pageSource).toContain('在产覆盖')
    expect(pageSource).not.toContain('在产库存锁定')
    expect(pageSource).not.toContain('待生产数量')
    expect(allocationTypeSource).toContain("type: 'success'")
    expect(allocationTypeSource).toContain("type: 'warning'")
    expect(allocationTypeSource).not.toContain("type: 'danger'")
  })

  test('supports module-local discarded view and locked stock picking status filters', () => {
    expect(pageSource).toContain('viewMode')
    expect(pageSource).toContain("{ label: '待处理', value: 'ACTIVE' }")
    expect(pageSource).toContain("{ label: '已废弃', value: 'DISCARDED' }")
    expect(pageSource).toContain("params.discarded = viewMode.value === 'DISCARDED'")
    expect(pageSource).toContain('stockPickingStatus')
    expect(pageSource).toContain("{ value: 'PENDING', label: '待下发', type: 'info' }")
    expect(pageSource).toContain("{ value: 'PROCESSING', label: '下发中', type: 'warning' }")
    expect(pageSource).toContain("{ value: 'RESULT_UNKNOWN', label: '结果待核对', type: 'warning' }")
    expect(pageSource).toContain("{ value: 'FAILED', label: '下发失败', type: 'danger' }")
    expect(pageSource).toContain('LOCKED_ITEM_DISCARD_PERMISSION')
    expect(pageSource).toContain('LOCKED_ITEM_RESTORE_PERMISSION')
    expect(pageSource).toContain('market:locked-item:discard')
    expect(pageSource).toContain('market:locked-item:restore')
    expect(pageSource).toContain('discardLockedItem(row.id')
    expect(pageSource).toContain('restoreLockedItem(row.id')
  })

  test('supports ticket-specific result entry and Element Plus error feedback', () => {
    expect(pageSource).toContain('route.query.ticketId')
    expect(pageSource).toContain('锁库零件列表加载失败。')
    expect(pageSource).toContain('ElMessage.error')
  })
})
