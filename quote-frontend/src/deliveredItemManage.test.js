import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const source = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')

describe('DeliveredItemManage foundation page', () => {
  test('freezes delivered item page title, filters, and core columns', () => {
    expect(source).toContain('已提零件')
    for (const label of ['客户', '来源订单号', '图号', '版本', '物料号', '零件类型', '内部项目号', 'MES状态', 'WMS状态', '交货日期']) {
      expect(source).toContain(label)
    }
    for (const label of ['提单ID', '图号', '物料号', '零件类型', '提单数量', 'MES状态', 'WMS状态', '客户订单号', '客户项目号', '材料类型', '毛重', '净重', '工艺路线', '材料描述', '表面处理']) {
      expect(source).toContain(label)
    }
  })

  test('explains delivered items as the unfinished MES WMS work pool without local history moves', () => {
    expect(source).toContain('仅展示尚未完成物料号获取和锁库流程的提单零件')
    expect(source).toContain('完成锁库后的结果请在锁库零件模块查看')
    expect(source).toContain('暂无未完成流程的已提零件')
    expect(source).toContain('empty-text="暂无未完成流程的已提零件')
    expect(source).not.toContain('delivered-active-pool-tip')
    expect(source).not.toContain("flowScope: 'ALL'")
    expect(source).not.toContain('flowScope: "ALL"')
    expect(source).not.toContain('items.value = (response.records || []).filter')

    for (const forbiddenAction of ['删除已提零件', '转移到历史', '搬到历史表']) {
      expect(source).not.toContain(forbiddenAction)
    }
  })

  test('keeps delivered item table columns in the MES WMS demand-pool order', () => {
    const tableSource = source.slice(source.indexOf('<el-table'))
    const labels = [
      'label="提单ID"',
      'label="图号"',
      'label="版本"',
      'label="物料号"',
      'label="零件类型"',
      'label="MES状态"',
      'label="WMS状态"',
      'label="订单类型"',
      'label="提单数量"',
      'label="客户"',
      'label="来源订单号"',
      'label="内部项目号"',
      'label="客户订单号"',
      'label="客户项目号"',
      'label="材料编码"',
      'label="材料类型"',
      'label="材料"',
      'label="材料形态"',
      'label="毛重"',
      'label="净重"',
      'label="工艺路线"',
      'label="交货日期"',
      'label="材料描述"',
      'label="表面处理"',
      'label="备注"',
      'label="操作"'
    ]
    const positions = labels.map(label => tableSource.indexOf(label))
    expect(positions.every(position => position >= 0)).toBe(true)
    expect(positions).toEqual([...positions].sort((a, b) => a - b))
  })

  test('does not expose removed customer demand snapshot beside delivered item remarks', () => {
    const removedField = 'customer' + 'Requirement'
    expect(source).not.toContain(`prop="${removedField}"`)
    expect(source).not.toContain(`selectedItem.${removedField}`)
  })

  test('keeps delivered ticket id tooltip enabled for long ids', () => {
    expect(source).toContain('prop="id" label="提单ID" width="96" fixed align="center" show-overflow-tooltip')
  })

  test('supports selecting delivered items for batch MES and WMS tasks', () => {
    expect(source).toContain('@selection-change="handleSelectionChange"')
    expect(source).toContain('type="selection"')
    expect(source).toContain('type="selection" width="46" fixed align="center"')
    expect(source).toContain('selectedRows')
    expect(source).toContain('selectedTicketIds')
    expect(source).toContain('batchMesSync')
    expect(source).toContain('batchWmsAllocation')
    expect(source).toContain('handleBatchMesSync')
    expect(source).toContain('handleBatchWmsAllocation')
    expect(source).toContain('syncMesMaterialBatch')
    expect(source).toContain('submitWmsAllocationBatch')
  })

  test('supports module-local discarded view and reasoned restore workflow', () => {
    expect(source).toContain('viewMode')
    expect(source).toContain("{ label: '待处理', value: 'ACTIVE' }")
    expect(source).toContain("{ label: '已废弃', value: 'DISCARDED' }")
    expect(source).toContain('params.discarded = viewMode.value === \'DISCARDED\'')
    expect(source).toContain('DELIVERED_ITEM_DISCARD_PERMISSION')
    expect(source).toContain('DELIVERED_ITEM_RESTORE_PERMISSION')
    expect(source).toContain('market:delivered-item:discard')
    expect(source).toContain('market:delivered-item:restore')
    expect(source).toContain('handleDiscard(row)')
    expect(source).toContain('handleRestore(row)')
    expect(source).toContain('废弃原因')
    expect(source).toContain('废弃时间')
    expect(source).toContain('废弃人')
    expect(source).toContain('discardDeliveredItem(row.id')
    expect(source).toContain('restoreDeliveredItem(row.id')
    expect(source).toContain('viewMode.value === \'ACTIVE\'')
    expect(source).toContain('viewMode.value === \'DISCARDED\'')
  })

  test('supports batch discard and batch restore with one reason per selected batch', () => {
    expect(source).toContain('batchDiscardDeliveredItems')
    expect(source).toContain('batchRestoreDeliveredItems')
    expect(source).toContain('batchDiscard: () => \'delivery:discard:batch\'')
    expect(source).toContain('batchRestore: () => \'delivery:restore:batch\'')
    expect(source).toContain('DELIVERED_ITEM_DISCARD_PERMISSION')
    expect(source).toContain('DELIVERED_ITEM_RESTORE_PERMISSION')
    expect(source).toContain('批量废弃')
    expect(source).toContain('批量恢复')
    expect(source).toContain('@click="handleBatchDiscard"')
    expect(source).toContain('@click="handleBatchRestore"')
    expect(source).toContain('请输入批量废弃原因')
    expect(source).toContain('await batchDiscardDeliveredItems(selectedTicketIds.value')
    expect(source).toContain('await batchRestoreDeliveredItems(selectedTicketIds.value')
    expect(source).toContain('selectedRows.value = []')
  })

  test('keeps WMS allocation as a batch toolbar action and locked-result entry as row navigation', () => {
    expect(source).toContain('DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION')
    expect(source).toContain('market:delivered-item:wms-allocate')
    expect(source).toContain('submitWmsAllocationBatch')
    expect(source).toContain('handleBatchWmsAllocation')
    expect(source).not.toContain('wmsAllocationAction(row)')
    expect(source).not.toContain('@click="handleWmsAllocation(row)"')
    expect(source).toContain('查看锁库')
  })

  test('removes row-level MES and WMS fetch actions from delivered item operation column', () => {
    const operationColumnSource = source.slice(
      source.indexOf('label="操作"'),
      source.indexOf('</el-table>', source.indexOf('label="操作"'))
    )

    expect(operationColumnSource).toContain('@click="openDetail(row)"')
    expect(operationColumnSource).toContain('@click="openLockedItems(row)"')
    expect(operationColumnSource).toContain('@click="openDrawingPreview(row)"')
    expect(operationColumnSource).not.toContain('mesSyncAction(row)')
    expect(operationColumnSource).not.toContain('wmsAllocationAction(row)')
    expect(operationColumnSource).not.toContain('@click="handleMesSync(row)"')
    expect(operationColumnSource).not.toContain('@click="handleWmsAllocation(row)"')
  })

  test('adds missing-drawing supplement shortcut between detail and discard actions', () => {
    const operationColumnSource = source.slice(
      source.indexOf('label="操作"'),
      source.indexOf('</el-table>', source.indexOf('label="操作"'))
    )

    const detailPosition = operationColumnSource.indexOf('@click="openDetail(row)"')
    const supplementPosition = operationColumnSource.indexOf('@click="openSalesOrderSupplement(row)"')
    const discardPosition = operationColumnSource.indexOf('@click="handleDiscard(row)"')

    expect(detailPosition).toBeGreaterThanOrEqual(0)
    expect(supplementPosition).toBeGreaterThan(detailPosition)
    expect(discardPosition).toBeGreaterThan(supplementPosition)
    expect(operationColumnSource).toContain('v-if="viewMode === \'ACTIVE\' && !row.drawingUrl"')
    expect(operationColumnSource).toContain('v-permission="SALES_ORDER_VIEW_PERMISSION"')
    expect(operationColumnSource).toContain(':disabled="!row.salesOrderId"')
    expect(operationColumnSource).toContain('补图')
    expect(source).toContain("const SALES_ORDER_VIEW_PERMISSION = 'market:sales-order:view'")
    expect(source).toContain('const openSalesOrderSupplement = (row) =>')
    expect(source).toContain("path: '/sales-orders'")
    expect(source).toContain("salesOrderId: String(row.salesOrderId)")
    expect(source).toContain("from: 'delivered-item'")
    expect(source).toContain('ticketId: String(row.id)')
    expect(source).toContain('未关联销售订单，无法跳转补图')
  })

  test('renders missing material numbers and malformed process routes safely', () => {
    expect(source).toContain('未获取')
    expect(source).toContain('formatMaterialNo')
    expect(source).toContain('formatMaterialName')
    expect(source).toContain('formatMaterialShape')
    expect(source).toContain('formatWeight')
    expect(source).toContain('formatProcessRoute')
    expect(source).toContain('JSON.parse')
    expect(source).not.toContain('工艺解析失败')
  })

  test('maps delivery date range filters to backend query params', () => {
    expect(source).toContain('dateRange')
    expect(source).toContain('dateFrom')
    expect(source).toContain('dateTo')
    expect(source).toContain('value-format="YYYY-MM-DD"')
  })

  test('uses module customer picker and freezes common delivered-item filters', () => {
    const toolbarStart = source.indexOf('delivered-filter-toolbar')
    const toolbarSource = source.slice(toolbarStart, source.indexOf('</section>', toolbarStart))
    const primaryStart = source.indexOf('delivered-primary-filters')
    const primarySource = source.slice(primaryStart, source.indexOf('</div>', primaryStart))
    const advancedStart = source.indexOf('delivered-advanced-filters')
    const advancedSource = source.slice(advancedStart, source.indexOf('</div>', advancedStart))

    expect(source).toContain("import CustomerPickerDialog from './CustomerPickerDialog.vue'")
    expect(source).toContain('<CustomerPickerDialog')
    expect(source).toContain('customer-scope="delivered-item"')
    expect(source).toContain('v-permission="\'market:delivered-item:customer-select\'"')
    expect(source).toContain('@select="handleCustomerSelected"')
    expect(toolbarSource).toContain('v-model="filters.customerName"')

    expect(source).toContain('delivered-toolbar-main')
    expect(source).toContain('delivered-primary-filters')
    expect(source).toContain('delivered-batch-actions')
    expect(source).toContain('delivered-advanced-filters')
    expect(source).toContain('v-if="advancedFiltersVisible"')
    expect(source).toContain('高级筛选')
    expect(source).toContain('收起筛选')
    expect(source).toContain('流程状态')
    expect(source).toContain('来源订单号')
    expect(source).not.toContain('来源单号/RFQ号')
    expect(source).not.toContain('label="RFQ号"')

    for (const token of [
      'filters.partNo',
      'filters.version',
      'filters.flowStage'
    ]) {
      expect(primarySource).toContain(token)
    }

    for (const advancedToken of [
      'filters.rfqNo',
      'filters.internalProjectNo',
      'filters.customerOrderNo',
      'filters.customerProjectNo',
      'filters.mesSyncStatus',
      'filters.wmsAllocateStatus',
      'filters.dateRange'
    ]) {
      expect(advancedSource).toContain(advancedToken)
      expect(primarySource).not.toContain(advancedToken)
    }

    for (const removedCommonFilter of [
      'filters.materialNo'
    ]) {
      expect(toolbarSource).not.toContain(removedCommonFilter)
    }
  })

  test('keeps delivered item batch actions separated from query filters', () => {
    const primaryStart = source.indexOf('delivered-primary-filters')
    const primarySource = source.slice(primaryStart, source.indexOf('</div>', primaryStart))
    const actionsStart = source.indexOf('delivered-batch-actions')
    const actionSource = source.slice(actionsStart, source.indexOf('</div>', actionsStart))

    expect(primarySource).toContain('@click="handleSearch"')
    expect(primarySource).toContain('@click="handleResetFilters"')
    expect(primarySource).not.toContain('handleBatchMesSync')
    expect(primarySource).not.toContain('handleBatchWmsAllocation')

    expect(actionSource).toContain('DELIVERED_ITEM_MES_SYNC_PERMISSION')
    expect(actionSource).toContain('DELIVERED_ITEM_WMS_ALLOCATE_PERMISSION')
    expect(actionSource).toContain('handleBatchMesSync')
    expect(actionSource).toContain('handleBatchWmsAllocation')
    expect(actionSource).toContain('批量获取物料号')
    expect(actionSource).toContain('批量获取库存数据')
  })

  test('styles delivered item filters as a layered workbench toolbar', () => {
    const styleSource = source.slice(source.indexOf('<style scoped>'))
    const toolbarStyle = styleSource.slice(
      styleSource.indexOf('.delivered-filter-toolbar'),
      styleSource.indexOf('.filter-input')
    )
    const responsiveStyle = styleSource.slice(styleSource.indexOf('@media (max-width: 1200px)'))

    expect(toolbarStyle).toContain('padding: 12px')
    expect(toolbarStyle).toContain('box-sizing: border-box')
    expect(toolbarStyle).toContain('row-gap: 8px')
    expect(toolbarStyle).toContain('column-gap: 10px')
    expect(toolbarStyle).toContain('align-self: stretch')
    expect(toolbarStyle).toContain('padding-left: 12px')
    expect(toolbarStyle).toContain('border-left: 1px solid var(--erp-border-soft)')
    expect(toolbarStyle).toContain('background: var(--erp-surface-soft)')
    expect(toolbarStyle).not.toContain('background: #')

    expect(responsiveStyle).toContain('border-left: 0')
    expect(responsiveStyle).toContain('padding-left: 0')
    expect(responsiveStyle).toContain('width: 100%')
  })

  test('sends flow stage filters to backend for precise MES and WMS business stages', () => {
    for (const label of ['全部未完成', '待获取物料号', '物料号处理中', '待MES回调', '物料号失败', '待获取库存', '库存处理中', '库存失败']) {
      expect(source).toContain(`label: '${label}'`)
    }

    expect(source).toContain(".filter(([key, value]) => key !== 'dateRange'")
    expect(source).toContain('params.flowStage = filters.flowStage')
    expect(source).not.toContain('flowStageStatusMap')
    expect(source).not.toContain('Object.assign(params, flowStageParams)')
    expect(source).toContain('if (filters.mesSyncStatus || filters.wmsAllocateStatus)')
    expect(source).toContain("filters.flowStage = ''")
  })

  test('reset clears all delivered item filters and returns to first page', () => {
    const resetStart = source.indexOf('const handleResetFilters')
    const resetSource = source.slice(resetStart, source.indexOf('const handleFlowStageChange', resetStart))

    for (const resetToken of [
      "customerName: ''",
      "rfqNo: ''",
      "partNo: ''",
      "version: ''",
      "flowStage: ''",
      "internalProjectNo: ''",
      "customerOrderNo: ''",
      "customerProjectNo: ''",
      "mesSyncStatus: ''",
      "wmsAllocateStatus: ''",
      'dateRange: []'
    ]) {
      expect(resetSource).toContain(resetToken)
    }

    expect(resetSource).toContain('await handleSearch()')
    expect(source).toContain('page.value = 1')
    expect(source).not.toContain('items.value = (response.records || []).filter')
  })

  test('wires drawing preview to delivered item module permission', () => {
    expect(source).toContain('market:delivered-item:file-preview')
    expect(source).toContain('预览图纸')
    expect(source).toContain('openDrawingPreview')
    expect(source).toContain('当前零件没有绑定图纸。')
    expect(source).toContain('图纸打开失败。')
  })
})
