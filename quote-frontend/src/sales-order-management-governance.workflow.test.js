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
      'supplementSalesOrderDrawings'
    ]) {
      expect(orderApi).toContain(`export const ${apiName}`)
      expect(salesOrderManage).toContain(apiName)
    }
    expect(orderApi).toContain('/api/market/sales-order-management/orders')
    expect(orderApi).toContain('/api/market/sales-order-management/orders/${salesOrderId}/supplement-drawings')
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

  test('detail view uses backend pagination for independent sales order tabs', () => {
    expect(salesOrderManage).toContain('detailItemPagination')
    expect(salesOrderManage).toContain('detailTicketPagination')
    expect(salesOrderManage).toContain('detailProcessSheetPagination')
    expect(salesOrderManage).toContain('listSalesOrderItems(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderTickets(salesOrderId')
    expect(salesOrderManage).toContain('listSalesOrderProcessSheets(salesOrderId')
  })
})
