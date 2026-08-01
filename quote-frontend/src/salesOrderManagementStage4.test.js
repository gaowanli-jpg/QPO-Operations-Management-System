import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readSource = (...segments) => readFileSync(resolve(currentDir, ...segments), 'utf8')

describe('sales order management stage 4 governance and drawings', () => {
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
      'supplementSalesOrderItemDrawings'
    ]) {
      expect(orderApi).toContain(`export const ${apiName}`)
      expect(salesOrderManage).toContain(apiName)
    }
    expect(orderApi).toContain('/api/market/sales-order-management/orders')
    expect(orderApi).toContain('/api/market/sales-order-management/items/${itemId}/drawings')
  })

  test('shows only sales order governance buttons and never exposes delete behavior', () => {
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS')
    expect(salesOrderManage).toContain("permission: 'market:sales-order:discard'")
    expect(salesOrderManage).toContain("permission: 'market:sales-order:restore'")
    expect(salesOrderManage).toContain('销售订单已生成提单，不能废弃')
    expect(salesOrderManage).toContain('SALES_ORDER_STATUS_ACTIONS.filter(action => action.status === row.status)')
    expect(salesOrderManage).toContain('v-permission="action.permission"')
    expect(salesOrderManage).not.toContain('deleteOrder')
    expect(salesOrderManage).not.toContain('market:order:delete')
    expect(salesOrderManage).not.toContain('删除')
  })

  test('supports row-level sales order drawing supplement bound to sales order item', () => {
    expect(salesOrderManage).toContain("v-permission=\"'market:sales-order:supplement-drawing'\"")
    expect(salesOrderManage).toContain('openSupplementDialog(scope.row)')
    expect(salesOrderManage).toContain('supplementTargetItem')
    expect(salesOrderManage).toContain('supplementSalesOrderItemDrawings(supplementTargetItem.value.id, formData)')
    expect(salesOrderManage).toContain('collectPdfFilesFromDataTransfer')
    expect(salesOrderManage).toContain('mergePdfFiles')
    expect(salesOrderManage).toContain('await fetchDetailItems()')
    expect(salesOrderManage).toContain('await loadOrders()')
  })

  test('detail view uses backend pagination for independent sales order tabs', () => {
    expect(salesOrderManage).toContain('detailItemPagination')
    expect(salesOrderManage).toContain('detailTicketPagination')
    expect(salesOrderManage).toContain('detailProcessSheetPagination')
    expect(salesOrderManage).toContain('listSalesOrderItems(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderTickets(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderProcessSheets(salesOrderId')
  })
})
