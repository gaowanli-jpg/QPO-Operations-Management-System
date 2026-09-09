import { describe, expect, test } from 'vitest'
import { readFileSync } from 'node:fs'
import { dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const currentDir = dirname(fileURLToPath(import.meta.url))
const readComponent = (fileName) => readFileSync(resolve(currentDir, 'components', fileName), 'utf8').replace(/\r\n/g, '\n')

const functionBlock = (source, name) => {
  const start = source.indexOf(`const ${name} = `)
  const next = source.indexOf('\nconst ', start + 1)
  return start >= 0 ? source.slice(start, next >= 0 ? next : undefined) : ''
}

const watchBlock = (source) => {
  const start = source.indexOf('watch([page, pageSize]')
  const next = source.indexOf('\nwatch(', start + 1)
  return start >= 0 ? source.slice(start, next >= 0 ? next : undefined) : ''
}

const cases = [
  ['询价订单', 'OrderManage.vue', 'listOrders'],
  ['销售订单', 'SalesOrderManage.vue', 'listSalesOrders']
]

describe('order customer filters are optional', () => {
  test.each(cases)('%s defaults to all customers and keeps customer as a filter only', (_, fileName, apiName) => {
    const source = readComponent(fileName)
    const loadOrders = functionBlock(source, 'loadOrders')
    const clearCustomer = functionBlock(source, 'handleCustomerClear')
    const pageWatcher = watchBlock(source)

    expect(source).toContain('全部客户')
    expect(source).not.toContain('请先选择客户查看')
    expect(source).not.toContain(':disabled="!selectedCustomer"')
    expect(source).toContain('onMounted')
    expect(source).toContain(apiName)

    expect(pageWatcher).toContain('loadOrders()')
    expect(pageWatcher).not.toContain('if (selectedCustomer.value)')

    expect(loadOrders).not.toContain('if (!selectedCustomer.value)')
    expect(loadOrders).toContain('customerCode: selectedCustomerCode.value || undefined')
    expect(loadOrders).toContain('customerName: selectedCustomer.value || undefined')

    expect(clearCustomer).toContain('await loadOrders()')
    expect(clearCustomer).not.toContain('orders.value = []')
    expect(clearCustomer).not.toContain('total.value = 0')
  })
})
