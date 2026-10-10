import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const salesOrderPageSource = readFileSync(resolve(currentDir, 'components', 'SalesOrderManage.vue'), 'utf8')
const deliveredPageSource = readFileSync(resolve(currentDir, 'components', 'DeliveredItemManage.vue'), 'utf8')
const orderApiSource = readFileSync(resolve(currentDir, 'services', 'orderApi.js'), 'utf8')

describe('Sales order optional WMS allocation entry', () => {
  test('exposes an order-level WMS allocation action in the allocation split tab', () => {
    expect(salesOrderPageSource).toContain("const SALES_ORDER_WMS_ALLOCATE_PERMISSION = 'market:sales-order:wms-allocate'")
    expect(salesOrderPageSource).toContain('v-permission="SALES_ORDER_WMS_ALLOCATE_PERMISSION"')
    expect(salesOrderPageSource).toContain('获取库存信息')
    expect(salesOrderPageSource).toContain('@click="handleWmsAllocation"')
    expect(salesOrderPageSource).toContain(':loading="wmsAllocationPending"')
    expect(salesOrderPageSource).toContain('label="锁库拆行" name="allocationSplits"')
    expect(salesOrderPageSource).toContain('listSalesOrderAllocationSplits')
  })

  test('submits the whole sales order instead of the current ticket page', () => {
    expect(orderApiSource).toContain('submitSalesOrderWmsAllocationBatch')
    expect(orderApiSource).toContain('/wms-allocation/batch')
    expect(salesOrderPageSource).toContain('submitSalesOrderWmsAllocationBatch(salesOrderId)')
    expect(salesOrderPageSource).toContain('按本订单数量大于 0 的待产 Allocation 获取库存')
    expect(salesOrderPageSource).not.toContain('submitSalesOrderWmsAllocationBatch(detailTicketList')
  })

  test('confirms, reports task submission, and refreshes allocation rows', () => {
    expect(salesOrderPageSource).toContain('ElMessageBox.confirm')
    expect(salesOrderPageSource).toContain('库存信息获取任务已提交')
    expect(salesOrderPageSource).toContain('数量大于 0 的待产 Allocation 获取库存信息')
    expect(salesOrderPageSource).toContain('runAction(salesOrderActionKeys.wmsAllocation(salesOrderId)')
    expect(salesOrderPageSource).toContain('await fetchAllocationSplits()')
  })

  test('keeps the ticket tab focused on the original ticket rows', () => {
    const ticketTabStart = salesOrderPageSource.indexOf('label="提单行" name="tickets"')
    const ticketTabEnd = salesOrderPageSource.indexOf('</el-tab-pane>', ticketTabStart)
    const ticketTabSource = salesOrderPageSource.slice(ticketTabStart, ticketTabEnd)
    expect(ticketTabSource).toContain(':data="detailTicketList"')
    expect(ticketTabSource).not.toContain('wmsAllocationTask')
    expect(ticketTabSource).not.toContain('handleWmsAllocation')
  })

  test('removes the WMS operation entry from the delivered item page', () => {
    expect(deliveredPageSource).not.toContain('market:delivered-item:wms-allocate')
    expect(deliveredPageSource).not.toContain('批量获取库存数据')
    expect(deliveredPageSource).not.toContain('handleBatchWmsAllocation')
    expect(deliveredPageSource).not.toContain('submitWmsAllocationBatch')
    expect(deliveredPageSource).toContain('库存信息改为在销售订单详情按订单获取')
  })

  test('keeps the delivered item pool scoped to unfinished material numbers', () => {
    expect(deliveredPageSource).toContain('仅展示尚未完成物料号获取的提单零件')
    expect(deliveredPageSource).not.toContain("label: '待获取库存'")
    expect(deliveredPageSource).not.toContain("label: '库存处理中'")
    expect(deliveredPageSource).not.toContain("label: '库存失败'")
  })
})
