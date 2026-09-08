import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order management governance and drawings', () => {
  const salesOrderManage = readSource('components', 'SalesOrderManage.vue')
  const orderApi = readSource('services', 'orderApi.js')

  test('uses sales order management APIs for list, detail, paged tabs, status actions, and drawings', () => {
    for (const apiName of [
      'listSalesOrders',
      'getSalesOrder',
      'listSalesOrderItems',
      'listSalesOrderTickets',
      'listSalesOrderProcessSheets',
      'getSalesOrderDrawingSummary',
      'discardSalesOrder',
      'restoreSalesOrder',
      'discardSalesOrderItemForU9Change',
      'restoreSalesOrderItemForU9Change',
      'supplementSalesOrderDrawings',
      'replaceSalesOrderDrawings',
      'downloadSalesOrderProductionTaskNotice'
    ]) {
      expect(orderApi).toContain(`export const ${apiName}`)
      expect(salesOrderManage).toContain(apiName)
    }
    expect(orderApi).toContain('/api/market/sales-order-management/orders')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/supplement-drawings')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/drawings/replace')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/production-task-notice/export')
    expect(orderApi).not.toContain('/api/market/sales-order-management/items/${itemId}/drawings')
  })

  test('shows only sales order governance buttons and never exposes delete behavior', () => {
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS')
    expect(salesOrderManage).toContain("permission: 'market:sales-order:discard'")
    expect(salesOrderManage).toContain("permission: 'market:sales-order:restore'")
    expect(salesOrderManage).toContain('销售订单已同步 U9，不能废弃')
    expect(salesOrderManage).toContain("status: '已生成提单'")
    expect(salesOrderManage).toContain('恢复为已生成提单')
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)')
    expect(salesOrderManage).toContain('v-permission="action.permission"')
    expect(salesOrderManage).not.toContain('deleteOrder')
    expect(salesOrderManage).not.toContain('market:order:delete')
    expect(salesOrderManage).not.toContain('删除')
  })

  test('supports order-level sales order drawing supplement with shared dialog', () => {
    expect(salesOrderManage).toContain("v-permission=\"'market:sales-order:supplement-drawing'\"")
    expect(salesOrderManage).toContain('<SupplementDrawingDialog')
    expect(salesOrderManage).toContain('v-model:files="supplementFiles"')
    expect(salesOrderManage).toContain('v-model:version-files="supplementVersionFiles"')
    expect(salesOrderManage).toContain(':missing-items="missingDrawingItems"')
    expect(salesOrderManage).toContain(':permission="\'market:sales-order:supplement-drawing\'"')
    expect(salesOrderManage).toContain('@click="openSupplementDialog"')
    expect(salesOrderManage).toContain('supplementSalesOrderDrawings(salesOrderId, formData)')
    expect(salesOrderManage).toContain("formData.append('drawings', file)")
    expect(salesOrderManage).toContain("formData.append('versionDrawings', file)")
    expect(salesOrderManage).toContain('drawingSummary.value?.missingItems')
    expect(salesOrderManage).not.toContain('openSupplementDialog(scope.row)')
    expect(salesOrderManage).not.toContain('supplementTargetItem')
    expect(salesOrderManage).not.toContain('supplementSalesOrderItemDrawings')
    expect(salesOrderManage).toContain('await fetchDetailItems()')
    expect(salesOrderManage).toContain('await loadOrders()')
  })

  test('detail view exposes secured production task notice export action', () => {
    expect(salesOrderManage).toContain('导出生产任务通知单')
    expect(salesOrderManage).toContain("v-permission=\"'market:sales-order:production-task-notice-export'\"")
    expect(salesOrderManage).toContain('productionTaskNoticeExport: (orderId) => `sales-order:production-task-notice-export:${orderId || \'none\'}`')
    expect(salesOrderManage).toContain('productionTaskNoticeExportPending')
    expect(salesOrderManage).toContain('downloadSalesOrderProductionTaskNotice')
    expect(salesOrderManage).toContain('handleProductionTaskNoticeExport')
    expect(salesOrderManage).toContain('runAction(salesOrderActionKeys.productionTaskNoticeExport(salesOrderId)')
  })

  test('detail view exposes sales order drawing replacement between supplement and production export', () => {
    expect(salesOrderManage).toContain('导入替换图纸')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawings')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingVisible')
    expect(salesOrderManage).toContain('submitReplaceSalesOrderDrawings')
    expect(salesOrderManage).toContain('salesOrderDrawingReplace: (orderId) => `sales-order:drawing-replace:${orderId || \'none\'}`')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingUploadPending')
    expect(salesOrderManage).toContain(':permission="\'market:sales-order:supplement-drawing\'"')
    expect(salesOrderManage).toContain('submit-text="上传并替换"')
    expect(salesOrderManage).toContain('bound-result-label="已替换"')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingFiles.value.forEach(file => formData.append(\'drawings\', file))')
    expect(salesOrderManage).toContain('replaceSalesOrderVersionDrawingFiles.value.forEach(file => formData.append(\'versionDrawings\', file))')
    expect(salesOrderManage).toContain('replaceSalesOrderDrawingFolderFiles.value.forEach(file => formData.append(\'drawingFolderFiles\', file))')
    expect(salesOrderManage.indexOf('补传图纸')).toBeLessThan(salesOrderManage.indexOf('导入替换图纸'))
    expect(salesOrderManage.indexOf('导入替换图纸')).toBeLessThan(salesOrderManage.indexOf('导出生产任务通知单'))
  })

  test('detail view uses backend pagination for independent sales order tabs', () => {
    expect(salesOrderManage).toContain('detailItemPagination')
    expect(salesOrderManage).toContain('detailTicketPagination')
    expect(salesOrderManage).toContain('detailProcessSheetPagination')
    expect(salesOrderManage).toContain('listSalesOrderItems(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderTickets(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderProcessSheets(salesOrderId')
  })

  test('sales order detail exposes item-level U9/change exclusion without hiding excluded rows', () => {
    expect(orderApi).toContain('discardSalesOrderItemForU9Change')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/items/${itemId}/discard')
    expect(orderApi).toContain('restoreSalesOrderItemForU9Change')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/items/${itemId}/restore')

    expect(salesOrderManage).toContain('u9ChangeExcluded')
    expect(salesOrderManage).toContain('仅从 U9 同步和销售变更排除')
    expect(salesOrderManage).toContain('不影响 MES 物料号、WMS 库存、已提/待产处理和 MES 生产下发')
    expect(salesOrderManage).toContain('handleSalesOrderItemU9ChangeExclusion')
    expect(salesOrderManage).toContain('discardSalesOrderItemForU9Change')
    expect(salesOrderManage).toContain('restoreSalesOrderItemForU9Change')
    expect(salesOrderManage).toContain("v-permission=\"SALES_ORDER_UPDATE_PERMISSION\"")
    expect(salesOrderManage).toContain('salesOrderItemU9ChangeExclusionLocked')
    expect(salesOrderManage).toContain("['同步中', '结果待确认'].includes")
    expect(salesOrderManage).toContain('请输入废弃原因')
    expect(salesOrderManage).toContain('请输入恢复原因')
    expect(salesOrderManage).toContain('detailItemList.value = response.records || []')
    expect(salesOrderManage).not.toContain('detailItemList.value = (response.records || []).filter')
  })
})
