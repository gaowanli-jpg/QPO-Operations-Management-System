import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const componentSource = () => readFileSync(resolve(currentDir, 'components', 'SalesOrderManage.vue'), 'utf8').replace(/\r\n/g, '\n')
const apiSource = () => readFileSync(resolve(currentDir, 'services', 'orderApi.js'), 'utf8').replace(/\r\n/g, '\n')

describe('sales order linked discard controls', () => {
  test('adds linked discard and restore beside the existing U9 change action', () => {
    const component = componentSource()
    const u9Column = component.slice(
      component.indexOf('<el-table-column label="U9/变更"'),
      component.indexOf('<el-table-column label="图纸"')
    )

    expect(u9Column).toContain('row.linkedDiscarded')
    expect(u9Column).toContain('关联废弃')
    expect(u9Column).toContain('关联恢复')
    expect(u9Column).toContain('handleSalesOrderItemLinkedDiscard(row)')
    expect(u9Column).toContain('SALES_ORDER_UPDATE_PERMISSION')
    expect(component).toContain('itemLinkedDiscard: (itemId)')
    expect(component).toContain('salesOrderItemLinkedDiscardLockedReason')
    expect(component).toContain('已提、待产和锁库工作池')
  })

  test('uses the sales order update permission and dedicated linked endpoints', () => {
    const component = componentSource()
    const api = apiSource()

    expect(component).toContain("const SALES_ORDER_UPDATE_PERMISSION = 'market:sales-order:update'")
    expect(component).toContain('discardSalesOrderItemLinked')
    expect(component).toContain('restoreSalesOrderItemLinked')
    expect(api).toContain('discardSalesOrderItemLinked')
    expect(api).toContain('/linked-discard')
    expect(api).toContain('restoreSalesOrderItemLinked')
    expect(api).toContain('/linked-restore')
  })
})
